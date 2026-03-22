package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * Layer 5 — Plan Score Calculator.
 * Multi-objective scoring with 12 terms + hard constraints.
 *
 * TotalScore(plan) =
 *   + w1 * OnTimeScore
 *   + w2 * DriverProfitScore
 *   + w3 * BundleEfficiencyScore
 *   + w4 * EndZoneOpportunityScore
 *   + w5 * CustomerFeeScore
 *   + w6 * NextOrderAcquisitionScore
 *   - p1 * DeadheadPenalty
 *   - p2 * CongestionPenalty
 *   - p3 * LateRiskPenalty
 *   - p4 * CancellationRiskPenalty
 *   - p5 * OverloadPenalty
 *   - p6 * RepositionPenalty
 */
public class PlanScoreCalculator {

    // ── Positive weights ────────────────────────────────────────────────
    private static final double W_ON_TIME      = 0.18;
    private static final double W_PROFIT       = 0.15;
    private static final double W_BUNDLE_EFF   = 0.12;
    private static final double W_END_ZONE     = 0.12;
    private static final double W_CUST_FEE     = 0.08;
    private static final double W_NEXT_ORDER   = 0.10;

    // ── Penalty weights ─────────────────────────────────────────────────
    private static final double P_DEADHEAD     = 0.08;
    private static final double P_CONGESTION   = 0.05;
    private static final double P_LATE_RISK    = 0.05;
    private static final double P_CANCEL_RISK  = 0.03;
    private static final double P_OVERLOAD     = 0.02;
    private static final double P_REPOSITION   = 0.02;

    // ── Hard constraint thresholds ──────────────────────────────────────
    private static final double MAX_LATE_RISK_PER_ORDER = 0.35;
    private static final double MIN_PROFIT_PER_ORDER = 3000;  // VND
    private static final double MAX_PICKUP_DEADHEAD_KM = 6.0;
    private static final double MAX_DETOUR_RATIO = 1.8;

    private final ZoneOpportunityScorer opportunityScorer;
    private final List<Region> zones;
    private final double trafficIntensity;
    private final WeatherProfile weather;

    public PlanScoreCalculator(ZoneOpportunityScorer opportunityScorer,
                                List<Region> zones,
                                double trafficIntensity, WeatherProfile weather) {
        this.opportunityScorer = opportunityScorer;
        this.zones = zones;
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;
    }

    /**
     * Predict plan metrics and compute total score.
     * Returns false if plan violates hard constraints.
     */
    public boolean scoreAndPredict(DispatchPlan plan) {
        Driver driver = plan.getDriver();
        List<Order> orders = plan.getOrders();

        // ── Predict metrics ─────────────────────────────────────────────
        double deadheadKm = driver.getCurrentLocation()
                .distanceTo(orders.get(0).getPickupPoint()) / 1000.0;
        plan.setPredictedDeadheadKm(deadheadKm);

        double totalMinutes = plan.getSequence().isEmpty() ? 0
                : plan.getSequence().get(plan.getSequence().size() - 1).estimatedArrivalMinutes();
        plan.setPredictedTotalMinutes(totalMinutes);

        // On-time probability: min across all orders in bundle
        double minOnTime = 1.0;
        double maxLateRisk = 0;
        double totalFee = 0;
        double totalStandaloneDist = 0;

        for (Order order : orders) {
            double orderEta = findDropoffTime(plan, order.getId());
            double slack = order.getPromisedEtaMinutes() - orderEta;
            double onTimeProb = sigmoid(slack, 2.5);
            minOnTime = Math.min(minOnTime, onTimeProb);

            double lateRisk = 1.0 - onTimeProb;
            if (weather == WeatherProfile.HEAVY_RAIN) lateRisk += 0.08;
            if (weather == WeatherProfile.STORM) lateRisk += 0.15;
            maxLateRisk = Math.max(maxLateRisk, Math.min(1.0, lateRisk));

            totalFee += order.getQuotedFee();
            totalStandaloneDist += order.getPickupPoint()
                    .distanceTo(order.getDropoffPoint()) / 1000.0;
        }

        plan.setOnTimeProbability(minOnTime);
        plan.setLateRisk(maxLateRisk);

        // Driver profit
        double payout = totalFee + (orders.size() > 1 ? orders.size() * 2000 : 0); // bundle bonus
        double fuelCost = (deadheadKm + totalStandaloneDist) * 1500; // ~1500 VND/km
        double profit = payout - fuelCost;
        plan.setDriverProfit(profit);
        plan.setCustomerFee(totalFee / orders.size()); // avg fee per order

        // Bundle efficiency
        double bundleDist = computeBundleRouteDistance(plan);
        double efficiency = totalStandaloneDist > 0
                ? Math.max(0, (totalStandaloneDist - bundleDist) / totalStandaloneDist) : 0;
        plan.setBundleEfficiency(efficiency);

        // End zone opportunity
        GeoPoint endZone = plan.getEndZonePoint();
        double endZoneOpp = opportunityScorer.computeContinuationValue(endZone, zones);
        plan.setEndZoneOpportunity(endZoneOpp);

        // Next order acquisition
        double nextOrderScore = endZoneOpp * (1.0 - deadheadKm / 10.0);
        plan.setNextOrderAcquisitionScore(Math.max(0, nextOrderScore));

        // Congestion penalty
        double congestion = trafficIntensity * 0.6;
        if (weather == WeatherProfile.HEAVY_RAIN) congestion += 0.15;
        if (weather == WeatherProfile.STORM) congestion += 0.3;
        plan.setCongestionPenalty(Math.min(1.0, congestion));

        // Cancellation risk
        double cancelRisk = 0;
        for (Order order : orders) {
            cancelRisk = Math.max(cancelRisk, order.getCancellationRisk());
        }
        if (totalMinutes > 45) cancelRisk += 0.15;
        plan.setCancellationRisk(Math.min(1.0, cancelRisk));

        // Reposition penalty (how far from current useful zone)
        plan.setRepositionPenalty(0); // computed separately by RepositionAgent

        // ── Hard constraints ────────────────────────────────────────────
        if (!passesHardConstraints(plan, deadheadKm, maxLateRisk,
                profit / orders.size(), totalStandaloneDist, bundleDist)) {
            return false;
        }

        // ── Score computation ───────────────────────────────────────────
        double score = computeScore(plan);
        plan.setTotalScore(score);

        double conf = computeConfidence(plan);
        plan.setConfidence(conf);

        return true;
    }

