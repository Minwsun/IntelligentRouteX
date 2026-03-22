package com.routechain.infra;

import javafx.animation.AnimationTimer;
import javafx.geometry.Point2D;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure JavaFX map component — NO WebView.
 * 
 * Architecture:
 * - Canvas for tile rendering (drawn once per tile, cached)
 * - Canvas for overlays (routes, traffic, weather, orders)
 * - Circles/labels for drivers (animated via AnimationTimer)
 * - Mouse handlers for pan + zoom
 * - All data received via applyFrame() from MapBridge
 */
public class NativeMapPane extends Pane {

    private static final int TILE_SIZE = 256;
    private static final double LN2 = Math.log(2);

    // ── Map state ───────────────────────────────────────────────────────
    private double centerLat = 10.7769;
    private double centerLng = 106.7009;
    private double zoom = 13.0;
    private double minZoom = 11.0;
    private double maxZoom = 17.0;

    // ── Canvas layers ───────────────────────────────────────────────────
    private final Canvas tileCanvas;
    private final Canvas overlayCanvas;
    private final TileCache tileCache;

    // ── Pan state ───────────────────────────────────────────────────────
    private double dragStartX, dragStartY;
    private double dragStartLat, dragStartLng;
    private boolean dragging = false;

    // ── Data for overlays ───────────────────────────────────────────────
    private final Map<String, DriverState> drivers = new ConcurrentHashMap<>();
    private final Map<String, RouteData> routes = new ConcurrentHashMap<>();
    private final List<TrafficSegment> trafficSegments = Collections.synchronizedList(new ArrayList<>());
    private final List<WeatherZone> weatherZones = Collections.synchronizedList(new ArrayList<>());
    private final List<OrderPoint> orderPoints = Collections.synchronizedList(new ArrayList<>());

    // ── Layer visibility ────────────────────────────────────────────────
    private boolean showDrivers = true;
    private boolean showRoutes = true;
    private boolean showTraffic = true;
    private boolean showWeather = true;
    private boolean showOrders = true;

    // ── Callbacks ───────────────────────────────────────────────────────
    private Runnable onMapReady;
    private java.util.function.Consumer<String> onDriverSelected;

    // ── Animation ───────────────────────────────────────────────────────
    private boolean needsRepaint = true;

    // ── Data records ────────────────────────────────────────────────────
    public static class DriverState {
        public double curLat, curLng, tgtLat, tgtLng;
        public String state, name, id;
    }

    public static class RouteData {
        public String driverId, phase;
        public List<double[]> coordinates; // [lng, lat] pairs
    }

    public static class TrafficSegment {
        public List<double[]> coordinates;
        public double severity;
    }

    public static class WeatherZone {
        public List<double[]> polygon; // [lng, lat] points
        public double intensity;
    }

    public static class OrderPoint {
        public String id;
        public double lat, lng;
    }

    public NativeMapPane() {
        this.tileCache = new TileCache();
        this.tileCanvas = new Canvas();
        this.overlayCanvas = new Canvas();

        getChildren().addAll(tileCanvas, overlayCanvas);

        // Resize canvases with pane
        widthProperty().addListener((o, ov, nv) -> resizeCanvases());
        heightProperty().addListener((o, ov, nv) -> resizeCanvases());

        // Pan handlers
        setOnMousePressed(this::onMousePressed);
        setOnMouseDragged(this::onMouseDragged);
        setOnMouseReleased(this::onMouseReleased);

        // Zoom handler
        setOnScroll(this::onScroll);

        // Click handler for driver selection
        setOnMouseClicked(this::onMouseClicked);

        // Tile load callback — repaint when tiles arrive
        tileCache.setLoadCallback((tx, ty, z, img) -> {
            if ((int) zoom == z) {
                needsRepaint = true;
            }
        });

        // Animation loop — 30fps for overlays
        AnimationTimer timer = new AnimationTimer() {
            private long lastFrame = 0;

            @Override
            public void handle(long now) {
                // ~30fps
                if (now - lastFrame < 33_000_000L && !needsRepaint) return;
                lastFrame = now;

                // Animate driver positions
                animateDrivers();

                // Repaint tiles + overlays
                if (needsRepaint) {
                    renderTiles();
                    needsRepaint = false;
                }
                renderOverlays();
            }
        };
        timer.start();

        // Notify map ready after a short delay
        javafx.application.Platform.runLater(() ->
                javafx.application.Platform.runLater(() -> {
                    if (onMapReady != null) onMapReady.run();
                })
        );

        setStyle("-fx-background-color: #1e2022;");
    }

    // ════════════════════════════════════════════════════════════════════
    // CALLBACKS
    // ════════════════════════════════════════════════════════════════════

