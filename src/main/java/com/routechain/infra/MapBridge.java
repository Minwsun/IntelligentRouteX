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

    // ── OSRM routing for Map rendering (HTTP client kept for geometry fetch) ─
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .executor(Executors.newFixedThreadPool(4))
            .build();

    // ── Corridor road-snapped geometries (OSRM, fetched once at startup) ─
    private final Map<String, List<double[]>> corridorGeometries = new ConcurrentHashMap<>();
    private volatile boolean corridorGeometriesFetched = false;

    public MapBridge(NativeMapPane mapPane) {
        this.mapPane = mapPane;
        this.cachedCorridors = HcmcCityData.createCorridors();
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
        fetchCorridorGeometries(); // One-time OSRM fetch for road-snapped traffic
    }

    public void setOnDriverSelected(java.util.function.Consumer<String> callback) {
        mapPane.setOnDriverSelected(callback);
    }

    public void setOnMapReady(Runnable callback) {
        mapPane.setOnMapReady(callback);
    }

    // ════════════════════════════════════════════════════════════════════
    // FRAME-BASED BATCH DISPATCHER
    // ════════════════════════════════════════════════════════════════════

    /**
     * Flush all pending updates to NativeMapPane.
     * Called once per simulation tick from MainApp.
     */
    public void flushFrame() {
        // --- Drivers & Routes ---
        if (pendingDrivers != null) {
            Map<String, NativeMapPane.DriverState> driverMap = new LinkedHashMap<>();
            Map<String, NativeMapPane.RouteData> routeMap = new LinkedHashMap<>();
            
            for (Driver d : pendingDrivers) {
                // Driver marker
                NativeMapPane.DriverState ds = new NativeMapPane.DriverState();
                ds.id = d.getId();
                ds.name = d.getName();
                ds.state = d.getState().name();
                ds.tgtLat = d.getCurrentLocation().lat();
                ds.tgtLng = d.getCurrentLocation().lng();
                ds.curLat = ds.tgtLat;
                ds.curLng = ds.tgtLng;
                if (d.getTargetLocation() != null) {
                    ds.destLat = d.getTargetLocation().lat();
                    ds.destLng = d.getTargetLocation().lng();
                    ds.hasDest = true;
                } else {
                    ds.hasDest = false;
                }
                driverMap.put(d.getId(), ds);
                
                // Route lines
                if (d.hasRouteWaypoints()) {
                    List<double[]> pts = d.getRemainingRoutePoints();
                    if (pts != null && pts.size() >= 2) {
                        NativeMapPane.RouteData rd = new NativeMapPane.RouteData();
                        rd.driverId = d.getId();
                        rd.phase = d.getState() == com.routechain.domain.Enums.DriverState.PICKUP_EN_ROUTE ? "pickup" : "delivery";
                        rd.coordinates = pts;
                        routeMap.put(d.getId(), rd);
                    }
                }
            }
            
            Platform.runLater(() -> {
                mapPane.setDriverPositions(driverMap);
                mapPane.setRoutes(routeMap);
            });
            pendingDrivers = null;
        }

        // --- Traffic (road-snapped via OSRM geometries) ---
        if (pendingTraffic != null) {
            List<NativeMapPane.TrafficSegment> segments = new ArrayList<>();
            for (var corridor : cachedCorridors) {
                Double severity = pendingTraffic.getOrDefault(corridor.id(), 0.0);
                if (severity < 0.05) continue;
                NativeMapPane.TrafficSegment seg = new NativeMapPane.TrafficSegment();
                seg.severity = severity;
                // Use OSRM road geometry if available, fallback to straight line
                List<double[]> roadGeometry = corridorGeometries.get(corridor.id());
                if (roadGeometry != null && roadGeometry.size() >= 2) {
                    seg.coordinates = new ArrayList<>(roadGeometry);
                } else {
                    seg.coordinates = List.of(
                            new double[]{corridor.from().lng(), corridor.from().lat()},
                            new double[]{corridor.to().lng(), corridor.to().lat()}
                    );
                }
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
                op.pickupLat = order.getPickupPoint().lat();
                op.pickupLng = order.getPickupPoint().lng();
                op.dropoffLat = order.getDropoffPoint().lat();
                op.dropoffLng = order.getDropoffPoint().lng();
                op.assignedDriverId = order.getAssignedDriverId();
                op.isPickedUp = (order.getStatus() == Enums.OrderStatus.PICKED_UP || order.getStatus() == Enums.OrderStatus.DELIVERED);
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
    // OSRM GEOMETRY FETCH FOR TRAFFIC
    // ════════════════════════════════════════════════════════════════════

    // ── Layer toggle ────────────────────────────────────────────────────
    public void toggleLayer(String layer, boolean visible) {
        Platform.runLater(() -> mapPane.toggleLayer(layer, visible));
    }

    public void focusDriver(String id) {
        Platform.runLater(() -> mapPane.focusDriver(id));
    }

    // ── Corridor geometry fetch (one-time OSRM for road-snapped traffic) ─
    /**
     * Fetch OSRM road geometry for all traffic corridors once at startup.
     * Results are cached permanently — corridor topology never changes.
     */
    private void fetchCorridorGeometries() {
        if (corridorGeometriesFetched) return;
        corridorGeometriesFetched = true;

        CompletableFuture.runAsync(() -> {
            System.out.println("[MapBridge] Fetching OSRM geometries for " + cachedCorridors.size() + " traffic corridors...");
            for (var corridor : cachedCorridors) {
                try {
                    String url = String.format(Locale.US,
                            "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=geojson",
                            corridor.from().lng(), corridor.from().lat(),
                            corridor.to().lng(), corridor.to().lat());

                    HttpRequest req = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(Duration.ofSeconds(10))
                            .header("User-Agent", "RouteChainAI/1.0")
                            .GET()
                            .build();

                    HttpResponse<String> response = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        List<double[]> coords = parseOsrmGeometry(response.body());
                        if (coords != null && coords.size() >= 2) {
                            corridorGeometries.put(corridor.id(), coords);
                            System.out.println("[MapBridge] ✓ Corridor " + corridor.name() + ": " + coords.size() + " points");
                        }
                    }
                    // Throttle to avoid OSRM rate limiting
                    Thread.sleep(300);
                } catch (Exception e) {
                    System.err.println("[MapBridge] ✗ Corridor " + corridor.name() + ": " + e.getMessage());
                }
            }
            System.out.println("[MapBridge] Corridor geometry fetch complete: " + corridorGeometries.size() + "/" + cachedCorridors.size());
        });
    }

    /**
     * Parse OSRM response body to extract route coordinates.
     * Returns list of [lng, lat] pairs matching NativeMapPane coordinate format.
     */
    private List<double[]> parseOsrmGeometry(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!"Ok".equals(root.has("code") ? root.get("code").getAsString() : "")) return null;
            JsonArray routes = root.getAsJsonArray("routes");
            if (routes != null && !routes.isEmpty()) {
                JsonArray coordsArray = routes.get(0).getAsJsonObject()
                        .getAsJsonObject("geometry").getAsJsonArray("coordinates");
                List<double[]> coordinates = new ArrayList<>();
                for (JsonElement coord : coordsArray) {
                    JsonArray c = coord.getAsJsonArray();
                    coordinates.add(new double[]{c.get(0).getAsDouble(), c.get(1).getAsDouble()});
                }
                return coordinates.size() >= 2 ? coordinates : null;
            }
        } catch (Exception e) {
            System.err.println("[MapBridge] OSRM corridor parse error: " + e.getMessage());
        }
        return null;
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
