package com.routechain.ai;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.SelectionBucket;

/**
 * Compact graph-aware state representation for one candidate route.
 */
public record GraphRouteState(
        String zoneKey,
        String serviceTier,
        SelectionBucket selectionBucket,
        WeatherProfile weatherProfile,
        int endHourBucket,
        int bundleSize,
        double pickupCost,
        double batchSynergy,
        double dropCoherence,
        double slaRisk,
        double deadheadPenalty,
        double futureOpportunity,
        double positioningValue,
        double stressPenalty,
        double graphAffinity,
        double postDropDemandProbability,
        double expectedNextOrderIdleMinutes,
        double expectedPostCompletionEmptyKm,
        double borrowedDependency,
        double emptyRiskAfter
) {}