    public void setOnMapReady(Runnable r) { this.onMapReady = r; }
    public void setOnDriverSelected(java.util.function.Consumer<String> c) { this.onDriverSelected = c; }

    // ════════════════════════════════════════════════════════════════════
    // LAYER TOGGLE
    // ════════════════════════════════════════════════════════════════════

    public void toggleLayer(String layer, boolean visible) {
        switch (layer) {
            case "drivers" -> showDrivers = visible;
            case "routes" -> showRoutes = visible;
            case "traffic" -> showTraffic = visible;
            case "weather" -> showWeather = visible;
            case "orders" -> showOrders = visible;
        }
        needsRepaint = true;
    }

    // ════════════════════════════════════════════════════════════════════
    // DATA SETTERS (called from MapBridge.flushFrame)
    // ════════════════════════════════════════════════════════════════════

    public void setDriverPositions(Map<String, DriverState> newDrivers) {
        // Update targets, keep current positions for interpolation
        for (var entry : newDrivers.entrySet()) {
            DriverState existing = drivers.get(entry.getKey());
            if (existing != null) {
                existing.tgtLat = entry.getValue().tgtLat;
                existing.tgtLng = entry.getValue().tgtLng;
                existing.state = entry.getValue().state;
                existing.name = entry.getValue().name;
            } else {
                drivers.put(entry.getKey(), entry.getValue());
            }
        }
        // Remove stale drivers
        drivers.keySet().retainAll(newDrivers.keySet());
        needsRepaint = true;
    }

    public void setRoutes(Map<String, RouteData> newRoutes) {
        routes.clear();
        routes.putAll(newRoutes);
        needsRepaint = true;
    }

    public void setTrafficSegments(List<TrafficSegment> segments) {
        synchronized (trafficSegments) {
            trafficSegments.clear();
            trafficSegments.addAll(segments);
        }
        needsRepaint = true;
    }

    public void setWeatherZones(List<WeatherZone> zones) {
        synchronized (weatherZones) {
            weatherZones.clear();
            weatherZones.addAll(zones);
        }
        needsRepaint = true;
    }

    public void setOrderPoints(List<OrderPoint> points) {
        synchronized (orderPoints) {
            orderPoints.clear();
            orderPoints.addAll(points);
        }
        needsRepaint = true;
    }

    public void focusDriver(String driverId) {
        DriverState ds = drivers.get(driverId);
        if (ds != null) {
            centerLat = ds.curLat;
            centerLng = ds.curLng;
            zoom = 15.0;
            needsRepaint = true;
        }
    }

    public void centerMap(double lat, double lng, double z) {
        centerLat = lat;
        centerLng = lng;
        zoom = z;
        needsRepaint = true;
    }

