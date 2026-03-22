package com.routechain.infra;

import com.google.gson.*;
import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.HcmcCityData;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridge between Java simulation and JavaScript map.
 * Pushes driver positions, traffic, weather, orders, and OSRM-routed paths to the WebView map.
 */
public class MapBridge {
    private final WebEngine engine;
    private final Gson gson = new GsonBuilder().create();
    private volatile boolean mapReady = false;
    private final List<Runnable> pendingCommands = new ArrayList<>();
    private final List<HcmcCityData.TrafficCorridor> cachedCorridors;

    // ── OSRM routing via Java HttpClient ────────────────────────────────
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    // Cache: driverId → CachedRoute (only stores SUCCESSFUL OSRM routes)
    private final Map<String, CachedRoute> routeCache = new ConcurrentHashMap<>();
    // Track which destinations we've already requested (prevent duplicate HTTP calls)
    private final Map<String, String> lastRequestedDest = new ConcurrentHashMap<>();
    // Global concurrency limiter: max 3 OSRM requests in flight
    private final Semaphore osrmSemaphore = new Semaphore(3);
    // Pending queue for throttled requests
    private final ConcurrentLinkedQueue<OsrmRequest> requestQueue = new ConcurrentLinkedQueue<>();
    // Track total OSRM stats
    private final AtomicInteger osrmSuccessCount = new AtomicInteger(0);
    private final AtomicInteger osrmFailCount = new AtomicInteger(0);

    private record CachedRoute(String destKey, List<List<Double>> coordinates, String phase) {}
    private record OsrmRequest(String driverId, String destKey, String phase,
                                double fromLat, double fromLng, double toLat, double toLng) {}

    // JS callback object exposed to the WebView
    public final JavaBridgeCallback callback = new JavaBridgeCallback();

    public MapBridge(WebEngine engine) {
        this.engine = engine;
        this.cachedCorridors = HcmcCityData.createCorridors();
        // Start the queue processor
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "osrm-queue-processor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::processRequestQueue, 500, 200, TimeUnit.MILLISECONDS);
    }

