package com.routechain.ai;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Grid-based spatial index for fast proximity queries on pending orders.
 *
 * Divides the HCMC bounding box into a GRID_SIZE × GRID_SIZE grid
 * and indexes each order's pickup point into its cell.
 *
 * Complexity:
 *   - rebuild(): O(N) where N = number of orders
 *   - findNearby(): O(k) where k = orders in scanned cells (typically << N)
 *
 * Rebuilt once per tick before driver context construction.
 */
public class NearbyOrderIndexer {

    private static final int GRID_SIZE = 20;

    // HCMC bounding box (must match SpatiotemporalField)
    private static final double MIN_LAT = 10.68;
    private static final double MAX_LAT = 10.87;
    private static final double MIN_LNG = 106.60;
    private static final double MAX_LNG = 106.80;

    private static final double LAT_SPAN = MAX_LAT - MIN_LAT;
    private static final double LNG_SPAN = MAX_LNG - MIN_LNG;
    private static final double CELL_LAT = LAT_SPAN / GRID_SIZE;
    private static final double CELL_LNG = LNG_SPAN / GRID_SIZE;

    /** Approximate meters per degree latitude at HCMC latitude. */
    private static final double METERS_PER_DEG_LAT = 111_320.0;
    private static final double METERS_PER_DEG_LNG =
            111_320.0 * Math.cos(Math.toRadians((MIN_LAT + MAX_LAT) / 2.0));

    private final Map<Long, List<Order>> grid = new HashMap<>();

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Rebuild the spatial index from scratch.
     * Call once at the start of each dispatch tick.
     *
     * @param pendingOrders only orders in PENDING_ASSIGNMENT status
     */
    public void rebuild(List<Order> pendingOrders) {
        grid.clear();
        for (Order order : pendingOrders) {
            long key = cellKey(order.getPickupPoint());
            if (key >= 0) {
                grid.computeIfAbsent(key, k -> new ArrayList<>()).add(order);
            }
        }
    }

    /**
     * Find all orders whose pickup point is within {@code radiusKm} of {@code center}.
     *
     * @param center   query origin (typically driver location)
     * @param radiusKm search radius in kilometers
     * @return list of orders within radius (may be empty, never null)
     */
    public List<Order> findNearby(GeoPoint center, double radiusKm) {
        double radiusMeters = radiusKm * 1000.0;
        List<Order> result = new ArrayList<>();

        // Determine bounding cells to scan
        double latMargin = radiusMeters / METERS_PER_DEG_LAT;
        double lngMargin = radiusMeters / METERS_PER_DEG_LNG;

        int rowMin = latToRow(center.lat() - latMargin);
        int rowMax = latToRow(center.lat() + latMargin);
        int colMin = lngToCol(center.lng() - lngMargin);
        int colMax = lngToCol(center.lng() + lngMargin);

        // Clamp to grid bounds
        rowMin = Math.max(0, rowMin);
        rowMax = Math.min(GRID_SIZE - 1, rowMax);
        colMin = Math.max(0, colMin);
        colMax = Math.min(GRID_SIZE - 1, colMax);

        for (int r = rowMin; r <= rowMax; r++) {
            for (int c = colMin; c <= colMax; c++) {
                List<Order> cell = grid.get(cellKey(r, c));
                if (cell == null) continue;
                for (Order order : cell) {
                    if (center.distanceTo(order.getPickupPoint()) <= radiusMeters) {
                        result.add(order);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Find orders reachable from {@code center} within {@code maxMinutes}
     * at the given estimated speed.
     *
     * @param center     query origin
     * @param speedKmh   estimated travel speed (km/h), adjusted for traffic/weather
     * @param maxMinutes maximum travel time in minutes
     * @return orders whose pickup is reachable within the time budget
     */
    public List<Order> findReachable(GeoPoint center, double speedKmh,
                                      double maxMinutes) {
        double radiusKm = speedKmh * (maxMinutes / 60.0);
        return findNearby(center, radiusKm);
    }

    /**
     * Return the number of indexed orders (for diagnostics).
     */
    public int size() {
        return grid.values().stream().mapToInt(List::size).sum();
    }

    // ── Grid helpers ────────────────────────────────────────────────────

    private long cellKey(GeoPoint p) {
        int r = latToRow(p.lat());
        int c = lngToCol(p.lng());
        if (r < 0 || r >= GRID_SIZE || c < 0 || c >= GRID_SIZE) {
            return -1; // outside bounding box
        }
        return cellKey(r, c);
    }

    private static long cellKey(int row, int col) {
        return (long) row * GRID_SIZE + col;
    }

    private int latToRow(double lat) {
        return (int) ((lat - MIN_LAT) / CELL_LAT);
    }

    private int lngToCol(double lng) {
        return (int) ((lng - MIN_LNG) / CELL_LNG);
    }
}
