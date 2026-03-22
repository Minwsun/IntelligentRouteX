package com.routechain.simulation;

import com.routechain.domain.*;

import java.util.*;

/**
 * Layer 2 — Zone Opportunity Scorer.
 * Computes OpportunityScore and ContinuationValue for each zone.
 *
 * OpportunityScore(z, t) =
 *   0.35 * PredictedDemand(z, t+10m)
 * + 0.20 * SpikeProbability(z)
 * + 0.15 * ShortageRatio(z, t)
 * + 0.10 * HistoricalConversion(z)
 * + 0.10 * ZoneTypeWeight(z)
 * - 0.10 * DriverOversupply(z, t)
 */
public class ZoneOpportunityScorer {

    /**
     * Compute opportunity scores for all zones.
     */
    public void scoreAllZones(List<Region> zones) {
        // Normalize predicted demand across zones
        double maxDemand = zones.stream()
                .mapToDouble(Region::getPredictedDemand10m)
                .max().orElse(1.0);
        if (maxDemand < 0.01) maxDemand = 1.0;

        for (Region zone : zones) {
            double demandNorm = zone.getPredictedDemand10m() / maxDemand;
            double spike = zone.getSpikeProbability();
            double shortage = zone.getShortageRatio();
            double historical = Math.min(1.0, zone.getHistoricalOrderDensity() / 5.0);
            double zoneWeight = zoneTypeWeight(zone);
            double oversupply = zone.getDriverOversupply();

            double score =
                    0.35 * demandNorm
                  + 0.20 * spike
                  + 0.15 * shortage
                  + 0.10 * historical
                  + 0.10 * zoneWeight
                  - 0.10 * Math.min(1.0, oversupply);

            zone.setOpportunityScore(Math.max(0, Math.min(1.0, score)));
        }
    }

    /**
     * Compute continuation value for a specific dropoff point.
     * What is the expected value of ending at this location?
     */
    public double computeContinuationValue(GeoPoint dropoffPoint, List<Region> zones) {
        Region bestZone = null;
        double bestDist = Double.MAX_VALUE;

        for (Region zone : zones) {
            double dist = zone.getCenter().distanceTo(dropoffPoint);
            if (dist < zone.getRadiusMeters() * 1.5 && dist < bestDist) {
                bestDist = dist;
                bestZone = zone;
            }
        }

        if (bestZone == null) return 0.1; // unknown zone, low value

        double proximityBonus = 1.0 - Math.min(1.0, bestDist / (bestZone.getRadiusMeters() * 2));
        return bestZone.getOpportunityScore() * (0.6 + 0.4 * proximityBonus);
    }

    /**
     * Compute Net Future Gain for a reposition decision.
     *
     * NetFutureGain = ExpectedNextOrderValue - RepositionCost - MissedOpportunityCost
     */
    public double computeNetFutureGain(
            GeoPoint currentPos, GeoPoint targetZoneCenter,
            double expectedOrderValue, double repositionDistKm,
            double currentZoneOpportunity) {

        double repositionCost = repositionDistKm * 3000; // ~3000 VND/km deadhead cost
        double repositionTimeCost = repositionDistKm / 15.0 * 5000; // time cost at 15 km/h

        // Missed opportunity: what if we stay and get an order here?
        double missedCost = currentZoneOpportunity * expectedOrderValue * 0.5;

        return expectedOrderValue - repositionCost - repositionTimeCost - missedCost;
    }

    private double zoneTypeWeight(Region zone) {
        return switch (zone.getZoneType()) {
            case CBD -> 0.9;
            case RESIDENTIAL -> 0.7;
            case CAMPUS -> 0.6;
            case INDUSTRIAL -> 0.4;
            case MIXED -> 0.65;
        };
    }
}