    public void attach() {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaBridge", callback);
                mapReady = true;
                for (Runnable cmd : pendingCommands) {
                    cmd.run();
                }
                pendingCommands.clear();
            }
        });
    }

    private void execJs(String script) {
        if (mapReady) {
            Platform.runLater(() -> {
                try {
                    engine.executeScript(script);
                } catch (Exception e) {
                    System.err.println("[MapBridge] JS error: " + e.getMessage());
                }
            });
        } else {
            pendingCommands.add(() -> Platform.runLater(() -> {
                try {
                    engine.executeScript(script);
                } catch (Exception e) {
                    System.err.println("[MapBridge] Deferred JS error: " + e.getMessage());
                }
            }));
        }
    }

    // ── Driver updates ──────────────────────────────────────────────────
    public void updateDriver(Driver driver) {
        String state = driver.getState().name();
        String name = driver.getName().replace("'", "\\'");
        execJs(String.format("window.appMap.updateDriver('%s', %f, %f, '%s', '%s')",
                driver.getId(), driver.getCurrentLocation().lat(),
                driver.getCurrentLocation().lng(), state, name));
    }

    public void removeDriver(String id) {
        execJs("window.appMap.removeDriver('" + id + "')");
    }

    // ── Order markers ───────────────────────────────────────────────────
    public void addOrder(Order order) {
        execJs(String.format("window.appMap.addOrder('%s', %f, %f)",
                order.getId(), order.getPickupPoint().lat(), order.getPickupPoint().lng()));
    }

    public void removeOrder(String id) {
        execJs("window.appMap.removeOrder('" + id + "')");
    }

    // ── Traffic segments ────────────────────────────────────────────────
    public void updateTraffic(Map<String, Double> corridorSeverity) {
        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        List<Map<String, Object>> features = new ArrayList<>();
        for (var corridor : cachedCorridors) {
            Double severity = corridorSeverity.getOrDefault(corridor.id(), 0.0);
            if (severity < 0.1) continue;
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", Map.of("severity", severity, "name", corridor.name()));
            feature.put("geometry", Map.of(
                    "type", "LineString",
                    "coordinates", List.of(
                            List.of(corridor.from().lng(), corridor.from().lat()),
                            List.of(corridor.to().lng(), corridor.to().lat())
                    )
            ));
            features.add(feature);
        }
        geojson.put("features", features);
        execJs("window.appMap.setTraffic('" + gson.toJson(geojson).replace("'", "\\'") + "')");
    }

    // ── Weather zones ───────────────────────────────────────────────────
    public void updateWeatherZones(List<Region> regions) {
        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        List<Map<String, Object>> features = new ArrayList<>();
        for (Region region : regions) {
            if (region.getRainIntensity() < 0.1) continue;
            List<List<Double>> ring = createCirclePolygon(
                    region.getCenter().lat(), region.getCenter().lng(),
                    region.getRadiusMeters(), 32);
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", Map.of("intensity", region.getRainIntensity(),
                    "name", region.getName()));
            feature.put("geometry", Map.of("type", "Polygon", "coordinates", List.of(ring)));
            features.add(feature);
        }
        geojson.put("features", features);
        execJs("window.appMap.setWeatherZones('" + gson.toJson(geojson).replace("'", "\\'") + "')");
    }

    // ── Order heatmap ───────────────────────────────────────────────────
    public void updateOrderHeat(List<Order> orders) {
        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        List<Map<String, Object>> features = new ArrayList<>();
        for (Order order : orders) {
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", Map.of());
            feature.put("geometry", Map.of("type", "Point",
                    "coordinates", List.of(order.getPickupPoint().lng(), order.getPickupPoint().lat())));
            features.add(feature);
        }
        geojson.put("features", features);
        execJs("window.appMap.setOrderHeat('" + gson.toJson(geojson).replace("'", "\\'") + "')");
    }

    // ── Layer visibility ────────────────────────────────────────────────
    public void toggleLayer(String layer, boolean visible) {
        execJs("window.appMap.toggleLayer('" + layer + "', " + visible + ")");
    }

    // ── Camera ──────────────────────────────────────────────────────────
    public void centerMap(double lat, double lng, int zoom) {
        execJs(String.format("window.appMap.centerMap(%f, %f, %d)", lat, lng, zoom));
    }

    public void focusDriver(String id) {
        execJs("window.appMap.focusDriver('" + id + "')");
    }

    // ── Road network overlay ────────────────────────────────────────────
    public void pushRoadNetwork() {
        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        List<Map<String, Object>> features = new ArrayList<>();
        for (var corridor : cachedCorridors) {
            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", Map.of("name", corridor.name()));
            feature.put("geometry", Map.of(
                    "type", "LineString",
                    "coordinates", List.of(
                            List.of(corridor.from().lng(), corridor.from().lat()),
                            List.of(corridor.to().lng(), corridor.to().lat())
                    )
            ));
            features.add(feature);
        }
        geojson.put("features", features);
        execJs("window.appMap.setRoadNetwork('" + gson.toJson(geojson).replace("'", "\\'") + "')");
    }

    // ═══════════════════════════════════════════════════════════════════
    // OSRM ROUTING — Java-side HTTP with throttling + retry
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Queue an OSRM route request for a driver.
     * If destination hasn't changed (and we have a valid cached route), skip.
     * Otherwise, enqueue for the throttled processor.
     */
    public void requestDriverRoute(String driverId, double fromLat, double fromLng,
                                    double toLat, double toLng, String phase) {
        String destKey = Math.round(toLat * 1000) + "_" + Math.round(toLng * 1000) + "_" + phase;

        // Skip if we already have a valid (non-fallback) OSRM route for this destination
        CachedRoute cached = routeCache.get(driverId);
        if (cached != null && cached.destKey().equals(destKey) && cached.coordinates().size() > 15) {
            return; // Good OSRM route already cached (>15 points = not a fallback)
        }

        // Skip if we already have a pending request for the same destination
        String lastReq = lastRequestedDest.get(driverId);
        if (destKey.equals(lastReq)) {
            return; // Already queued/in-flight for this destination
        }

        lastRequestedDest.put(driverId, destKey);
        requestQueue.offer(new OsrmRequest(driverId, destKey, phase, fromLat, fromLng, toLat, toLng));
    }

    /**
     * Process the request queue — called every 200ms by the scheduler.
     * Fires up to 3 concurrent OSRM requests (semaphore controlled).
     */
    private void processRequestQueue() {
        while (!requestQueue.isEmpty() && osrmSemaphore.tryAcquire()) {
            OsrmRequest req = requestQueue.poll();
            if (req == null) {
                osrmSemaphore.release();
                break;
            }
            fireOsrmRequest(req);
        }
    }

    private void fireOsrmRequest(OsrmRequest req) {
        String url = String.format(Locale.US,
                "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                req.fromLng(), req.fromLat(), req.toLng(), req.toLat());

        HttpRequest httpReq = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "RouteChainAI/1.0")
                .GET()
                .build();

        httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    osrmSemaphore.release();
                    if (response.statusCode() == 200) {
                        boolean success = parseAndCacheRoute(req.driverId(), req.destKey(), req.phase(), response.body());
                        if (success) {
                            int count = osrmSuccessCount.incrementAndGet();
                            if (count <= 5 || count % 10 == 0) {
                                System.out.println("[OSRM] ✓ Route OK for " + req.driverId()
                                        + " (" + count + " total, " + osrmFailCount.get() + " failed)");
                            }
                        } else {
                            osrmFailCount.incrementAndGet();
                            System.err.println("[OSRM] ✗ Parse failed for " + req.driverId());
                            lastRequestedDest.remove(req.driverId()); // Allow retry
                        }
                    } else {
                        osrmFailCount.incrementAndGet();
                        System.err.println("[OSRM] ✗ HTTP " + response.statusCode() + " for " + req.driverId());
                        lastRequestedDest.remove(req.driverId()); // Allow retry
                    }
                })
                .exceptionally(ex -> {
                    osrmSemaphore.release();
                    osrmFailCount.incrementAndGet();
                    System.err.println("[OSRM] ✗ Network error for " + req.driverId() + ": " + ex.getMessage());
                    lastRequestedDest.remove(req.driverId()); // Allow retry
                    return null;
                });
    }

    private boolean parseAndCacheRoute(String driverId, String destKey, String phase, String responseBody) {
        try {
            JsonObject root = JsonParser.parseString(responseBody).getAsJsonObject();
            String code = root.has("code") ? root.get("code").getAsString() : "";
            if (!"Ok".equals(code)) {
                System.err.println("[OSRM] API returned code: " + code);
                return false;
            }
            JsonArray routes = root.getAsJsonArray("routes");
            if (routes != null && !routes.isEmpty()) {
                JsonObject route = routes.get(0).getAsJsonObject();
                JsonObject geometry = route.getAsJsonObject("geometry");
                JsonArray coordsArray = geometry.getAsJsonArray("coordinates");

                List<List<Double>> coordinates = new ArrayList<>();
                for (JsonElement coord : coordsArray) {
                    JsonArray c = coord.getAsJsonArray();
                    coordinates.add(List.of(c.get(0).getAsDouble(), c.get(1).getAsDouble()));
                }

                if (coordinates.size() >= 2) {
                    routeCache.put(driverId, new CachedRoute(destKey, coordinates, phase));
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[OSRM] Parse exception: " + e.getMessage());
        }
        return false;
    }

    /**
     * Clear the cached route for a driver (when they become idle).
     */
    public void clearDriverRoute(String driverId) {
        routeCache.remove(driverId);
        lastRequestedDest.remove(driverId);
    }

    /**
     * Push ALL cached routes to the map as a single GeoJSON batch.
     * Only pushes routes that have OSRM data (>2 coordinates).
     */
    public void pushCachedRoutes() {
        Map<String, Object> geojson = new HashMap<>();
        geojson.put("type", "FeatureCollection");
        List<Map<String, Object>> features = new ArrayList<>();

        for (var entry : routeCache.entrySet()) {
            CachedRoute route = entry.getValue();
            if (route.coordinates().size() < 2) continue;

            Map<String, Object> feature = new HashMap<>();
            feature.put("type", "Feature");
            feature.put("properties", Map.of(
                    "driverId", entry.getKey(),
                    "phase", route.phase()
            ));
            feature.put("geometry", Map.of(
                    "type", "LineString",
                    "coordinates", route.coordinates()
            ));
            features.add(feature);
        }

        geojson.put("features", features);
        execJs("window.appMap.setRoutes('" + gson.toJson(geojson).replace("'", "\\'") + "')");
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private List<List<Double>> createCirclePolygon(double lat, double lng, double radiusM, int points) {
        List<List<Double>> ring = new ArrayList<>();
        for (int i = 0; i <= points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double dLat = radiusM * Math.cos(angle) / 111320.0;
            double dLng = radiusM * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(lat)));
            ring.add(List.of(lng + dLng, lat + dLat));
        }
        return ring;
    }

    /**
     * Callback object exposed to JavaScript via window.javaBridge.
     */
    public static class JavaBridgeCallback {
        private volatile Runnable onMapReady;
        private volatile java.util.function.Consumer<String> onDriverSelected;
        private volatile java.util.function.Consumer<Double> onZoomChanged;

        public void onMapReady() {
            if (onMapReady != null) Platform.runLater(onMapReady);
        }

        public void onDriverSelected(String driverId) {
            if (onDriverSelected != null) Platform.runLater(() -> onDriverSelected.accept(driverId));
        }

        public void onMapZoomChanged(double zoom) {
            if (onZoomChanged != null) Platform.runLater(() -> onZoomChanged.accept(zoom));
        }

        public void log(String msg) {
            System.out.println("[MapJS] " + msg);
        }

        public void setOnMapReady(Runnable r) { this.onMapReady = r; }
        public void setOnDriverSelected(java.util.function.Consumer<String> c) { this.onDriverSelected = c; }
        public void setOnZoomChanged(java.util.function.Consumer<Double> c) { this.onZoomChanged = c; }
    }
}
