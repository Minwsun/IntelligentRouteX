package com.routechain.infra;

import com.google.gson.*;
import com.routechain.domain.*;
import com.routechain.simulation.HcmcCityData;
import javafx.application.Platform;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bridge between Java simulation and NativeMapPane.
 * All updates accumulate as pending state, then flush in a single
 * flushFrame() call that pushes data directly to the NativeMapPane.
 *
 * Architecture:
 * - NO WebView, NO JavaScript
 * - Dirty flags track which data sources changed
 * - flushFrame() pushes data to NativeMapPane Java methods
 * - OSRM routing remains async with throttled queue
 */
public class MapBridge {
    private final NativeMapPane mapPane;
    private volatile boolean mapReady = false;
    private final List<HcmcCityData.TrafficCorridor> cachedCorridors;

    // ── Frame state: accumulate updates ─────────────────────────────────
    private volatile List<Driver> pendingDrivers = null;
    private volatile Map<String, Double> pendingTraffic = null;
    private volatile List<Region> pendingWeather = null;
    private volatile List<Order> pendingOrders = null;
    private volatile boolean pendingRoadNetwork = false;

    // ── OSRM routing via Java HttpClient ────────────────────────────────
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    private final Map<String, CachedRoute> routeCache = new ConcurrentHashMap<>();
    private final Map<String, String> lastRequestedDest = new ConcurrentHashMap<>();
    private final Semaphore osrmSemaphore = new Semaphore(3);
    private final ConcurrentLinkedQueue<OsrmRequest> requestQueue = new ConcurrentLinkedQueue<>();
    private final AtomicInteger osrmSuccessCount = new AtomicInteger(0);
    private final AtomicInteger osrmFailCount = new AtomicInteger(0);
    private volatile String lastRoutesHash = "";
    private volatile boolean routesDirty = false;

    // Driver lookup for pushing route waypoints to simulation
    private volatile java.util.function.Function<String, Driver> driverLookup;

    private record CachedRoute(String destKey, List<double[]> coordinates, String phase) {}
    private record OsrmRequest(String driverId, String destKey, String phase,
                                double fromLat, double fromLng, double toLat, double toLng) {}

