package com.routechain.core;

import com.routechain.simulation.DispatchPlan;

import java.util.Comparator;

public class DecisionExplainer {

    public CompactDecisionExplanation explain(DispatchPlan winner,
                                              AdaptiveScoreBreakdown breakdown,
                                              DispatchPlan comparator) {
        String primary = breakdown.featureContributions().entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> -Math.abs(entry.getValue())))
                .limit(3)
                .map(entry -> humanize(entry.getKey()))
                .reduce((left, right) -> left + ", " + right)
                .orElse("balanced utility");

        String summary;
        if (comparator == null) {
            summary = "selected " + winner.getBundle().bundleId()
                    + " because compact utility favored " + primary;
        } else {
            summary = "selected " + winner.getBundle().bundleId()
                    + " over " + comparator.getBundle().bundleId()
                    + " due to " + primary;
        }
        return new CompactDecisionExplanation(
                winner.getBundle().bundleId(),
                winner.getDriver().getId(),
                winner.getCompactPlanType(),
                summary,
                breakdown);
    }

    private String humanize(String featureName) {
        return switch (featureName) {
            case "on_time_probability" -> "on-time safety";
            case "deadhead_penalty" -> "deadhead control";
            case "bundle_efficiency" -> "bundle efficiency";
            case "merchant_alignment" -> "merchant alignment";
            case "delivery_corridor_quality" -> "delivery corridor";
            case "last_drop_landing" -> "landing quality";
            case "post_completion_empty_km" -> "empty-km after drop";
            case "cancel_risk" -> "cancel risk";
            default -> featureName.replace('_', ' ');
        };
    }
}
