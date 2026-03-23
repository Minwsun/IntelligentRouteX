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
    private String selectedDriverId = null;

    // ── Animation ───────────────────────────────────────────────────────
    private boolean needsRepaint = true;

    // ── Data records ────────────────────────────────────────────────────
    public static class DriverState {
        public double curLat, curLng, tgtLat, tgtLng;
        public double destLat, destLng;
        public boolean hasDest;
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
        public double pickupLat, pickupLng;
        public double dropoffLat, dropoffLng;
        public String assignedDriverId;
        public boolean isPickedUp;
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
                existing.destLat = entry.getValue().destLat;
                existing.destLng = entry.getValue().destLng;
                existing.hasDest = entry.getValue().hasDest;
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
        selectedDriverId = driverId;
        DriverState ds = drivers.get(driverId);
        if (ds != null) {
            centerLat = ds.curLat;
            centerLng = ds.curLng;
            zoom = 15.0;
            needsRepaint = true;
        }
    }

    public void clearSelectedDriver() {
        this.selectedDriverId = null;
        needsRepaint = true;
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

        // Draw visible tiles — use placeholder from parent zoom if tile not cached
        for (int tx = minTX; tx <= maxTX; tx++) {
            for (int ty = minTY; ty <= maxTY; ty++) {
                double px = (tx * TILE_SIZE - vpLeft) * scale;
                double py = (ty * TILE_SIZE - vpTop) * scale;
                double sz = TILE_SIZE * scale;

                Image tile = tileCache.getTile(tx, ty, z);
                if (tile != null) {
                    gc.drawImage(tile, px, py, sz, sz);
                } else {
                    // Placeholder: draw scaled sub-region from a parent zoom tile
                    TileCache.PlaceholderResult ph = tileCache.getPlaceholderTile(tx, ty, z);
                    if (ph != null) {
                        gc.drawImage(ph.image(),
                                ph.srcX(), ph.srcY(), ph.srcSize(), ph.srcSize(),
                                px, py, sz, sz);
                    }
                }
            }
        }

        // Preload ±1 zoom for smoother future transitions
        if (z > 0) {
            int pMinX = minTX >> 1, pMinY = minTY >> 1;
            int pMaxX = maxTX >> 1, pMaxY = maxTY >> 1;
            tileCache.preload(pMinX, pMinY, pMaxX, pMaxY, z - 1);
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

        // Draw driver→destination fallback lines
        if (showRoutes) drawDriverDestLines(gc);

        // Draw orders
        if (showOrders) drawOrders(gc);

        // Draw drivers (top layer)
        if (showDrivers) drawDrivers(gc);

        // Draw legend (always on top)
        drawLegend(gc, w, h);
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
                // Google Maps-style traffic colors: green → orange → red
                Color color = seg.severity > 0.7 ? Color.web("#d7383b") :
                        seg.severity > 0.4 ? Color.web("#ff716c") : Color.web("#ff9966");

                gc.setLineCap(javafx.scene.shape.StrokeLineCap.ROUND);
                gc.setLineJoin(javafx.scene.shape.StrokeLineJoin.ROUND);
                gc.setLineDashes(null);

                // Glow layer (wider, semi-transparent) — road outline effect
                gc.setStroke(color.deriveColor(0, 1, 1, 0.15));
                gc.setLineWidth(8 + seg.severity * 3);
                gc.beginPath();
                boolean first = true;
                for (double[] coord : seg.coordinates) {
                    Point2D pt = geoToScreen(coord[1], coord[0]);
                    if (first) { gc.moveTo(pt.getX(), pt.getY()); first = false; }
                    else gc.lineTo(pt.getX(), pt.getY());
                }
                gc.stroke();

                // Main traffic line (thinner, more opaque) — sits on top
                gc.setStroke(color.deriveColor(0, 1, 1, 0.55 + seg.severity * 0.35));
                gc.setLineWidth(3 + seg.severity * 2);
                gc.beginPath();
                first = true;
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
            // Pickup: Blue, Delivery: Yellow
            Color color = isPickup ? Color.web("#00a8ff") : Color.web("#f1c40f");

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

            // Destination dot (White)
            double[] last = route.coordinates.get(route.coordinates.size() - 1);
            Point2D destPt = geoToScreen(last[1], last[0]);
            gc.setFill(Color.WHITE);
            gc.setStroke(color); // Subtle stroke matching route color
            gc.setLineWidth(1.5);
            gc.fillOval(destPt.getX() - 4, destPt.getY() - 4, 8, 8);
            gc.strokeOval(destPt.getX() - 4, destPt.getY() - 4, 8, 8);
        }
    }

    private void drawOrders(GraphicsContext gc) {
        synchronized (orderPoints) {
            for (OrderPoint op : orderPoints) {
                boolean isAssignedToSelected = (selectedDriverId != null && selectedDriverId.equals(op.assignedDriverId));

                // Draw dropoff point (Orange) ONLY if it belongs to selected driver
                if (isAssignedToSelected) {
                    Point2D dropoffPt = geoToScreen(op.dropoffLat, op.dropoffLng);
                    gc.setFill(Color.web("#e67e22")); // Orange
                    gc.fillOval(dropoffPt.getX() - 4, dropoffPt.getY() - 4, 8, 8);
                    
                    // Draw a faint line connecting pickup/current to dropoff
                }

                // Draw pickup point
                if (!op.isPickedUp) {
                    Point2D pickupPt = geoToScreen(op.pickupLat, op.pickupLng);
                    if (isAssignedToSelected) {
                        gc.setFill(Color.web("#9b59b6")); // Purple
                        gc.fillOval(pickupPt.getX() - 5, pickupPt.getY() - 5, 10, 10);
                        // Add glow for emphasis
                        gc.setStroke(Color.web("#9b59b6", 0.4));
                        gc.setLineWidth(4);
                        gc.strokeOval(pickupPt.getX() - 5, pickupPt.getY() - 5, 10, 10);
                    } else if (op.assignedDriverId == null || !op.assignedDriverId.equals(selectedDriverId)) {
                        gc.setFill(Color.RED);
                        gc.fillOval(pickupPt.getX() - 3, pickupPt.getY() - 3, 6, 6);
                    }
                }
            }
        }
    }

    private void drawDrivers(GraphicsContext gc) {
        for (DriverState ds : drivers.values()) {
            Point2D pt = geoToScreen(ds.curLat, ds.curLng);
            Color color;
            double size;
            
            // Map state to colors
            if ("ONLINE_IDLE".equals(ds.state)) {
                color = Color.web("#00ffab"); // Green
                size = 8;
            } else if ("PICKUP_EN_ROUTE".equals(ds.state)) {
                color = Color.web("#00a8ff"); // Blue
                size = 12;
            } else if ("DELIVERING".equals(ds.state)) {
                color = Color.web("#f1c40f"); // Yellow
                size = 12;
            } else {
                color = Color.GRAY;
                size = 8;
            }

            // Triangle coordinates (pointing UP)
            double[] xPoints = {pt.getX(), pt.getX() - size * 0.866, pt.getX() + size * 0.866};
            double[] yPoints = {pt.getY() - size, pt.getY() + size * 0.5, pt.getY() + size * 0.5};

            // Glow Triangle
            gc.setFill(color.deriveColor(0, 1, 1, 0.25));
            double glowSize = size + 4;
            double[] gxPoints = {pt.getX(), pt.getX() - glowSize * 0.866, pt.getX() + glowSize * 0.866};
            double[] gyPoints = {pt.getY() - glowSize, pt.getY() + glowSize * 0.5, pt.getY() + glowSize * 0.5};
            gc.fillPolygon(gxPoints, gyPoints, 3);

            // Core Triangle
            gc.setFill(color);
            gc.fillPolygon(xPoints, yPoints, 3);

            // Name label (only at higher zoom)
            if (zoom >= 14) {
                gc.setFill(Color.web("#99f7ff", 0.8));
                gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 9));
                gc.fillText(ds.name != null ? ds.name : ds.id, pt.getX() + size + 2, pt.getY() - size);
            }
        }
    }

    /**
     * Draw thin lines from each busy driver to their destination.
     * If an OSRM route exists, skip (the route polyline handles it).
     */
    private void drawDriverDestLines(GraphicsContext gc) {
        for (DriverState ds : drivers.values()) {
            if (!ds.hasDest) continue;
            // Skip if OSRM route already exists for this driver
            if (routes.containsKey(ds.id)) continue;

            boolean isPickup = "PICKUP_EN_ROUTE".equals(ds.state);
            Color color = isPickup ? Color.web("#00a8ff", 0.5) : Color.web("#f1c40f", 0.5);

            Point2D from = geoToScreen(ds.curLat, ds.curLng);
            Point2D to = geoToScreen(ds.destLat, ds.destLng);

            gc.setStroke(color);
            gc.setLineWidth(1.5);
            gc.setLineDashes(6, 4);
            gc.beginPath();
            gc.moveTo(from.getX(), from.getY());
            gc.lineTo(to.getX(), to.getY());
            gc.stroke();
            gc.setLineDashes(null);

            // Small destination marker
            gc.setFill(color.deriveColor(0, 1, 1, 0.8));
            gc.fillOval(to.getX() - 3, to.getY() - 3, 6, 6);
        }
    }

    /**
     * Draw map legend at bottom-right corner.
     */
    private void drawLegend(GraphicsContext gc, double w, double h) {
        double legendW = 180;
        double legendH = 175;
        double padR = 16;
        double padB = 16;
        double x = w - legendW - padR;
        double y = h - legendH - padB;
        double rowH = 20;

        // Background
        gc.setFill(Color.web("#1a1c1e", 0.85));
        gc.fillRoundRect(x, y, legendW, legendH, 10, 10);
        gc.setStroke(Color.web("#444", 0.6));
        gc.setLineWidth(1);
        gc.setLineDashes(null);
        gc.strokeRoundRect(x, y, legendW, legendH, 10, 10);

        // Title
        gc.setFill(Color.WHITE);
        gc.setFont(Font.font("Segoe UI", FontWeight.BOLD, 11));
        gc.fillText("CHÚ THÍCH", x + 12, y + 18);

        gc.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 10));
        double itemY = y + 36;

        // Driver — Idle (green triangle)
        drawLegendTriangle(gc, x + 18, itemY, Color.web("#00ffab"));
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Tài xế rảnh", x + 34, itemY + 4);
        itemY += rowH;

        // Driver — Pickup (blue triangle)
        drawLegendTriangle(gc, x + 18, itemY, Color.web("#00a8ff"));
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Đang đi lấy hàng", x + 34, itemY + 4);
        itemY += rowH;

        // Driver — Delivering (yellow triangle)
        drawLegendTriangle(gc, x + 18, itemY, Color.web("#f1c40f"));
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Đang giao hàng", x + 34, itemY + 4);
        itemY += rowH;

        // Route — Pickup (blue dashed)
        gc.setStroke(Color.web("#00a8ff"));
        gc.setLineWidth(2);
        gc.setLineDashes(5, 3);
        gc.strokeLine(x + 10, itemY, x + 26, itemY);
        gc.setLineDashes(null);
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Route lấy hàng", x + 34, itemY + 4);
        itemY += rowH;

        // Route — Delivery (yellow solid)
        gc.setStroke(Color.web("#f1c40f"));
        gc.setLineWidth(2);
        gc.strokeLine(x + 10, itemY, x + 26, itemY);
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Route giao hàng", x + 34, itemY + 4);
        itemY += rowH;

        // Order (red dot)
        gc.setFill(Color.RED);
        gc.fillOval(x + 14, itemY - 4, 8, 8);
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Đơn hàng chờ", x + 34, itemY + 4);
        itemY += rowH;

        // Traffic (red/orange line)
        gc.setStroke(Color.web("#d7383b"));
        gc.setLineWidth(3);
        gc.strokeLine(x + 10, itemY, x + 18, itemY);
        gc.setStroke(Color.web("#ff9966"));
        gc.strokeLine(x + 18, itemY, x + 26, itemY);
        gc.setFill(Color.web("#ccc"));
        gc.fillText("Giao thông", x + 34, itemY + 4);
    }

    private void drawLegendTriangle(GraphicsContext gc, double cx, double cy, Color color) {
        double s = 5;
        double[] xs = {cx, cx - s * 0.866, cx + s * 0.866};
        double[] ys = {cy - s, cy + s * 0.5, cy + s * 0.5};
        gc.setFill(color);
        gc.fillPolygon(xs, ys, 3);
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
        // Left click: select driver
        if (e.getButton() == javafx.scene.input.MouseButton.PRIMARY
                && e.getClickCount() == 1) {
            Point2D click = new Point2D(e.getX(), e.getY());
            boolean driverClicked = false;
            for (DriverState ds : drivers.values()) {
                Point2D pt = geoToScreen(ds.curLat, ds.curLng);
                if (click.distance(pt) < 15) {
                    selectedDriverId = ds.id;
                    if (onDriverSelected != null) onDriverSelected.accept(ds.id);
                    needsRepaint = true;
                    driverClicked = true;
                    break;
                }
            }
            if (!driverClicked) {
                // Clicked on empty space, clear selection
                selectedDriverId = null;
                needsRepaint = true;
            }
            if (driverClicked) return;
        }
        // Right click: fire geo callback for editor
        if (e.getButton() == javafx.scene.input.MouseButton.SECONDARY && onMapClickedGeo != null) {
            double[] geo = screenToGeo(e.getX(), e.getY());
            onMapClickedGeo.accept(geo[0], geo[1]);
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

    /**
     * Convert screen pixel position to geographic coordinates (inverse of geoToScreen).
     */
    public double[] screenToGeo(double screenX, double screenY) {
        int z = (int) Math.round(zoom);
        double scale = Math.pow(2, zoom - z);

        double centerPxX = lngToTileX(centerLng, z) * TILE_SIZE;
        double centerPxY = latToTileY(centerLat, z) * TILE_SIZE;

        double pointPxX = (screenX - getWidth() / 2.0) / scale + centerPxX;
        double pointPxY = (screenY - getHeight() / 2.0) / scale + centerPxY;

        // Inverse of lngToTileX
        double lng = pointPxX / (1 << z) / TILE_SIZE * 360.0 - 180.0;

        // Inverse of latToTileY (Mercator)
        double n = Math.PI - 2 * Math.PI * pointPxY / (1 << z) / TILE_SIZE;
        double lat = Math.toDegrees(Math.atan(Math.sinh(n)));

        return new double[]{lat, lng};
    }

    // ── Map click callback (for editor mode) ────────────────────────────
    private java.util.function.BiConsumer<Double, Double> onMapClickedGeo;

    public void setOnMapClickedGeo(java.util.function.BiConsumer<Double, Double> callback) {
        this.onMapClickedGeo = callback;
    }
}