    // ── Score function ──────────────────────────────────────────────────

    private double computeScore(DispatchPlan plan) {
        double profitNorm = Math.min(1.0, plan.getDriverProfit() / 50000.0);
        double feeScore = 1.0 - Math.min(1.0, plan.getCustomerFee() / 35000.0);
        double deadheadNorm = Math.min(1.0, plan.getPredictedDeadheadKm() / MAX_PICKUP_DEADHEAD_KM);
        double overload = (double) plan.getDriver().getCurrentOrderCount() / 5.0;

        return
                + W_ON_TIME      * plan.getOnTimeProbability()
                + W_PROFIT       * profitNorm
                + W_BUNDLE_EFF   * plan.getBundleEfficiency()
                + W_END_ZONE     * plan.getEndZoneOpportunity()
                + W_CUST_FEE     * feeScore
                + W_NEXT_ORDER   * plan.getNextOrderAcquisitionScore()
                - P_DEADHEAD     * deadheadNorm
                - P_CONGESTION   * plan.getCongestionPenalty()
                - P_LATE_RISK    * plan.getLateRisk()
                - P_CANCEL_RISK  * plan.getCancellationRisk()
                - P_OVERLOAD     * Math.min(1.0, overload)
                - P_REPOSITION   * plan.getRepositionPenalty();
    }

    private double computeConfidence(DispatchPlan plan) {
        double conf = plan.getOnTimeProbability() * 0.35
                + (1.0 - plan.getLateRisk()) * 0.25
                + plan.getBundleEfficiency() * 0.15
                + plan.getEndZoneOpportunity() * 0.15
                + (plan.getDriverProfit() > 0 ? 0.10 : 0);
        return Math.max(0.05, Math.min(1.0, conf));
    }

    // ── Hard constraints ────────────────────────────────────────────────

    private boolean passesHardConstraints(DispatchPlan plan, double deadheadKm,
                                           double maxLateRisk, double profitPerOrder,
                                           double standaloneDist, double bundleDist) {
        // Any order has excessive late risk
        if (maxLateRisk > MAX_LATE_RISK_PER_ORDER) return false;

        // Profit floor
        if (profitPerOrder < MIN_PROFIT_PER_ORDER) return false;

        // Deadhead too far
        if (deadheadKm > MAX_PICKUP_DEADHEAD_KM) return false;

        // Detour ratio: bundle route should not be absurdly longer than standalone
        if (standaloneDist > 0 && bundleDist / standaloneDist > MAX_DETOUR_RATIO) return false;

        // Driver overloaded
        if (plan.getDriver().getCurrentOrderCount() >= 5) return false;

        return true;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private double findDropoffTime(DispatchPlan plan, String orderId) {
        for (DispatchPlan.Stop stop : plan.getSequence()) {
            if (stop.orderId().equals(orderId)
                    && stop.type() == DispatchPlan.Stop.StopType.DROPOFF) {
                return stop.estimatedArrivalMinutes();
            }
        }
        return plan.getPredictedTotalMinutes();
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

    private static double sigmoid(double x, double steepness) {
        return 1.0 / (1.0 + Math.exp(-steepness * x));
    }
}
