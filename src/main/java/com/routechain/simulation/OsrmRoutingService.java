package com.routechain.simulation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Handles asynchronous OSRM requests for drivers.
 * Keeps a queue and a semaphore so we don't spam public OSRM mapping servers.
 * Pushes waypoints directly into Driver.setRouteWaypoints() upon success.
 */
public class OsrmRoutingService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    private final Map<String, String> lastRequestedDest = new ConcurrentHashMap<>();
    private final Semaphore osrmSemaphore = new Semaphore(3); // Max 3 concurrent HTTP requests
    private final ConcurrentLinkedQueue<OsrmRequest> requestQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;

    private record OsrmRequest(
            Driver driver,
            String destKey,
            double fromLat, double fromLng,
            double toLat, double toLng
    ) {}

    public OsrmRoutingService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "osrm-engine-processor");
            t.setDaemon(true);
            return t;
        });
        // Process queue every 150ms to strictly comply with public demo servers limits.
        scheduler.scheduleAtFixedRate(this::processRequestQueue, 500, 150, TimeUnit.MILLISECONDS);
    }

    /**
     * Clear all pending routing requests to free memory/threads on shutdown/reset.
     */
    public void reset() {
        requestQueue.clear();
        lastRequestedDest.clear();
    }

    public void tearDown() {
        scheduler.shutdownNow();
    }

    /**
     * Request a route for a driver asynchronously.
     * The driver should not move while `driver.hasRouteWaypoints()` is false.
     * Once route returns, it'll populate `driver.setRouteWaypoints()`.
     */
    public void requestRouteAsync(Driver driver, GeoPoint from, GeoPoint to) {
        String destKey = Math.round(to.lat() * 1000) + "_" + Math.round(to.lng() * 1000);
        
        String lastReq = lastRequestedDest.get(driver.getId());
        if (destKey.equals(lastReq)) return; // Already requested or running
        
        lastRequestedDest.put(driver.getId(), destKey);
        
        requestQueue.offer(new OsrmRequest(
                driver, destKey,
                from.lat(), from.lng(),
                to.lat(), to.lng()
        ));
    }

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
                .timeout(Duration.ofSeconds(10))
                .header("User-Agent", "RouteChainAI-Engine/1.0")
                .GET()
                .build();

        httpClient.sendAsync(httpReq, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    osrmSemaphore.release();
                    boolean ok = false;
                    if (response.statusCode() == 200) {
                        ok = parseAndInjectRoute(req.driver(), response.body());
                    }
                    if (!ok) {
                        // Request failed or dropped, remove from tracking so it can be retried 
                        // by the Engine if needed.
                        lastRequestedDest.remove(req.driver().getId());
                    }
                })
                .exceptionally(ex -> {
                    osrmSemaphore.release();
                    lastRequestedDest.remove(req.driver().getId());
                    return null;
                });
    }

    private boolean parseAndInjectRoute(Driver driver, String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!"Ok".equals(root.has("code") ? root.get("code").getAsString() : "")) return false;
            JsonArray routes = root.getAsJsonArray("routes");
            
            if (routes != null && !routes.isEmpty()) {
                JsonArray coordsArray = routes.get(0).getAsJsonObject()
                        .getAsJsonObject("geometry").getAsJsonArray("coordinates");
                
                List<double[]> coordinates = new ArrayList<>();
                for (JsonElement coord : coordsArray) {
                    JsonArray c = coord.getAsJsonArray();
                    // OSRM returns [longitude, latitude]
                    coordinates.add(new double[]{c.get(0).getAsDouble(), c.get(1).getAsDouble()});
                }
                
                if (coordinates.size() >= 2) {
                    driver.setRouteWaypoints(coordinates);
                    return true;
                }
            }
        } catch (Exception ignored) {
            // Json syntax or network payload error
        }
        return false;
    }
}
