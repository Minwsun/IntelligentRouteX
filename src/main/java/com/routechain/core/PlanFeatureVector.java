package com.routechain.core;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public record PlanFeatureVector(
        double onTimeProbability,
        double deadheadPenalty,
        double bundleEfficiency,
        double merchantAlignment,
        double deliveryCorridorQuality,
        double lastDropLanding,
        double postCompletionEmptyKm,
        double cancelRisk) {

    public static final int SIZE = 8;

    public double[] values() {
        return new double[] {
                onTimeProbability,
                deadheadPenalty,
                bundleEfficiency,
                merchantAlignment,
                deliveryCorridorQuality,
                lastDropLanding,
                postCompletionEmptyKm,
                cancelRisk
        };
    }

    public Map<String, Double> asMap() {
        Map<String, Double> values = new LinkedHashMap<>();
        values.put("on_time_probability", onTimeProbability);
        values.put("deadhead_penalty", deadheadPenalty);
        values.put("bundle_efficiency", bundleEfficiency);
        values.put("merchant_alignment", merchantAlignment);
        values.put("delivery_corridor_quality", deliveryCorridorQuality);
        values.put("last_drop_landing", lastDropLanding);
        values.put("post_completion_empty_km", postCompletionEmptyKm);
        values.put("cancel_risk", cancelRisk);
        return values;
    }

    public static String featureName(int index) {
        return switch (index) {
            case 0 -> "on_time_probability";
            case 1 -> "deadhead_penalty";
            case 2 -> "bundle_efficiency";
            case 3 -> "merchant_alignment";
            case 4 -> "delivery_corridor_quality";
            case 5 -> "last_drop_landing";
            case 6 -> "post_completion_empty_km";
            case 7 -> "cancel_risk";
            default -> "feature_" + index;
        };
    }

    public static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    @Override
    public String toString() {
        return "PlanFeatureVector" + Arrays.toString(values());
    }
}
