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
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Handles asynchronous route requests and protects drivers from stale responses.
 *
 * Headless mode uses simulated latency and injects a minimal fallback polyline
 * after a small number of sub-ticks instead of returning the route immediately.
 */
public class OsrmRoutingService {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    private final Semaphore osrmSemaphore = new Semaphore(3);
    private final ConcurrentLinkedQueue<OsrmRequest> requestQueue = new ConcurrentLinkedQueue<>();
    private final ScheduledExecutorService scheduler;
    private final Map<String, HeadlessPendingRoute> pendingHeadlessRoutes = new ConcurrentHashMap<>();
    private final Random rng = new Random(42);

    private record OsrmRequest(
            Driver driver,
            String requestId,
            double fromLat,
            double fromLng,
            double toLat,
            double toLng
    ) {}

    private record HeadlessPendingRoute(
            Driver driver,
            String requestId,
            GeoPoint from,
            GeoPoint to,
            int remainingTicks
    ) {}

    private volatile boolean headlessMode = false;

    public OsrmRoutingService() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "osrm-engine-processor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::processRequestQueue, 500, 150, TimeUnit.MILLISECONDS);
    }

    public void setHeadlessMode(boolean headless) {
        this.headlessMode = headless;
    }

    public void reset() {
        requestQueue.clear();
        pendingHeadlessRoutes.clear();
    }

    public void tearDown() {
        scheduler.shutdownNow();
    }

    public void requestRouteAsync(Driver driver,
                                  GeoPoint from,
                                  GeoPoint to,
                                  String requestId,
                                  int latencyTicks) {
        if (headlessMode) {
            if (latencyTicks <= 0) {
                injectFallbackPolyline(driver, requestId, from, to);
                return;
            }
            pendingHeadlessRoutes.put(driver.getId(), new HeadlessPendingRoute(
                    driver,
                    requestId,
                    from,
                    to,
                    latencyTicks
            ));
            return;
        }

        requestQueue.offer(new OsrmRequest(
                driver,
                requestId,
                from.lat(),
                from.lng(),
                to.lat(),
                to.lng()
        ));
    }

    public void advanceSimulationSubTick() {
        if (!headlessMode || pendingHeadlessRoutes.isEmpty()) {
            return;
        }

        List<String> completed = new ArrayList<>();
        for (Map.Entry<String, HeadlessPendingRoute> entry : pendingHeadlessRoutes.entrySet()) {
            HeadlessPendingRoute pending = entry.getValue();
            HeadlessPendingRoute updated = new HeadlessPendingRoute(
                    pending.driver(),
                    pending.requestId(),
                    pending.from(),
                    pending.to(),
                    pending.remainingTicks() - 1
            );

            if (updated.remainingTicks() <= 0) {
                if (isCurrentRequest(pending.driver(), pending.requestId())) {
                    injectFallbackPolyline(pending.driver(), pending.requestId(), pending.from(), pending.to());
                }
                completed.add(entry.getKey());
            } else {
                pendingHeadlessRoutes.put(entry.getKey(), updated);
            }
        }

        for (String driverId : completed) {
            pendingHeadlessRoutes.remove(driverId);
        }
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
                        ok = parseAndInjectRoute(
                                req.driver(),
                                req.requestId(),
                                response.body());
                    }
                    if (!ok) {
                        injectFallbackPolyline(
                                req.driver(),
                                req.requestId(),
                                new GeoPoint(req.fromLat(), req.fromLng()),
                                new GeoPoint(req.toLat(), req.toLng()));
                    }
                })
                .exceptionally(ex -> {
                    osrmSemaphore.release();
                    injectFallbackPolyline(
                            req.driver(),
                            req.requestId(),
                            new GeoPoint(req.fromLat(), req.fromLng()),
                            new GeoPoint(req.toLat(), req.toLng()));
                    return null;
                });
    }

    private boolean parseAndInjectRoute(Driver driver, String requestId, String body) {
        if (!isCurrentRequest(driver, requestId)) {
            return false;
        }

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!"Ok".equals(root.has("code") ? root.get("code").getAsString() : "")) {
                return false;
            }

            JsonArray routes = root.getAsJsonArray("routes");
            if (routes == null || routes.isEmpty()) {
                return false;
            }

            JsonArray coordsArray = routes.get(0).getAsJsonObject()
                    .getAsJsonObject("geometry")
                    .getAsJsonArray("coordinates");

            List<double[]> coordinates = new ArrayList<>();
            for (JsonElement coord : coordsArray) {
                JsonArray c = coord.getAsJsonArray();
                coordinates.add(new double[]{c.get(0).getAsDouble(), c.get(1).getAsDouble()});
            }

            if (coordinates.size() >= 2 && isCurrentRequest(driver, requestId)) {
                driver.setRouteWaypoints(
                        coordinates,
                        Driver.RouteGeometrySource.OSRM,
                        java.time.Instant.now());
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }

        return false;
    }

    private void injectFallbackPolyline(Driver driver,
                                        String requestId,
                                        GeoPoint from,
                                        GeoPoint to) {
        if (!isCurrentRequest(driver, requestId)) {
            return;
        }

        List<double[]> coordinates = new ArrayList<>();
        coordinates.add(new double[]{from.lng(), from.lat()});

        double midLat = (from.lat() + to.lat()) / 2.0;
        double midLng = (from.lng() + to.lng()) / 2.0;
        double jitterLat = (rng.nextDouble() - 0.5) * 0.0012;
        double jitterLng = (rng.nextDouble() - 0.5) * 0.0012;
        coordinates.add(new double[]{midLng + jitterLng, midLat + jitterLat});
        coordinates.add(new double[]{to.lng(), to.lat()});

        driver.setRouteWaypoints(
                coordinates,
                Driver.RouteGeometrySource.FALLBACK,
                java.time.Instant.now());
    }

    private boolean isCurrentRequest(Driver driver, String requestId) {
        return requestId != null && requestId.equals(driver.getRouteRequestId());
    }
}