    public MapBridge(NativeMapPane mapPane) {
        this.mapPane = mapPane;
        this.cachedCorridors = HcmcCityData.createCorridors();

        // OSRM queue processor
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "osrm-queue-processor");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::processRequestQueue, 500, 200, TimeUnit.MILLISECONDS);
    }

    /**
     * Attach callbacks. Call once after construction.
     */
    public void attach() {
        mapPane.setOnMapReady(() -> {
            mapReady = true;
            System.out.println("[MapBridge] Native map ready");
        });
        mapReady = true; // Native pane is always ready
    }

    public void setOnDriverSelected(java.util.function.Consumer<String> callback) {
        mapPane.setOnDriverSelected(callback);
    }

    public void setOnMapReady(Runnable callback) {
        mapPane.setOnMapReady(callback);
    }

    /**
     * Set driver lookup function for pushing OSRM waypoints to drivers.
     */
    public void setDriverLookup(java.util.function.Function<String, Driver> lookup) {
        this.driverLookup = lookup;
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAME-BASED BATCH DISPATCHER
    // ════════════════════════════════════════════════════════════════════

    /**
     * Flush all pending updates to NativeMapPane.
     * Called once per simulation tick from MainApp.
     */
    public void flushFrame() {
        // --- Drivers ---
        if (pendingDrivers != null) {
            Map<String, NativeMapPane.DriverState> driverMap = new LinkedHashMap<>();
            for (Driver d : pendingDrivers) {
                NativeMapPane.DriverState ds = new NativeMapPane.DriverState();
                ds.id = d.getId();
                ds.name = d.getName();
                ds.state = d.getState().name();
                ds.tgtLat = d.getCurrentLocation().lat();
                ds.tgtLng = d.getCurrentLocation().lng();
                ds.curLat = ds.tgtLat;
                ds.curLng = ds.tgtLng;
                driverMap.put(d.getId(), ds);
            }
            Platform.runLater(() -> mapPane.setDriverPositions(driverMap));
            pendingDrivers = null;
        }

        // --- Routes ---
        if (routesDirty) {
            Map<String, NativeMapPane.RouteData> routeMap = new LinkedHashMap<>();
            for (var entry : routeCache.entrySet()) {
                CachedRoute cr = entry.getValue();
                if (cr.coordinates().size() < 2) continue;
                NativeMapPane.RouteData rd = new NativeMapPane.RouteData();
                rd.driverId = entry.getKey();
                rd.phase = cr.phase();
                rd.coordinates = cr.coordinates();
                routeMap.put(entry.getKey(), rd);
            }
            Platform.runLater(() -> mapPane.setRoutes(routeMap));
            routesDirty = false;
        }

        // --- Traffic ---
        if (pendingTraffic != null) {
            List<NativeMapPane.TrafficSegment> segments = new ArrayList<>();
            for (var corridor : cachedCorridors) {
                Double severity = pendingTraffic.getOrDefault(corridor.id(), 0.0);
                if (severity < 0.05) continue;
                NativeMapPane.TrafficSegment seg = new NativeMapPane.TrafficSegment();
                seg.severity = severity;
                seg.coordinates = List.of(
                        new double[]{corridor.from().lng(), corridor.from().lat()},
                        new double[]{corridor.to().lng(), corridor.to().lat()}
                );
                segments.add(seg);
            }
            Platform.runLater(() -> mapPane.setTrafficSegments(segments));
            pendingTraffic = null;
        }

        // --- Weather ---
        if (pendingWeather != null) {
            List<NativeMapPane.WeatherZone> zones = new ArrayList<>();
            for (var region : pendingWeather) {
                if (region.getRainIntensity() < 0.05) continue;
                NativeMapPane.WeatherZone wz = new NativeMapPane.WeatherZone();
                wz.intensity = region.getRainIntensity();
                wz.polygon = createCirclePolygon(region.getCenter().lat(), region.getCenter().lng(),
                        region.getRadiusMeters(), 24);
                zones.add(wz);
            }
            Platform.runLater(() -> mapPane.setWeatherZones(zones));
            pendingWeather = null;
        }

        // --- Orders ---
        if (pendingOrders != null) {
            List<NativeMapPane.OrderPoint> points = new ArrayList<>();
            for (Order order : pendingOrders) {
                NativeMapPane.OrderPoint op = new NativeMapPane.OrderPoint();
                op.id = order.getId();
                op.lat = order.getPickupPoint().lat();
                op.lng = order.getPickupPoint().lng();
                points.add(op);
            }
            Platform.runLater(() -> mapPane.setOrderPoints(points));
            pendingOrders = null;
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DATA SETTERS — accumulate into pending state
    // ════════════════════════════════════════════════════════════════════

    public void setDriverPositions(List<Driver> drivers) {
        this.pendingDrivers = new ArrayList<>(drivers);
    }

    public void setTrafficData(Map<String, Double> corridorSeverity) {
        this.pendingTraffic = new HashMap<>(corridorSeverity);
    }

    public void setWeatherData(List<Region> regions) {
        this.pendingWeather = new ArrayList<>(regions);
    }

    public void setOrderData(List<Order> orders) {
        this.pendingOrders = new ArrayList<>(orders);
    }

    public void setRoadNetworkData() {
        // Not needed in native renderer — traffic corridors are drawn directly
    }

    // ════════════════════════════════════════════════════════════════════
    // OSRM ROUTING — async, throttled, cached (unchanged architecture)
    // ════════════════════════════════════════════════════════════════════

    public void buildAndSetRouteData() {
        String hash = String.valueOf(routeCache.size()) + "_" + routeCache.hashCode();
        if (!hash.equals(lastRoutesHash)) {
            lastRoutesHash = hash;
            routesDirty = true;
        }
    }

    public void requestDriverRoute(String driverId, double fromLat, double fromLng,
                                    double toLat, double toLng, String phase) {
        String destKey = Math.round(toLat * 1000) + "_" + Math.round(toLng * 1000) + "_" + phase;
        CachedRoute cached = routeCache.get(driverId);
        if (cached != null && cached.destKey().equals(destKey) && cached.coordinates().size() > 15) {
            return;
        }
        String lastReq = lastRequestedDest.get(driverId);
        if (destKey.equals(lastReq)) return;
        lastRequestedDest.put(driverId, destKey);
        requestQueue.offer(new OsrmRequest(driverId, destKey, phase, fromLat, fromLng, toLat, toLng));
    }

    public void clearDriverRoute(String driverId) {
        routeCache.remove(driverId);
        lastRequestedDest.remove(driverId);
    }

    private void processRequestQueue() {
        while (!requestQueue.isEmpty() && osrmSemaphore.tryAcquire()) {
            OsrmRequest req = requestQueue.poll();
            if (req == null) { osrmSemaphore.release(); break; }
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
                        boolean ok = parseAndCacheRoute(req.driverId(), req.destKey(), req.phase(), response.body());
                        if (ok) {
                            int c = osrmSuccessCount.incrementAndGet();
                            if (c <= 5 || c % 10 == 0)
                                System.out.println("[OSRM] ✓ Route OK for " + req.driverId() + " (" + c + " total, " + osrmFailCount.get() + " fail)");
                        } else {
                            osrmFailCount.incrementAndGet();
                            lastRequestedDest.remove(req.driverId());
                        }
                    } else {
                        osrmFailCount.incrementAndGet();
                        lastRequestedDest.remove(req.driverId());
                    }
                })
                .exceptionally(ex -> {
                    osrmSemaphore.release();
                    osrmFailCount.incrementAndGet();
                    lastRequestedDest.remove(req.driverId());
                    System.err.println("[OSRM] ✗ " + req.driverId() + ": " + ex.getMessage());
                    return null;
                });
    }

    private boolean parseAndCacheRoute(String driverId, String destKey, String phase, String body) {
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
                    coordinates.add(new double[]{c.get(0).getAsDouble(), c.get(1).getAsDouble()});
                }
                if (coordinates.size() >= 2) {
                    routeCache.put(driverId, new CachedRoute(destKey, coordinates, phase));
                    // Push waypoints to driver for road-following movement
                    var lookup = driverLookup;
                    if (lookup != null) {
                        Driver driver = lookup.apply(driverId);
                        if (driver != null) {
                            driver.setRouteWaypoints(coordinates);
                        }
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("[OSRM] Parse error: " + e.getMessage());
        }
        return false;
    }

    // ── Layer toggle ────────────────────────────────────────────────────
    public void toggleLayer(String layer, boolean visible) {
        Platform.runLater(() -> mapPane.toggleLayer(layer, visible));
    }

    public void focusDriver(String id) {
        Platform.runLater(() -> mapPane.focusDriver(id));
    }

    // ── Helpers ──────────────────────────────────────────────────────────
    private List<double[]> createCirclePolygon(double lat, double lng, double radiusM, int points) {
        List<double[]> ring = new ArrayList<>();
        for (int i = 0; i <= points; i++) {
            double angle = (2 * Math.PI / points) * i;
            double dLat = radiusM * Math.cos(angle) / 111320.0;
            double dLng = radiusM * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(lat)));
            ring.add(new double[]{lng + dLng, lat + dLat});
        }
        return ring;
    }
}
