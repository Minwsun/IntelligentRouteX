package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.util.ArrayList;
import java.util.List;

public final class PairHardGateEvaluator {
    private final RouteChainDispatchV2Properties properties;

    public PairHardGateEvaluator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public PairGateDecision evaluate(PairFeatureVector features) {
        List<String> reasons = new ArrayList<>();
        double pickupThreshold = features.weatherTightened()
                ? properties.getPair().getWeatherTightened().getPickupDistanceKmThreshold()
                : properties.getPair().getPickupDistanceKmThreshold();
        int readyThreshold = features.weatherTightened()
                ? properties.getPair().getWeatherTightened().getReadyGapMinutesThreshold()
                : properties.getPair().getReadyGapMinutesThreshold();
        double mergeThreshold = features.weatherTightened()
                ? properties.getPair().getWeatherTightened().getMergeEtaRatioThreshold()
                : properties.getPair().getMergeEtaRatioThreshold();

        if (features.pickupDistanceKm() > pickupThreshold) {
            reasons.add("pickup-distance-too-far");
        }
        if (features.readyGapMinutes() > readyThreshold) {
            reasons.add("ready-gap-too-large");
        }
        if (features.dropAngleDiffDegrees() > properties.getPair().getDropAngleDiffDegreesThreshold()) {
            reasons.add("drop-angle-too-wide");
        }
        if (features.mergeEtaRatio() > mergeThreshold) {
            reasons.add("merge-eta-ratio-too-high");
        }

        return new PairGateDecision("pair-gate-decision/v1", reasons.isEmpty(), List.copyOf(reasons));
    }
}

