package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * AI dispatch scorer — replaces nearest-driver heuristic with
 * multi-criteria scoring function.
 *
 * Score = a1*OnTimeProbability + a2*BundleGain + a3*ContinuationValue + a4*EarningScore
 *       - b1*DeadheadCost - b2*LateRisk - b3*OverloadRisk - b4*CancellationRisk
 */
public class DispatchScorer {

    // ── Scoring weights (tunable) ───────────────────────────────────────
    private static final double W_ON_TIME      = 0.25;
    private static final double W_BUNDLE_GAIN  = 0.10;
    private static final double W_CONTINUATION = 0.10;
    private static final double W_EARNING      = 0.10;
    private static final double W_DEADHEAD     = 0.20;
    private static final double W_LATE_RISK    = 0.10;
    private static final double W_OVERLOAD     = 0.05;
    private static final double W_CANCEL_RISK  = 0.10;

    // ── Hard constraint thresholds ──────────────────────────────────────
    private static final double MAX_LATE_RISK      = 0.85;  // reject if > 85%
    private static final double MIN_EARNING_FLOOR  = 5000;  // VND minimum
    private static final double MAX_DEADHEAD_KM    = 8.0;   // reject if > 8km
    private static final int    MAX_ACTIVE_ORDERS  = 3;     // max orders per driver

    private final List<Region> regions;
    private final double trafficIntensity;
    private final WeatherProfile weatherProfile;

    public DispatchScorer(List<Region> regions, double trafficIntensity,
                          WeatherProfile weatherProfile) {
        this.regions = regions;
        this.trafficIntensity = trafficIntensity;
        this.weatherProfile = weatherProfile;
    }

    /**
     * Generate all candidate assignments for pending orders against available drivers.
     * Returns scored and filtered candidates, sorted best-first.
     */
    public List<CandidateAssignment> generateAndScore(
            List<Order> pendingOrders, List<Driver> availableDrivers) {

        List<CandidateAssignment> allCandidates = new ArrayList<>();

        for (Order order : pendingOrders) {
            for (Driver driver : availableDrivers) {
                CandidateAssignment candidate = new CandidateAssignment(driver, order);
                scoreCandidate(candidate);

                // Hard constraint filtering
                if (passesHardConstraints(candidate)) {
                    allCandidates.add(candidate);
                }
            }
        }

        // Sort by total score descending
        allCandidates.sort(Comparator.comparingDouble(CandidateAssignment::getTotalScore).reversed());
        return allCandidates;
    }

    /**
     * Select the best non-conflicting assignments from scored candidates.
     * Greedy: pick best candidate, mark driver+order as taken, repeat.
     */
    public List<CandidateAssignment> selectBestAssignments(List<CandidateAssignment> scoredCandidates) {
        Set<String> assignedDrivers = new HashSet<>();
        Set<String> assignedOrders = new HashSet<>();
        List<CandidateAssignment> selected = new ArrayList<>();

        for (CandidateAssignment c : scoredCandidates) {
            String dId = c.getDriver().getId();
            String oId = c.getOrder().getId();

            if (assignedDrivers.contains(dId) || assignedOrders.contains(oId)) continue;

            selected.add(c);
            assignedDrivers.add(dId);
            assignedOrders.add(oId);
        }

        return selected;
    }

    // ── Scoring function ────────────────────────────────────────────────

