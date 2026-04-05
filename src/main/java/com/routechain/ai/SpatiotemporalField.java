package com.routechain.ai;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.CellValueSnapshot;
import com.routechain.simulation.HcmcCityData;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

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
    private static final int H3_RESOLUTION = 8;
    private static final double DIFFUSION_RATE = 0.12;
    private static final double TEMPORAL_DECAY = 0.92;
    private static final H3Support H3_SUPPORT = H3Support.tryCreate(H3_RESOLUTION);

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
    private final double[][] weatherExposure  = new double[ROWS][COLS];
    private final double[][] congestionExposure = new double[ROWS][COLS];
    private final double[][] committedPickupPressure = new double[ROWS][COLS];

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
                committedPickupPressure[r][c] = committedPickupPressure[r][c] * TEMPORAL_DECAY;
            }
        }

        // 2. Count open pickup demand separately from already-claimed pickup pressure.
        for (Order order : allOrders) {
            int[] cell = cellOf(order.getPickupPoint());
            if (cell == null) {
                continue;
            }
            if (contributesOpenPickupDemand(order)) {
                orderCount[cell[0]][cell[1]]++;
            }
            if (contributesCommittedPickupPressure(order)) {
                committedPickupPressure[cell[0]][cell[1]] =
                        committedPickupPressure[cell[0]][cell[1]] * TEMPORAL_DECAY + (1 - TEMPORAL_DECAY);
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
        double weatherSeverity = switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.25;
            case HEAVY_RAIN -> 0.65;
            case STORM -> 1.0;
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

                congestionExposure[r][c] = Math.max(0, Math.min(1.0,
                        trafficIntensity * 0.55
                                + Math.min(1.0, demandIntensity[r][c] / 5.0) * 0.20
                                + shortagePressure[r][c] * 0.15
                                + Math.min(1.0, driverDensity[r][c] / 5.0) * 0.10
                                + committedPickupPressure[r][c] * 0.18));

                weatherExposure[r][c] = Math.max(0, Math.min(1.0,
                        weatherSeverity * (0.80 + shortagePressure[r][c] * 0.20)));

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
        diffuse(weatherExposure);
        diffuse(congestionExposure);
    }

    // ── Spatial diffusion ───────────────────────────────────────────────

    /**
     * Apply 3×3 kernel averaging to propagate field values to neighbors.
     */
    private boolean contributesOpenPickupDemand(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        return switch (order.getStatus()) {
            case CONFIRMED, PENDING_ASSIGNMENT -> true;
            default -> false;
        };
    }

    private boolean contributesCommittedPickupPressure(Order order) {
        if (order == null || order.getStatus() == null) {
            return false;
        }
        return switch (order.getStatus()) {
            case ASSIGNED, PICKUP_EN_ROUTE -> true;
            default -> false;
        };
    }

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

    /** Get committed pickup pressure at a geographic point. */
    public double getCommittedPickupPressureAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? committedPickupPressure[cell[0]][cell[1]] : 0;
    }

    /** Get weather risk exposure at a geographic point. */
    public double getWeatherExposureAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? weatherExposure[cell[0]][cell[1]] : 0;
    }

    /** Get corridor / congestion exposure at a geographic point. */
    public double getCongestionExposureAt(GeoPoint p) {
        int[] cell = cellOf(p);
        return cell != null ? congestionExposure[cell[0]][cell[1]] : 0;
    }

    /** Risk-adjusted attraction, used for smart repositioning under adverse conditions. */
    public double getRiskAdjustedAttractionAt(GeoPoint p) {
        int[] cell = cellOf(p);
        if (cell == null) return 0;

        int r = cell[0];
        int c = cell[1];
        double raw = attractionScore[r][c];
        double penalty = weatherExposure[r][c] * 0.18 + congestionExposure[r][c] * 0.22;
        double shortageLift = shortagePressure[r][c] * 0.08;
        return Math.max(0.0, raw - penalty + shortageLift);
    }

    /**
     * Lightweight near-future demand forecast from the current field state.
     * This is intentionally cheap enough for online use in dispatch/repositioning.
     */
    public double getForecastDemandAt(GeoPoint p, int horizonMinutes) {
        int[] cell = cellOf(p);
        if (cell == null) return 0;

        int r = cell[0];
        int c = cell[1];
        double currentDemand = demandIntensity[r][c];
        double spike = spikeProbability[r][c];
        double shortage = shortagePressure[r][c];
        double densityPenalty = Math.min(0.6, driverDensity[r][c] / 8.0);

        double horizonFactor = Math.max(0.4, horizonMinutes / 10.0);
        double spikeLift = spike * (0.45 + 0.08 * horizonFactor);
        double shortageLift = shortage * (0.20 + 0.04 * horizonFactor);
        double persistence = currentDemand * (0.80 + 0.03 * horizonFactor);

        return Math.max(0.0, persistence + spikeLift + shortageLift - densityPenalty);
    }

    /** Forecasted congestion/traffic pressure at a point for a near-future horizon. */
    public double getTrafficForecastAt(GeoPoint p, int horizonMinutes) {
        int[] cell = cellOf(p);
        if (cell == null) return 0;

        int r = cell[0];
        int c = cell[1];
        double currentCongestion = congestionExposure[r][c];
        double futureDemand = Math.min(1.0, getForecastDemandAt(p, horizonMinutes) / 4.5);
        double futureShortage = getShortageForecastAt(p, horizonMinutes);
        double futureWeather = getWeatherForecastAt(p, horizonMinutes);
        double horizonFactor = Math.max(0.4, horizonMinutes / 10.0);

        return clamp01(
                currentCongestion * 0.64
                        + futureDemand * 0.18
                        + futureShortage * 0.10
                        + futureWeather * 0.08
                        + Math.min(0.10, horizonFactor * 0.05));
    }

    /** Forecasted weather exposure for short-horizon dispatch decisions. */
    public double getWeatherForecastAt(GeoPoint p, int horizonMinutes) {
        int[] cell = cellOf(p);
        if (cell == null) return 0;

        int r = cell[0];
        int c = cell[1];
        double currentWeather = weatherExposure[r][c];
        double spike = spikeProbability[r][c];
        double shortage = shortagePressure[r][c];
        double horizonFactor = Math.max(0.5, horizonMinutes / 10.0);

        return clamp01(
                currentWeather * (0.78 + 0.04 * horizonFactor)
                        + spike * 0.08
                        + shortage * 0.06);
    }

    /** Forecasted shortage pressure, useful for reserve shaping and post-drop landing. */
    public double getShortageForecastAt(GeoPoint p, int horizonMinutes) {
        int[] cell = cellOf(p);
        if (cell == null) return 0;

        int r = cell[0];
        int c = cell[1];
        double currentShortage = shortagePressure[r][c];
        double futureDemand = Math.min(1.0, getForecastDemandAt(p, horizonMinutes) / 4.0);
        double supplyPenalty = Math.min(0.55, driverDensity[r][c] / 8.5);

        return clamp01(currentShortage * 0.62 + futureDemand * 0.30 - supplyPenalty * 0.16 + 0.10);
    }

    /**
     * Post-drop opportunity combines demand, shortage, and low-risk landing quality.
     * Higher means better chance a driver gets the next order soon after the final drop.
     */
    public double getPostDropOpportunityAt(GeoPoint p, int horizonMinutes) {
        double demand = Math.min(1.0, getForecastDemandAt(p, horizonMinutes) / 4.0);
        double shortage = getShortageForecastAt(p, horizonMinutes);
        double attraction = clamp01(getRiskAdjustedAttractionAt(p));
        double traffic = getTrafficForecastAt(p, horizonMinutes);
        double weather = getWeatherForecastAt(p, horizonMinutes);
        double density = Math.min(1.0, getDriverDensityAt(p) / 6.0);

        return clamp01(
                demand * 0.34
                        + shortage * 0.22
                        + attraction * 0.24
                        + (1.0 - traffic) * 0.10
                        + (1.0 - weather) * 0.06
                        + (1.0 - density) * 0.04);
    }

    /** Risk that a driver landing here will idle and run empty after completion. */
    public double getEmptyZoneRiskAt(GeoPoint p, int horizonMinutes) {
        double postDropOpportunity = getPostDropOpportunityAt(p, horizonMinutes);
        double traffic = getTrafficForecastAt(p, horizonMinutes);
        double weather = getWeatherForecastAt(p, horizonMinutes);
        double density = Math.min(1.0, getDriverDensityAt(p) / 6.0);

        return clamp01(
                (1.0 - postDropOpportunity) * 0.58
                        + traffic * 0.18
                        + weather * 0.14
                        + density * 0.10);
    }

    /** Expected merchant prep burden in minutes for this pickup area and horizon. */
    public double getMerchantPrepForecastMinutesAt(GeoPoint p, int horizonMinutes) {
        double demand = Math.min(1.0, getForecastDemandAt(p, horizonMinutes) / 4.5);
        double traffic = getTrafficForecastAt(p, horizonMinutes);
        double weather = getWeatherForecastAt(p, horizonMinutes);
        double shortage = getShortageForecastAt(p, horizonMinutes);
        double committedPressure = Math.min(1.0, getCommittedPickupPressureAt(p));
        double horizonFactor = Math.max(0.5, horizonMinutes / 10.0);
        double minutes = 3.5
                + demand * 3.2
                + traffic * 2.0
                + weather * 2.8
                + shortage * 1.4
                + committedPressure * 1.8
                + horizonFactor * 0.4;
        return Math.max(2.0, Math.min(18.0, minutes));
    }

    /** Probability that borrowed coverage remains useful for this zone in the near future. */
    public double getBorrowSuccessProbabilityAt(GeoPoint p, int horizonMinutes) {
        double shortage = getShortageForecastAt(p, horizonMinutes);
        double density = Math.min(1.0, getDriverDensityAt(p) / 6.0);
        double traffic = getTrafficForecastAt(p, horizonMinutes);
        double weather = getWeatherForecastAt(p, horizonMinutes);
        double postDropOpportunity = getPostDropOpportunityAt(p, horizonMinutes);
        return clamp01(
                0.48
                        + postDropOpportunity * 0.24
                        + shortage * 0.16
                        + (1.0 - density) * 0.10
                        - traffic * 0.10
                        - weather * 0.08);
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

    public String cellKeyOf(GeoPoint p) {
        String h3Cell = H3_SUPPORT.cellId(p);
        if (h3Cell != null) {
            return h3Cell;
        }
        int[] cell = cellOf(p);
        if (cell == null) {
            return "GRID-outside";
        }
        return "GRID-" + cell[0] + "-" + cell[1];
    }

    public List<CellValueSnapshot> topCellSnapshots(String serviceTier, int limit) {
        return topCellSnapshots(serviceTier, 10, limit);
    }

    public List<CellValueSnapshot> topCellSnapshots(String serviceTier,
                                                    int focusHorizonMinutes,
                                                    int limit) {
        List<CellValueSnapshot> snapshots = new ArrayList<>(ROWS * COLS);
        double tierBias = serviceTierBias(serviceTier);
        for (int row = 0; row < ROWS; row++) {
            for (int col = 0; col < COLS; col++) {
                GeoPoint center = cellCenter(row, col);
                double demand5 = getForecastDemandAt(center, 5);
                double demand10 = getForecastDemandAt(center, 10);
                double demand15 = getForecastDemandAt(center, 15);
                double demand30 = getForecastDemandAt(center, 30);
                double shortage10 = getShortageForecastAt(center, 10);
                double traffic10 = getTrafficForecastAt(center, 10);
                double weather10 = getWeatherForecastAt(center, 10);
                double postDrop = getPostDropOpportunityAt(center, focusHorizonMinutes);
                double emptyRisk = getEmptyZoneRiskAt(center, focusHorizonMinutes);
                double reserveTargetScore = clamp01(
                        Math.min(1.0, demand10 / 4.5) * 0.36
                                + shortage10 * 0.38
                                + postDrop * 0.26);
                double borrowPressure = clamp01(
                        emptyRisk * 0.38
                                + shortage10 * 0.34
                                + Math.max(0.0, Math.min(1.0, (demand15 - demand5) / 4.0)) * 0.28);
                double compositeValue = clamp01(
                        postDrop * 0.34
                                + Math.min(1.0, demand10 / 4.5) * 0.22
                                + shortage10 * 0.16
                                + reserveTargetScore * 0.12
                                + (1.0 - traffic10) * 0.08
                                + (1.0 - weather10) * 0.04
                                + tierBias);
                String cellId = cellKeyOf(center);
                snapshots.add(new CellValueSnapshot(
                        cellId,
                        H3_SUPPORT.available() ? "H3-r" + H3_RESOLUTION : "GRID12x12-fallback",
                        serviceTier,
                        center.lat(),
                        center.lng(),
                        demandIntensity[row][col],
                        demand5,
                        demand10,
                        demand15,
                        demand30,
                        shortage10,
                        traffic10,
                        weather10,
                        postDrop,
                        emptyRisk,
                        reserveTargetScore,
                        borrowPressure,
                        compositeValue
                ));
            }
        }
        snapshots.sort((left, right) -> Double.compare(right.compositeValue(), left.compositeValue()));
        return snapshots.subList(0, Math.min(Math.max(limit, 0), snapshots.size()));
    }

    private static final class H3Support {
        private final Object core;
        private final Method latLngToCellAddress;
        private final Method geoToH3Address;
        private final int resolution;

        private H3Support(Object core,
                          Method latLngToCellAddress,
                          Method geoToH3Address,
                          int resolution) {
            this.core = core;
            this.latLngToCellAddress = latLngToCellAddress;
            this.geoToH3Address = geoToH3Address;
            this.resolution = resolution;
        }

        static H3Support tryCreate(int resolution) {
            try {
                Class<?> h3CoreClass = Class.forName("com.uber.h3core.H3Core");
                Object core = h3CoreClass.getMethod("newInstance").invoke(null);
                Method latLngMethod = null;
                Method geoToH3Method = null;
                try {
                    latLngMethod = h3CoreClass.getMethod("latLngToCellAddress", double.class, double.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Try legacy API name below.
                }
                try {
                    geoToH3Method = h3CoreClass.getMethod("geoToH3Address", double.class, double.class, int.class);
                } catch (NoSuchMethodException ignored) {
                    // Legacy API missing too.
                }
                if (latLngMethod == null && geoToH3Method == null) {
                    return new H3Support(null, null, null, resolution);
                }
                return new H3Support(core, latLngMethod, geoToH3Method, resolution);
            } catch (Throwable ignored) {
                return new H3Support(null, null, null, resolution);
            }
        }

        boolean available() {
            return core != null && (latLngToCellAddress != null || geoToH3Address != null);
        }

        String cellId(GeoPoint point) {
            if (!available() || point == null) {
                return null;
            }
            try {
                if (latLngToCellAddress != null) {
                    return (String) latLngToCellAddress.invoke(core, point.lat(), point.lng(), resolution);
                }
                return (String) geoToH3Address.invoke(core, point.lat(), point.lng(), resolution);
            } catch (Throwable ignored) {
                return null;
            }
        }
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
            case "committedPickupPressure" -> committedPickupPressure;
            case "weatherExposure" -> weatherExposure;
            case "congestionExposure" -> congestionExposure;
            default -> demandIntensity;
        };
        for (int r = 0; r < ROWS; r++) {
            System.arraycopy(source[r], 0, snapshot[r], 0, COLS);
        }
        return snapshot;
    }

    public double[][] getDerivedSnapshot(String layer, int horizonMinutes) {
        double[][] snapshot = new double[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                GeoPoint point = cellCenter(r, c);
                snapshot[r][c] = switch (layer) {
                    case "trafficForecast" -> getTrafficForecastAt(point, horizonMinutes);
                    case "weatherForecast" -> getWeatherForecastAt(point, horizonMinutes);
                    case "shortageForecast" -> getShortageForecastAt(point, horizonMinutes);
                    case "postDropOpportunity" -> getPostDropOpportunityAt(point, horizonMinutes);
                    case "emptyZoneRisk" -> getEmptyZoneRiskAt(point, horizonMinutes);
                    case "merchantPrepForecast" -> getMerchantPrepForecastMinutesAt(point, horizonMinutes);
                    case "borrowSuccessProbability" -> getBorrowSuccessProbabilityAt(point, horizonMinutes);
                    default -> 0.0;
                };
            }
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
                weatherExposure[r][c] = 0;
                congestionExposure[r][c] = 0;
                committedPickupPressure[r][c] = 0;
                prevDemand[r][c] = 0;
            }
        }
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double serviceTierBias(String serviceTier) {
        if (serviceTier == null) {
            return 0.0;
        }
        String normalized = serviceTier.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "instant" -> 0.02;
            case "2h" -> 0.04;
            case "4h", "scheduled" -> 0.06;
            case "multi_stop_cod" -> 0.03;
            default -> 0.0;
        };
    }
}
