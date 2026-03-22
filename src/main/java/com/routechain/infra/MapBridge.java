package com.routechain.infra;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.HcmcCityData;
import javafx.application.Platform;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

import java.util.*;

/**
 * Bridge between Java simulation and JavaScript map.
 * Pushes driver positions, traffic, weather, orders to the WebView map.
 */
public class MapBridge {
    private final WebEngine engine;
    private final Gson gson = new GsonBuilder().create();
    private volatile boolean mapReady = false;
    private final List<Runnable> pendingCommands = new ArrayList<>();
    private final List<HcmcCityData.TrafficCorridor> cachedCorridors;

    // JS callback object exposed to the WebView
    public final JavaBridgeCallback callback = new JavaBridgeCallback();

    public MapBridge(WebEngine engine) {
        this.engine = engine;
        this.cachedCorridors = HcmcCityData.createCorridors();
    }

    public void attach() {
        engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == javafx.concurrent.Worker.State.SUCCEEDED) {
                JSObject window = (JSObject) engine.executeScript("window");
                window.setMember("javaBridge", callback);
                mapReady = true;
                // Execute pending commands
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
            // Create a circle polygon approximation
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

    // ── Road network overlay ───────────────────────────────────────────
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

    // ── Driver route (OSRM) ─────────────────────────────────────────────
    public void requestDriverRoute(String driverId, double fromLat, double fromLng,
                                    double toLat, double toLng, String phase) {
        execJs(String.format("window.appMap.requestRoute('%s', %f, %f, %f, %f, '%s')",
                driverId, fromLat, fromLng, toLat, toLng, phase));
    }

    public void clearDriverRoute(String driverId) {
        execJs("window.appMap.clearRoute('" + driverId + "')");
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
