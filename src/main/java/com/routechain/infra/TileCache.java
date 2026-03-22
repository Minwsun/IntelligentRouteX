package com.routechain.infra;

import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.effect.ColorAdjust;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Async tile downloader with LRU cache and dark filter.
 * Downloads OSM tiles via Java HttpClient (bypasses WebView limits).
 * Applies a dark grayscale filter once per tile, then caches the result.
 */
public class TileCache {

    private static final String TILE_URL_TEMPLATE =
            "https://basemaps.cartocdn.com/dark_all/%d/%d/%d@2x.png";

    private static final int MAX_CACHE_SIZE = 512;
    private static final int TILE_SIZE = 256;

    private final HttpClient httpClient;
    private final Map<String, Image> cache;
    private final Map<String, CompletableFuture<Image>> inFlight;
    private final ExecutorService downloadPool;

    // Dark filter effect applied once per tile
    private final ColorAdjust darkFilter;

    public interface TileLoadCallback {
        void onTileLoaded(int tileX, int tileY, int zoom, Image image);
    }

    private volatile TileLoadCallback loadCallback;

    public TileCache() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .executor(Executors.newFixedThreadPool(3))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        // LRU cache
        this.cache = new LinkedHashMap<>(MAX_CACHE_SIZE, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > MAX_CACHE_SIZE;
            }
        };

        this.inFlight = new ConcurrentHashMap<>();
        this.downloadPool = Executors.newFixedThreadPool(3, r -> {
            Thread t = new Thread(r, "tile-download");
            t.setDaemon(true);
            return t;
        });

        // Dark filter for tiles
        this.darkFilter = new ColorAdjust();
        this.darkFilter.setBrightness(-0.1);
        this.darkFilter.setContrast(0.1);
        this.darkFilter.setSaturation(-0.3);
    }

    public void setLoadCallback(TileLoadCallback callback) {
        this.loadCallback = callback;
    }

    /**
     * Get a tile from cache or trigger async download.
     * Returns cached image immediately if available, null if downloading.
     */
    public Image getTile(int tileX, int tileY, int zoom) {
        String key = zoom + "/" + tileX + "/" + tileY;
        synchronized (cache) {
            Image cached = cache.get(key);
            if (cached != null) return cached;
        }

        // Trigger async download if not already in flight
        if (!inFlight.containsKey(key)) {
            CompletableFuture<Image> future = CompletableFuture.supplyAsync(
                    () -> downloadTile(tileX, tileY, zoom), downloadPool
            );
            inFlight.put(key, future);
            future.thenAcceptAsync(img -> {
                if (img != null) {
                    synchronized (cache) {
                        cache.put(key, img);
                    }
                    inFlight.remove(key);
                    // Notify callback on JavaFX thread
                    TileLoadCallback cb = loadCallback;
                    if (cb != null) {
                        javafx.application.Platform.runLater(() ->
                                cb.onTileLoaded(tileX, tileY, zoom, img));
                    }
                } else {
                    inFlight.remove(key);
                }
            }, downloadPool);
        }
        return null;
    }

    /**
     * Check if a tile is in cache (for fast hit during pan/zoom).
     */
    public boolean hasTile(int tileX, int tileY, int zoom) {
        String key = zoom + "/" + tileX + "/" + tileY;
        synchronized (cache) {
            return cache.containsKey(key);
        }
    }

    /**
     * Download a single tile and return as Image.
     */
    private Image downloadTile(int tileX, int tileY, int zoom) {
        String url = String.format(TILE_URL_TEMPLATE, zoom, tileX, tileY);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "RouteChain-AI/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    // Creating Image from stream — CartoDB dark tiles are already dark
                    return new Image(is, TILE_SIZE, TILE_SIZE, true, true);
                }
            } else {
                // Fallback: try OSM tiles
                return downloadOsmFallback(tileX, tileY, zoom);
            }
        } catch (Exception e) {
            // Fallback to OSM
            return downloadOsmFallback(tileX, tileY, zoom);
        }
    }

    /**
     * Fallback: download from OSM and apply dark filter.
     */
    private Image downloadOsmFallback(int tileX, int tileY, int zoom) {
        String url = String.format("https://tile.openstreetmap.org/%d/%d/%d.png", zoom, tileX, tileY);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", "RouteChain-AI/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                try (InputStream is = response.body()) {
                    Image original = new Image(is, TILE_SIZE, TILE_SIZE, true, true);
                    // Apply dark filter by drawing to canvas with effect
                    return applyDarkFilter(original);
                }
            }
        } catch (Exception e) {
            System.err.println("[TileCache] Failed to download tile: " + url);
        }
        return null;
    }

    /**
     * Apply dark filter to a tile image using JavaFX canvas snapshot.
     * This is done ONCE per tile and cached — no per-frame overhead.
     */
    private Image applyDarkFilter(Image original) {
        try {
            // Use a canvas to apply the effect
            Canvas tempCanvas = new Canvas(TILE_SIZE, TILE_SIZE);
            GraphicsContext gc = tempCanvas.getGraphicsContext2D();
            gc.drawImage(original, 0, 0, TILE_SIZE, TILE_SIZE);

            // Apply color adjustment effect
            tempCanvas.setEffect(darkFilter);

            // Snapshot to get the filtered image
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage result = new WritableImage(TILE_SIZE, TILE_SIZE);
            // Note: snapshot must be called on FX thread; since we're in bg thread,
            // we return the original if snapshot fails
            CompletableFuture<WritableImage> fut = new CompletableFuture<>();
            javafx.application.Platform.runLater(() -> {
                try {
                    tempCanvas.snapshot(params, result);
                    fut.complete(result);
                } catch (Exception e) {
                    fut.complete(null);
                }
            });
            WritableImage filtered = fut.get(2, TimeUnit.SECONDS);
            return filtered != null ? filtered : original;
        } catch (Exception e) {
            return original; // Return unfiltered as fallback
        }
    }

    /**
     * Clear the cache.
     */
    public void clear() {
        synchronized (cache) {
            cache.clear();
        }
        inFlight.clear();
    }

    /**
     * Preload tiles for a given viewport.
     */
    public void preload(int minTileX, int minTileY, int maxTileX, int maxTileY, int zoom) {
        for (int x = minTileX; x <= maxTileX; x++) {
            for (int y = minTileY; y <= maxTileY; y++) {
                getTile(x, y, zoom); // triggers async download if not cached
            }
        }
    }
}
