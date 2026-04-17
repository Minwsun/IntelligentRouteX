package com.routechain.v2.scenario;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class RobustUtilityAggregator {
    private static final Map<ScenarioType, Double> SCENARIO_WEIGHTS;

    static {
        Map<ScenarioType, Double> weights = new EnumMap<>(ScenarioType.class);
        weights.put(ScenarioType.NORMAL, 1.00);
        weights.put(ScenarioType.WEATHER_BAD, 0.90);
        weights.put(ScenarioType.TRAFFIC_BAD, 0.90);
        weights.put(ScenarioType.DEMAND_SHIFT, 0.0);
        weights.put(ScenarioType.ZONE_BURST, 0.0);
        weights.put(ScenarioType.POST_DROP_SHIFT, 0.0);
        weights.put(ScenarioType.MERCHANT_DELAY, 0.75);
        weights.put(ScenarioType.DRIVER_DRIFT, 0.70);
        weights.put(ScenarioType.PICKUP_QUEUE, 0.70);
        SCENARIO_WEIGHTS = Map.copyOf(weights);
    }

    public RobustUtility aggregate(String proposalId, List<ScenarioEvaluation> evaluations) {
        List<ScenarioEvaluation> proposalEvaluations = evaluations.stream()
                .filter(evaluation -> evaluation.proposalId().equals(proposalId))
                .toList();
        List<ScenarioEvaluation> appliedEvaluations = proposalEvaluations.stream()
                .filter(ScenarioEvaluation::applied)
                .toList();
        List<ScenarioEvaluation> aggregateInput = appliedEvaluations.isEmpty()
                ? proposalEvaluations.stream().filter(evaluation -> evaluation.scenario() == ScenarioType.NORMAL).limit(1).toList()
                : appliedEvaluations;
        double totalWeight = aggregateInput.stream()
                .mapToDouble(evaluation -> SCENARIO_WEIGHTS.getOrDefault(evaluation.scenario(), 0.0))
                .sum();
        if (totalWeight <= 0.0) {
            totalWeight = 1.0;
        }
        double expectedValue = aggregateInput.stream()
                .mapToDouble(evaluation -> evaluation.value() * SCENARIO_WEIGHTS.getOrDefault(evaluation.scenario(), 0.0))
                .sum() / totalWeight;
        double worstCaseValue = aggregateInput.stream().mapToDouble(ScenarioEvaluation::value).min().orElse(0.0);
        double landingValue = aggregateInput.stream().mapToDouble(ScenarioEvaluation::landingValue).average().orElse(0.0);
        double averageStability = aggregateInput.stream().mapToDouble(ScenarioEvaluation::stabilityScore).average().orElse(0.0);
        double valueVariancePenalty = aggregateInput.size() <= 1 ? 0.0 : aggregateInput.stream()
                .mapToDouble(evaluation -> Math.abs(evaluation.value() - expectedValue))
                .average()
                .orElse(0.0) * 0.20;
        double stabilityScore = clamp(averageStability - valueVariancePenalty);
        double robustUtility = clamp(
                0.45 * expectedValue
                        + 0.20 * worstCaseValue
                        + 0.20 * landingValue
                        + 0.15 * stabilityScore);
        return new RobustUtility(
                "robust-utility/v1",
                proposalId,
                expectedValue,
                worstCaseValue,
                landingValue,
                stabilityScore,
                robustUtility,
                proposalEvaluations.size(),
                appliedEvaluations.size());
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
