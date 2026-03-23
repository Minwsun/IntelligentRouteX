package com.routechain.ai;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.DispatchPlan;

import java.util.List;

/**
 * Standardized feature engineering for all AI models.
 * Single source of truth for feature vector construction.
 *
 * Produces:
 * - Plan features (15D) — scoring, ranking, horizon evaluation
 * - End-state features (10D) — continuation value prediction
 * - Context features (8D) — policy selection
 * - ETA features (6D) — ETA prediction
 * - Risk features (7D) — late risk prediction
 * - Cancel features (5D) — cancellation risk prediction
 */
public class FeatureExtractor {

    // ── Plan features (15D) ─────────────────────────────────────────────

    /**
     * Extract 15-dimensional feature vector for a dispatch plan.
     */
    public double[] planFeatures(DispatchPlan plan, SpatiotemporalField field,
                                  double trafficIntensity, WeatherProfile weather) {
        double[] f = new double[15];
        List<Order> orders = plan.getOrders();
        Driver driver = plan.getDriver();

        // 0: bundleSize
        f[0] = orders.size() / 5.0;

        // 1: pickupSpreadKm — max pairwise distance between pickups
        f[1] = computePickupSpread(orders) / 5.0; // normalize to ~0-1

        // 2: dropoffDirectionCoherence
        f[2] = computeDropoffCoherence(orders);

        // 3: predictedETAMinutes (normalized)
        f[3] = plan.getPredictedTotalMinutes() / 60.0;

        // 4: lateRisk
        f[4] = plan.getLateRisk();

        // 5: cancelRisk
        f[5] = plan.getCancellationRisk();

        // 6: driverProfitNorm
        f[6] = Math.max(-1, Math.min(1, plan.getDriverProfit() / 50000.0));

        // 7: customerFeeNorm
        f[7] = plan.getCustomerFee() / 35000.0;

        // 8: deadheadKm
        f[8] = Math.min(1.0, plan.getPredictedDeadheadKm() / 6.0);

        // 9: continuationValue
        f[9] = plan.getEndZoneOpportunity();

        // 10: endZoneDemand10m
        GeoPoint endPt = plan.getEndZonePoint();
        f[10] = field != null ? Math.min(1.0, field.getForecastDemandAt(endPt, 10) / 5.0) : 0.3;

        // 11: endZoneDriverDensity
        f[11] = field != null ? Math.min(1.0, field.getDriverDensityAt(endPt) / 5.0) : 0.3;

        // 12: congestionExposure
        f[12] = field != null
                ? Math.min(1.0, Math.max(trafficIntensity, field.getCongestionExposureAt(endPt)))
                : Math.min(1.0, trafficIntensity);

        // 13: detourRatio
        double standaloneDist = computeStandaloneDistance(orders);
        double bundleDist = computeBundleRouteDistance(plan);
        f[13] = standaloneDist > 0
                ? Math.min(2.0, bundleDist / standaloneDist) / 2.0 : 0.5;

        // 14: acceptanceProbability
        f[14] = driver.getPredictedAcceptanceProb();

        return f;
    }

    // ── End-state features (10D) ────────────────────────────────────────

    /**
     * Extract 10-dimensional feature vector for end-state evaluation.
     */
    public double[] endStateFeatures(GeoPoint endPos, int endHour,
                                      SpatiotemporalField field,
                                      WeatherProfile weather, Driver driver) {
        double[] f = new double[10];

        f[0] = field != null ? Math.min(1.0, field.getDemandAt(endPos) / 3.0) : 0.3;
        f[1] = field != null ? Math.min(1.0, field.getForecastDemandAt(endPos, 10) / 5.0) : 0.3;
        f[2] = field != null ? field.getSpikeAt(endPos) : 0.1;
        f[3] = field != null ? Math.min(1.0, field.getDriverDensityAt(endPos) / 5.0) : 0.3;
        f[4] = field != null ? field.getShortageAt(endPos) : 0.3;
        f[5] = field != null ? field.getCongestionExposureAt(endPos) : 0.3;
        f[6] = endHour / 24.0;
        f[7] = weather.ordinal() / 3.0;
        f[8] = driver.getComputedUtilization();
        f[9] = Math.min(1.0, driver.getCompletedOrders() / 20.0);

        return f;
    }

    // ── Context features (8D) ───────────────────────────────────────────

