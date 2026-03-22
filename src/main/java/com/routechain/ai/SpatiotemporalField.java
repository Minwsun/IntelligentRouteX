package com.routechain.ai;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.HcmcCityData;

import java.util.List;

/**
 * Spatiotemporal Demand Field — the "city brain".
 * 12×12 grid over HCMC bounding box with 4 continuously evolving layers:
 *
 * 1. demandIntensity — orders per cell per 5-min window
 * 2. spikeProbability — probability of demand spike in next 5min
 * 3. shortagePressure — demand-supply gap (normalized)
 * 4. attractionScore — combined opportunity metric for driver positioning
 *
 * Each tick:
 * - Cells are updated from real-time order/driver data
 * - Spatial diffusion propagates influence to neighbors
 * - Temporal decay prevents stale signals
 */
public class SpatiotemporalField {

    public static final int ROWS = 12;
    public static final int COLS = 12;
    private static final double DIFFUSION_RATE = 0.12;
    private static final double TEMPORAL_DECAY = 0.92;

    // ── HCMC bounding box ───────────────────────────────────────────────
    private static final double MIN_LAT = 10.68;
    private static final double MAX_LAT = 10.87;
    private static final double MIN_LNG = 106.60;
    private static final double MAX_LNG = 106.80;

    // ── 4 field layers ──────────────────────────────────────────────────
    private final double[][] demandIntensity  = new double[ROWS][COLS];
    private final double[][] spikeProbability = new double[ROWS][COLS];
    private final double[][] shortagePressure = new double[ROWS][COLS];
    private final double[][] attractionScore  = new double[ROWS][COLS];

    // ── Auxiliary state ─────────────────────────────────────────────────
    private final double[][] driverDensity    = new double[ROWS][COLS];
    private final int[][] orderCount          = new int[ROWS][COLS];
    private final int[][] driverCount         = new int[ROWS][COLS];

    // ── Previous demand for trend detection ─────────────────────────────
    private final double[][] prevDemand       = new double[ROWS][COLS];

    // ── Update ──────────────────────────────────────────────────────────