    private void scoreCandidate(CandidateAssignment c) {
        Driver driver = c.getDriver();
        Order order = c.getOrder();

        // Raw distance
        double distM = driver.getCurrentLocation().distanceTo(order.getPickupPoint());
        double distKm = distM / 1000.0;
        c.setDistanceToPickupKm(distKm);

        // Speed estimation (km/h) adjusted for traffic + weather
        double baseSpeedKmh = 25.0;
        double speedFactor = 1.0 - trafficIntensity * 0.5;
        if (weatherProfile == WeatherProfile.HEAVY_RAIN) speedFactor *= 0.7;
        if (weatherProfile == WeatherProfile.STORM) speedFactor *= 0.4;
        double effectiveSpeed = baseSpeedKmh * Math.max(0.3, speedFactor);

        // ETA estimation
        double pickupMinutes = (distKm / effectiveSpeed) * 60.0;
        double deliveryDistKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        double deliveryMinutes = (deliveryDistKm / effectiveSpeed) * 60.0;
        c.setEstimatedPickupMinutes(pickupMinutes);
        c.setEstimatedDeliveryMinutes(deliveryMinutes);

        double totalMinutes = pickupMinutes + deliveryMinutes;

        // 1. OnTimeProbability: sigmoid based on time slack
        double slack = order.getPromisedEtaMinutes() - totalMinutes;
        double onTimeProb = sigmoid(slack, 3.0);
        c.setOnTimeProbability(onTimeProb);

        // 2. DeadheadCost: normalized [0,1], lower is better → higher cost
        double deadheadNorm = Math.min(1.0, distKm / MAX_DEADHEAD_KM);
        c.setDeadheadCost(deadheadNorm);

        // 3. LateRisk: inverse of on-time probability + pickup delay factor
        double lateRisk = 1.0 - onTimeProb;
        if (weatherProfile == WeatherProfile.HEAVY_RAIN) lateRisk += 0.1;
        if (weatherProfile == WeatherProfile.STORM) lateRisk += 0.2;
        lateRisk = Math.min(1.0, lateRisk);
        c.setLateRisk(lateRisk);

        // 4. EarningScore: normalized fee quality
        double earningNorm = Math.min(1.0, order.getQuotedFee() / 50000.0);
        c.setEarningScore(earningNorm);

        // 5. OverloadRisk: based on driver's current order count
        int currentOrders = driver.getCurrentOrderCount();
        double overload = (double) currentOrders / MAX_ACTIVE_ORDERS;
        c.setOverloadRisk(Math.min(1.0, overload));

        // 6. CancellationRisk: from order + driver acceptance rate
        double cancelRisk = order.getCancellationRisk() * 0.5
                + (1.0 - driver.getAcceptanceRate()) * 0.5;
        c.setCancellationRisk(cancelRisk);

        // 7. ContinuationValue: is the dropoff zone a high-demand zone?
        double contValue = computeContinuationValue(order.getDropoffPoint());
        c.setContinuationValue(contValue);

        // 8. BundleGain: placeholder for now (0 for single-order)
        c.setBundleGain(0.0);

        // ── Compute total score ─────────────────────────────────────────
        double score =
                + W_ON_TIME      * onTimeProb
                + W_BUNDLE_GAIN  * c.getBundleGain()
                + W_CONTINUATION * contValue
                + W_EARNING      * earningNorm
                - W_DEADHEAD     * deadheadNorm
                - W_LATE_RISK    * lateRisk
                - W_OVERLOAD     * overload
                - W_CANCEL_RISK  * cancelRisk;

        c.setTotalScore(score);

        // Confidence: how sure are we this is a good assignment
        double confidence = Math.max(0.0, Math.min(1.0,
                onTimeProb * 0.4 + (1.0 - deadheadNorm) * 0.3 + earningNorm * 0.3));
        c.setConfidence(confidence);
    }

    // ── Hard Constraints ────────────────────────────────────────────────

    private boolean passesHardConstraints(CandidateAssignment c) {
        // 1. Late risk too high
        if (c.getLateRisk() > MAX_LATE_RISK) return false;

        // 2. Earning too low
        if (c.getOrder().getQuotedFee() < MIN_EARNING_FLOOR) return false;

        // 3. Deadhead too far
        if (c.getDistanceToPickupKm() > MAX_DEADHEAD_KM) return false;

        // 4. Driver overloaded
        if (c.getDriver().getCurrentOrderCount() >= MAX_ACTIVE_ORDERS) return false;

        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /**
     * Compute continuation value: how likely is the driver to get
     * another order quickly after delivering in the dropoff zone.
     * Higher demand zones → higher continuation value.
     */
    private double computeContinuationValue(GeoPoint dropoffPoint) {
        for (Region region : regions) {
            if (region.contains(dropoffPoint)) {
                // Normalize demand pressure to [0, 1]
                double demand = region.getCurrentDemandPressure();
                double supply = region.getCurrentDriverSupply();
                if (supply < 1) supply = 1;
                double ratio = Math.min(1.0, demand / (supply * 3.0));
                return ratio;
            }
        }
        return 0.2; // default low continuation value for unknown zones
    }

    /**
     * Sigmoid function for smooth probability mapping.
     * center: where probability = 0.5
     */
    private static double sigmoid(double x, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * x));
    }
}