    // ════════════════════════════════════════════════════════════════════
    // CANVAS MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    private void resizeCanvases() {
        double w = getWidth();
        double h = getHeight();
        tileCanvas.setWidth(w);
        tileCanvas.setHeight(h);
        overlayCanvas.setWidth(w);
        overlayCanvas.setHeight(h);
        needsRepaint = true;
    }

    // ════════════════════════════════════════════════════════════════════
    // TILE RENDERING
    // ════════════════════════════════════════════════════════════════════

    private void renderTiles() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = tileCanvas.getGraphicsContext2D();
        gc.setFill(Color.web("#1e2022"));
        gc.fillRect(0, 0, w, h);

        int z = (int) Math.round(zoom);
        double scale = Math.pow(2, zoom - z);
        int numTiles = 1 << z;

        // Center pixel position
        double centerX = lngToTileX(centerLng, z) * TILE_SIZE;
        double centerY = latToTileY(centerLat, z) * TILE_SIZE;

        // Viewport bounds in tile pixels
        double vpLeft = centerX - (w / 2.0) / scale;
        double vpTop = centerY - (h / 2.0) / scale;
        double vpRight = centerX + (w / 2.0) / scale;
        double vpBottom = centerY + (h / 2.0) / scale;

        // Tile index range
        int minTX = Math.max(0, (int) Math.floor(vpLeft / TILE_SIZE));
        int minTY = Math.max(0, (int) Math.floor(vpTop / TILE_SIZE));
        int maxTX = Math.min(numTiles - 1, (int) Math.floor(vpRight / TILE_SIZE));
        int maxTY = Math.min(numTiles - 1, (int) Math.floor(vpBottom / TILE_SIZE));

        // Draw visible tiles
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                Image tile = tileCache.getTile(tx, ty, z);
                if (tile != null) {
                    double px = (tx * TILE_SIZE - vpLeft) * scale;
                    double py = (ty * TILE_SIZE - vpTop) * scale;
                    double sz = TILE_SIZE * scale;
                    gc.drawImage(tile, px, py, sz, sz);
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // OVERLAY RENDERING (routes, drivers, traffic, weather, orders)
    // ════════════════════════════════════════════════════════════════════

    private void renderOverlays() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = overlayCanvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        // Draw weather zones (bottom layer)
        if (showWeather) drawWeather(gc);

        // Draw traffic segments
        if (showTraffic) drawTraffic(gc);

        // Draw routes
        if (showRoutes) drawRoutes(gc);

        // Draw orders
        if (showOrders) drawOrders(gc);

        // Draw drivers (top layer)
        if (showDrivers) drawDrivers(gc);
    }

    private void drawWeather(GraphicsContext gc) {
        synchronized (weatherZones) {
            for (WeatherZone wz : weatherZones) {
                if (wz.intensity < 0.05 || wz.polygon == null || wz.polygon.size() < 3) continue;
                gc.setFill(Color.rgb(110, 187, 238, wz.intensity * 0.15));
                gc.setStroke(Color.rgb(110, 187, 238, wz.intensity * 0.3));
                gc.setLineWidth(1);
                gc.setLineDashes(6, 4);
                gc.beginPath();
                boolean first = true;
                for (double[] coord : wz.polygon) {
                    Point2D pt = geoToScreen(coord[1], coord[0]);
                    if (first) { gc.moveTo(pt.getX(), pt.getY()); first = false; }
                    else gc.lineTo(pt.getX(), pt.getY());
                }
                gc.closePath();
                gc.fill();
                gc.stroke();
                gc.setLineDashes(null);
            }
        }
    }

    private void drawTraffic(GraphicsContext gc) {
        synchronized (trafficSegments) {
            for (TrafficSegment seg : trafficSegments) {
                if (seg.severity < 0.1 || seg.coordinates == null || seg.coordinates.size() < 2) continue;
                Color color = seg.severity > 0.7 ? Color.web("#d7383b") :
                        seg.severity > 0.4 ? Color.web("#ff716c") : Color.web("#ff9966");
                gc.setStroke(color.deriveColor(0, 1, 1, 0.4 + seg.severity * 0.3));
                gc.setLineWidth(3 + seg.severity * 4);
                gc.setLineDashes(null);
                gc.beginPath();
                boolean first = true;
                for (double[] coord : seg.coordinates) {
                    Point2D pt = geoToScreen(coord[1], coord[0]);
                    if (first) { gc.moveTo(pt.getX(), pt.getY()); first = false; }
                    else gc.lineTo(pt.getX(), pt.getY());
                }
                gc.stroke();
            }
        }
    }

    private void drawRoutes(GraphicsContext gc) {
        for (RouteData route : routes.values()) {
            if (route.coordinates == null || route.coordinates.size() < 2) continue;
            boolean isPickup = "pickup".equals(route.phase);
            Color color = isPickup ? Color.web("#FFBE6B") : Color.web("#00ffab");

            // Glow
            gc.setStroke(color.deriveColor(0, 1, 1, 0.12));
            gc.setLineWidth(8);
            gc.setLineDashes(null);
            gc.beginPath();
            boolean first = true;
            for (double[] coord : route.coordinates) {
                Point2D pt = geoToScreen(coord[1], coord[0]);
                if (first) { gc.moveTo(pt.getX(), pt.getY()); first = false; }
                else gc.lineTo(pt.getX(), pt.getY());
            }
            gc.stroke();

            // Main line
            gc.setStroke(color.deriveColor(0, 1, 1, 0.85));
            gc.setLineWidth(3);
            if (isPickup) gc.setLineDashes(8, 5);
            else gc.setLineDashes(null);
            gc.beginPath();
            first = true;
            for (double[] coord : route.coordinates) {
                Point2D pt = geoToScreen(coord[1], coord[0]);
                if (first) { gc.moveTo(pt.getX(), pt.getY()); first = false; }
                else gc.lineTo(pt.getX(), pt.getY());
            }
            gc.stroke();
            gc.setLineDashes(null);

            // Destination dot
            double[] last = route.coordinates.get(route.coordinates.size() - 1);
            Point2D destPt = geoToScreen(last[1], last[0]);
            gc.setFill(color);
            gc.setStroke(Color.WHITE);
            gc.setLineWidth(1.5);
            gc.fillOval(destPt.getX() - 5, destPt.getY() - 5, 10, 10);
            gc.strokeOval(destPt.getX() - 5, destPt.getY() - 5, 10, 10);
        }
    }

    private void drawOrders(GraphicsContext gc) {
        synchronized (orderPoints) {
            gc.setFill(Color.rgb(255, 113, 108, 0.45));
            for (OrderPoint op : orderPoints) {
                Point2D pt = geoToScreen(op.lat, op.lng);
                gc.fillOval(pt.getX() - 2, pt.getY() - 2, 4, 4);
            }
        }
    }

    private void drawDrivers(GraphicsContext gc) {
        for (DriverState ds : drivers.values()) {
            Point2D pt = geoToScreen(ds.curLat, ds.curLng);
            boolean isBusy = !"ONLINE_IDLE".equals(ds.state);
            Color color = isBusy ? Color.web("#99f7ff") : Color.web("#00ffab");
            double size = isBusy ? 12 : 8;

            // Glow
            gc.setFill(color.deriveColor(0, 1, 1, 0.25));
            gc.fillOval(pt.getX() - size, pt.getY() - size, size * 2, size * 2);

            // Dot
            gc.setFill(color);
            gc.fillOval(pt.getX() - size / 2, pt.getY() - size / 2, size, size);

            // Name label (only at higher zoom)
            if (zoom >= 14) {
                gc.setFill(Color.web("#99f7ff", 0.8));
                gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9));
                gc.fillText(ds.name != null ? ds.name : ds.id, pt.getX() + size, pt.getY() - 4);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // DRIVER ANIMATION
    // ════════════════════════════════════════════════════════════════════

    private void animateDrivers() {
        double t = 0.08; // interpolation factor
        for (DriverState ds : drivers.values()) {
            if (ds.curLat != ds.tgtLat || ds.curLng != ds.tgtLng) {
                ds.curLat += (ds.tgtLat - ds.curLat) * t;
                ds.curLng += (ds.tgtLng - ds.curLng) * t;
                if (Math.abs(ds.curLat - ds.tgtLat) < 0.0000005 &&
                        Math.abs(ds.curLng - ds.tgtLng) < 0.0000005) {
                    ds.curLat = ds.tgtLat;
                    ds.curLng = ds.tgtLng;
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MOUSE HANDLERS (pan + zoom)
    // ════════════════════════════════════════════════════════════════════

    private void onMousePressed(MouseEvent e) {
        dragStartX = e.getX();
        dragStartY = e.getY();
        dragStartLat = centerLat;
        dragStartLng = centerLng;
        dragging = true;
    }

    private void onMouseDragged(MouseEvent e) {
        if (!dragging) return;
        double dx = e.getX() - dragStartX;
        double dy = e.getY() - dragStartY;

        double scale = TILE_SIZE * Math.pow(2, zoom);
        double dLng = -dx / scale * 360.0;
        double dLat = dy / scale * 360.0; // Simplified — Mercator correction

        centerLat = dragStartLat + dLat;
        centerLng = dragStartLng + dLng;

        // Clamp
        centerLat = Math.max(-85, Math.min(85, centerLat));
        centerLng = Math.max(-180, Math.min(180, centerLng));

        needsRepaint = true;
    }

    private void onMouseReleased(MouseEvent e) {
        dragging = false;
    }

    private void onScroll(ScrollEvent e) {
        double delta = e.getDeltaY() > 0 ? 0.5 : -0.5;
        double newZoom = Math.max(minZoom, Math.min(maxZoom, zoom + delta));
        if (newZoom != zoom) {
            zoom = newZoom;
            needsRepaint = true;
        }
    }

    private void onMouseClicked(MouseEvent e) {
        if (e.getClickCount() == 1 && onDriverSelected != null) {
            Point2D click = new Point2D(e.getX(), e.getY());
            for (DriverState ds : drivers.values()) {
                Point2D pt = geoToScreen(ds.curLat, ds.curLng);
                if (click.distance(pt) < 15) {
                    onDriverSelected.accept(ds.id);
                    break;
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // GEO MATH
    // ════════════════════════════════════════════════════════════════════

    private double lngToTileX(double lng, int z) {
        return (lng + 180.0) / 360.0 * (1 << z);
    }

    private double latToTileY(double lat, int z) {
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << z);
    }

    /**
     * Convert geographic coordinates to screen pixel position.
     */
    private Point2D geoToScreen(double lat, double lng) {
        int z = (int) Math.round(zoom);
        double scale = Math.pow(2, zoom - z);

        double centerPxX = lngToTileX(centerLng, z) * TILE_SIZE;
        double centerPxY = latToTileY(centerLat, z) * TILE_SIZE;
        double pointPxX = lngToTileX(lng, z) * TILE_SIZE;
        double pointPxY = latToTileY(lat, z) * TILE_SIZE;

        double screenX = (pointPxX - centerPxX) * scale + getWidth() / 2.0;
        double screenY = (pointPxY - centerPxY) * scale + getHeight() / 2.0;

        return new Point2D(screenX, screenY);
    }
}
