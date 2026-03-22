package com.routechain.simulation;

import com.routechain.domain.*;

import java.util.*;

/**
 * Layer 7 — Reposition Agent.
 * Post-delivery end-zone optimization.
 *
 * After a driver completes their last delivery:
 * - Evaluate if staying is better than repositioning
 * - If repositioning, choose nearby zone with best net future gain
 * - Hard rules: max reposition 1.5km, expected gain > 1.3× cost
 */
public class RepositionAgent {

    private static final double MAX_REPOSITION_KM = 1.5;
    private static final double MIN_GAIN_RATIO = 1.3;
    private static final double AVG_ORDER_VALUE = 25000; // VND

    private final ZoneOpportunityScorer opportunityScorer;
    private final List<Region> zones;

    public RepositionAgent(ZoneOpportunityScorer opportunityScorer, List<Region> zones) {
        this.opportunityScorer = opportunityScorer;
        this.zones = zones;
    }

    /**
     * Decide reposition action for a driver who just completed all deliveries.
     */
    public RepositionDecision plan(Driver driver) {
        GeoPoint currentPos = driver.getCurrentLocation();

        // Current zone opportunity
        double currentOpp = opportunityScorer.computeContinuationValue(currentPos, zones);

        // Find nearby zones with better opportunity
        RepositionDecision bestDecision = new RepositionDecision(
                RepositionAction.STAY, currentPos, 0, 0, currentOpp);

        for (Region zone : zones) {
            double distKm = currentPos.distanceTo(zone.getCenter()) / 1000.0;
            if (distKm > MAX_REPOSITION_KM) continue;
            if (distKm < 0.1) continue; // already there

            double targetOpp = zone.getOpportunityScore();
            if (targetOpp <= currentOpp) continue;

            double expectedGain = targetOpp * AVG_ORDER_VALUE;
            double netFutureGain = opportunityScorer.computeNetFutureGain(
                    currentPos, zone.getCenter(),
                    expectedGain, distKm, currentOpp);

            double repositionCost = distKm * 3000 + (distKm / 15.0) * 5000;

            // Rule: only reposition if gain clearly exceeds cost
            if (netFutureGain > 0 && expectedGain > repositionCost * MIN_GAIN_RATIO) {
                if (netFutureGain > bestDecision.netFutureGain()) {
                    bestDecision = new RepositionDecision(
                            RepositionAction.REPOSITION_SHORT,
                            zone.getCenter(),
                            distKm,
                            netFutureGain,
                            targetOpp);
                }
            }
        }

        return bestDecision;
    }

    public enum RepositionAction { STAY, REPOSITION_SHORT }

    public record RepositionDecision(
            RepositionAction action,
            GeoPoint targetPosition,
            double distanceKm,
            double netFutureGain,
            double targetOpportunity) {}
}