    /**
     * Recompute all 4 field layers from current simulation state.
     * Call once per tick.
     */
    public void update(List<Order> allOrders, List<Driver> drivers,
                       int simulatedHour, double trafficIntensity,
                       WeatherProfile weather) {

        // 1. Reset counts
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                orderCount[r][c] = 0;
                driverCount[r][c] = 0;
            }
        }

        // 2. Count orders per cell (pending and active)
        for (Order order : allOrders) {
            int[] cell = cellOf(order.getPickupPoint());
            if (cell != null) {
                orderCount[cell[0]][cell[1]]++;
            }
        }

        // 3. Count drivers per cell
        for (Driver driver : drivers) {
            if (driver.getState() == DriverState.OFFLINE) continue;
            int[] cell = cellOf(driver.getCurrentLocation());
            if (cell != null) {
                driverCount[cell[0]][cell[1]]++;
            }
        }

        // 4. Compute layers
        double hourlyMult = HcmcCityData.hourlyMultiplier(simulatedHour);
        double weatherMult = switch (weather) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 1.15;
            case HEAVY_RAIN -> 1.35;
            case STORM -> 1.6;
        };

        double cellAreaKm2 = cellAreaKm2();

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                // Save previous demand for trend
                prevDemand[r][c] = demandIntensity[r][c];

                // Demand intensity: decay old + add new
                double rawDemand = orderCount[r][c] * hourlyMult * weatherMult;
                demandIntensity[r][c] = demandIntensity[r][c] * TEMPORAL_DECAY
                        + rawDemand * (1 - TEMPORAL_DECAY);

                // Driver density: drivers per km²
                driverDensity[r][c] = driverCount[r][c] / Math.max(0.1, cellAreaKm2);

                // Shortage pressure: normalized gap
                double supply = driverCount[r][c];
                double demand = demandIntensity[r][c];
                shortagePressure[r][c] = demand > 0.01
                        ? Math.max(0, Math.min(1.0, (demand - supply) / Math.max(demand, 1)))
                        : 0;

                // Spike probability: demand growing fast
                double trend = demandIntensity[r][c] - prevDemand[r][c];
                double spike = 0;
                if (trend > 0.5) spike += 0.35;
                if (demandIntensity[r][c] > 3.0) spike += 0.25;
                if (shortagePressure[r][c] > 0.5) spike += 0.20;
                if (weather == WeatherProfile.STORM) spike += 0.15;
                if (trafficIntensity > 0.7) spike += 0.05;
                spikeProbability[r][c] = Math.min(1.0, spike);

                // Attraction score: combined opportunity
                attractionScore[r][c] =
                        0.30 * Math.min(1.0, demandIntensity[r][c] / 5.0)
                      + 0.25 * spikeProbability[r][c]
                      + 0.20 * shortagePressure[r][c]
                      + 0.15 * (1.0 - Math.min(1.0, driverDensity[r][c] / 5.0))
                      + 0.10 * Math.min(1.0, hourlyMult / 2.0);
            }
        }

        // 5. Spatial diffusion
        diffuse(demandIntensity);
        diffuse(spikeProbability);
        diffuse(attractionScore);
    }

    // ── Spatial diffusion ───────────────────────────────────────────────

    /**
     * Apply 3×3 kernel averaging to propagate field values to neighbors.
     */
    private void diffuse(double[][] field) {
        double[][] buffer = new double[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                double sum = 0;
                int count = 0;
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = r + dr, nc = c + dc;
                        if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS) {
                            sum += field[nr][nc];
                            count++;
                        }
                    }
                }
                buffer[r][c] = field[r][c] * (1 - DIFFUSION_RATE)
                        + (sum / count) * DIFFUSION_RATE;
            }
        }
        // Copy back
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(buffer[r], 0, field[r], 0, COLS);
        }
    }

    // ── Point queries ───────────────────────────────────────────────────

    /** Get demand intensity at a geographic point. */
    public double getDemandAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? demandIntensity[cell[0]][cell[1]] : 0;
    }

    /** Get spike probability at a geographic point. */
    public double getSpikeAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? spikeProbability[cell[0]][cell[1]] : 0;
    }

    /** Get shortage pressure at a geographic point. */
    public double getShortageAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? shortagePressure[cell[0]][cell[1]] : 0;
    }

    /** Get attraction score at a geographic point. */
    public double getAttractionAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? attractionScore[cell[0]][cell[1]] : 0;
    }

    /** Get driver density at a geographic point. */
    public double getDriverDensityAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? driverDensity[cell[0]][cell[1]] : 0;
    }

    // ── Grid utilities ──────────────────────────────────────────────────

    /**
     * Map a geographic point to grid [row, col].
     * Returns null if point is outside bounding box.
     */
    public int[] cellOf(GeoPoint p) {
        if (p.lat() < MIN_LAT || p.lat() > MAX_LAT
                || p.lng() < MIN_LNG || p.lng() > MAX_LNG) {
            return null;
        }
        int r = (int) ((p.lat() - MIN_LAT) / (MAX_LAT - MIN_LAT) * ROWS);
        int c = (int) ((p.lng() - MIN_LNG) / (MAX_LNG - MIN_LNG) * COLS);
        r = Math.min(r, ROWS - 1);
        c = Math.min(c, COLS - 1);
        return new int[] { r, c };
    }

    /**
     * Get center GeoPoint of a cell.
     */
    public GeoPoint cellCenter(int row, int col) {
        double lat = MIN_LAT + (row + 0.5) * (MAX_LAT - MIN_LAT) / ROWS;
        double lng = MIN_LNG + (col + 0.5) * (MAX_LNG - MIN_LNG) / COLS;
        return new GeoPoint(lat, lng);
    }

    private double cellAreaKm2() {
        double cellLatDeg = (MAX_LAT - MIN_LAT) / ROWS;
        double cellLngDeg = (MAX_LNG - MIN_LNG) / COLS;
        double latKm = cellLatDeg * 111.32;
        double lngKm = cellLngDeg * 111.32 * Math.cos(Math.toRadians((MIN_LAT + MAX_LAT) / 2));
        return latKm * lngKm;
    }

    // ── Snapshots for UI visualization ──────────────────────────────────

    /**
     * Get a snapshot of a field layer for rendering as heatmap.
     */
    public double[][] getFieldSnapshot(String layer) {
        double[][] snapshot = new double[ROWS][COLS];
        double[][] source = switch (layer) {
            case "demand" -> demandIntensity;
            case "spike" -> spikeProbability;
            case "shortage" -> shortagePressure;
            case "attraction" -> attractionScore;
            case "driverDensity" -> driverDensity;
            default -> demandIntensity;
        };
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(source[r], 0, snapshot[r], 0, COLS);
        }
        return snapshot;
    }

    /** Get bounding box for UI rendering. */
    public double[] getBounds() {
        return new double[] { MIN_LAT, MAX_LAT, MIN_LNG, MAX_LNG };
    }

    /** Reset all fields to zero. */
    public void reset() {
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                demandIntensity[r][c] = 0;
                spikeProbability[r][c] = 0;
                shortagePressure[r][c] = 0;
                attractionScore[r][c] = 0;
                driverDensity[r][c] = 0;
                prevDemand[r][c] = 0;
            }
        }
    }
}
