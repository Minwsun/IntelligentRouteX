package com.routechain.simulation;

import java.util.Map;

/**
 * Read-only snapshot of the contextual policy tuner for control-room/demo output.
 */
public record RoutePolicyProfile(
        String policyName,
        String serviceTier,
        String executionProfile,
        String routeLatencyMode,
        String gateProfile,
        String reserveProfile,
        String contextualBanditMode,
        double explorationRate,
        double shortageRatio,
        double avgPendingWaitMinutes,
        double surgeLevel,
        double trafficIntensity,
        String weatherProfile,
        Map<String, Double> predictedRewards,
        Map<String, Integer> selectionCounts
) {
    public RoutePolicyProfile {
        policyName = policyName == null || policyName.isBlank() ? "NORMAL" : policyName;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        executionProfile = executionProfile == null || executionProfile.isBlank()
                ? "MAINLINE_REALISTIC"
                : executionProfile;
        routeLatencyMode = routeLatencyMode == null || routeLatencyMode.isBlank()
                ? "SIMULATED_ASYNC"
                : routeLatencyMode;
        gateProfile = gateProfile == null || gateProfile.isBlank()
                ? "execution-first-default"
                : gateProfile;
        reserveProfile = reserveProfile == null || reserveProfile.isBlank()
                ? "balanced-reserve-shaping"
                : reserveProfile;
        contextualBanditMode = contextualBanditMode == null || contextualBanditMode.isBlank()
                ? "epsilon-greedy"
                : contextualBanditMode;
        weatherProfile = weatherProfile == null || weatherProfile.isBlank()
                ? "CLEAR"
                : weatherProfile;
        predictedRewards = predictedRewards == null ? Map.of() : Map.copyOf(predictedRewards);
        selectionCounts = selectionCounts == null ? Map.of() : Map.copyOf(selectionCounts);
    }
}