    /**
     * Extract 8-dimensional context vector for policy selection.
     */
    public double[] contextFeatures(double traffic, WeatherProfile weather,
                                     int hour, double shortage, double avgWait,
                                     int pendingOrders, int availableDrivers,
                                     double surgeLevel) {
        double[] f = new double[8];

        f[0] = traffic;
        f[1] = weather.ordinal() / 3.0;
        f[2] = hour / 24.0;
        f[3] = Math.min(1.0, shortage);
        f[4] = Math.min(1.0, avgWait / 15.0);
        f[5] = Math.min(1.0, pendingOrders / 30.0);
        f[6] = Math.min(1.0, availableDrivers / 20.0);
        f[7] = Math.min(1.0, surgeLevel);

        return f;
    }

    // ── ETA features (6D) ───────────────────────────────────────────────

    /**
     * Extract features for ETA prediction model.
     */
    public double[] etaFeatures(double distanceKm, double traffic,
                                 WeatherProfile weather, int hour,
                                 int bundleSize, double congestion) {
        return new double[] {
            Math.min(1.0, distanceKm / 15.0),
            traffic,
            weather.ordinal() / 3.0,
            hour / 24.0,
            bundleSize / 5.0,
            Math.min(1.0, congestion)
        };
    }

    // ── Late risk features (7D) ─────────────────────────────────────────

    /**
     * Extract features for late risk prediction model.
     */
    public double[] lateRiskFeatures(double distanceKm, double traffic,
                                      WeatherProfile weather, int hour,
                                      int bundleSize, double slaSlackMinutes,
                                      double pickupDelay) {
        return new double[] {
            Math.min(1.0, distanceKm / 15.0),
            traffic,
            weather.ordinal() / 3.0,
            hour / 24.0,
            bundleSize / 5.0,
            Math.max(-1, Math.min(1, slaSlackMinutes / 30.0)),
            Math.min(1.0, pickupDelay / 10.0)
        };
    }

    // ── Cancel risk features (5D) ───────────────────────────────────────

    /**
     * Extract features for cancellation risk prediction model.
     */
    public double[] cancelFeatures(double waitTimeMinutes, double lateRisk,
                                    double fee, WeatherProfile weather,
                                    int bundleSize) {
        return new double[] {
            Math.min(1.0, waitTimeMinutes / 15.0),
            lateRisk,
            Math.min(1.0, fee / 50000.0),
            weather.ordinal() / 3.0,
            bundleSize / 5.0
        };
    }

    // ── Pickup delay features (4D) ──────────────────────────────────────

    /**
     * Extract features for pickup delay prediction.
     */
    public double[] pickupDelayFeatures(double traffic, WeatherProfile weather,
                                         double distanceKm, int hour) {
        return new double[] {
            traffic,
            weather.ordinal() / 3.0,
            Math.min(1.0, distanceKm / 10.0),
            hour / 24.0
        };
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double computePickupSpread(List<Order> orders) {
        if (orders.size() <= 1) return 0;
        double maxDist = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                double d = orders.get(i).getPickupPoint()
                        .distanceTo(orders.get(j).getPickupPoint()) / 1000.0;
                maxDist = Math.max(maxDist, d);
            }
        }
        return maxDist;
    }

    private double computeDropoffCoherence(List<Order> orders) {
        if (orders.size() <= 1) return 1.0;

        // Average cosine similarity of dropoff direction vectors from pickup centroid
        double cLat = 0, cLng = 0;
        for (Order o : orders) {
            cLat += o.getPickupPoint().lat();
            cLng += o.getPickupPoint().lng();
        }
        cLat /= orders.size();
        cLng /= orders.size();

        double totalCos = 0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            double dx1 = orders.get(i).getDropoffPoint().lng() - cLng;
            double dy1 = orders.get(i).getDropoffPoint().lat() - cLat;
            for (int j = i + 1; j < orders.size(); j++) {
                double dx2 = orders.get(j).getDropoffPoint().lng() - cLng;
                double dy2 = orders.get(j).getDropoffPoint().lat() - cLat;
                double dot = dx1 * dx2 + dy1 * dy2;
                double mag = Math.sqrt(dx1 * dx1 + dy1 * dy1) * Math.sqrt(dx2 * dx2 + dy2 * dy2);
                totalCos += mag > 1e-9 ? (dot / mag + 1.0) / 2.0 : 0.5;
                pairs++;
            }
        }
        return pairs > 0 ? totalCos / pairs : 0.5;
    }

    private double computeStandaloneDistance(List<Order> orders) {
        double total = 0;
        for (Order o : orders) {
            total += o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0;
        }
        return total;
    }

    private double computeBundleRouteDistance(DispatchPlan plan) {
        double dist = 0;
        GeoPoint prev = plan.getDriver().getCurrentLocation();
        for (DispatchPlan.Stop stop : plan.getSequence()) {
            dist += prev.distanceTo(stop.location()) / 1000.0;
            prev = stop.location();
        }
        return dist;
    }
}
