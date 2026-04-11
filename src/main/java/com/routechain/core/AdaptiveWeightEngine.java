package com.routechain.core;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class AdaptiveWeightEngine {
    private static final double L2 = 0.012;
    private static final double EMA_ALPHA = 0.18;
    private static final double MIN_WEIGHT = -1.20;
    private static final double MAX_WEIGHT = 1.20;

    private final RegimeClassifier regimeClassifier = new RegimeClassifier();
    private final DualPenaltyController dualPenaltyController = new DualPenaltyController();
    private final Map<RegimeKey, double[]> regimeWeights = new EnumMap<>(RegimeKey.class);
    private final Map<RegimeKey, Double> regimeConfidence = new EnumMap<>(RegimeKey.class);
    private final Map<RegimeKey, Double> learningRates = new EnumMap<>(RegimeKey.class);

    public AdaptiveWeightEngine() {
        seedRegime(RegimeKey.CLEAR_NORMAL, new double[] {0.42, -0.34, 0.26, 0.18, 0.22, 0.20, -0.20, -0.26}, 0.86, 0.18);
        seedRegime(RegimeKey.CLEAR_SHORTAGE, new double[] {0.46, -0.30, 0.24, 0.16, 0.18, 0.18, -0.18, -0.30}, 0.74, 0.20);
        seedRegime(RegimeKey.RAIN_STRESS, new double[] {0.52, -0.40, 0.18, 0.14, 0.12, 0.12, -0.24, -0.34}, 0.72, 0.16);
        seedRegime(RegimeKey.OFFPEAK_LOW_DENSITY, new double[] {0.36, -0.24, 0.20, 0.14, 0.24, 0.28, -0.18, -0.18}, 0.70, 0.14);
    }

    public double score(PlanFeatureVector phi, CompactDispatchContext context) {
        return explain(phi, context).finalScore();
    }

    public AdaptiveScoreBreakdown explain(PlanFeatureVector phi, CompactDispatchContext context) {
        RegimeKey regimeKey = resolveRegime(context);
        double[] weights = regimeWeights.get(regimeKey);
        double utilityScore = 0.0;
        Map<String, Double> contributions = new LinkedHashMap<>();
        double[] values = phi.values();
        for (int i = 0; i < values.length; i++) {
            double contribution = weights[i] * values[i];
            utilityScore += contribution;
            contributions.put(PlanFeatureVector.featureName(i), contribution);
        }
        double penaltyScore = dualPenaltyController.scorePenalty(phi);
        return AdaptiveScoreBreakdown.of(
                regimeKey,
                utilityScore,
                penaltyScore,
                utilityScore - penaltyScore,
                contributions,
                dualPenaltyController.currentPenalties());
    }

    public void recordOutcome(PlanFeatureVector phi, CompactDispatchContext context, OutcomeVector outcome) {
        RegimeKey regimeKey = resolveRegime(context);
        recordOutcome(regimeKey, phi, outcome);
    }

    public void recordOutcome(RegimeKey regimeKey, PlanFeatureVector phi, OutcomeVector outcome) {
        double[] current = regimeWeights.get(regimeKey);
        double[] updated = current.clone();
        double prediction = dot(current, phi.values());
        double error = outcome.totalReward() - prediction;
        double lr = learningRates.getOrDefault(regimeKey, 0.16);
        double[] values = phi.values();
        for (int i = 0; i < values.length; i++) {
            double gradient = error * values[i] - (L2 * current[i]);
            double candidate = current[i] + lr * gradient;
            candidate = enforceSign(i, candidate);
            candidate = clamp(candidate);
            updated[i] = current[i] + (candidate - current[i]) * EMA_ALPHA;
        }
        regimeWeights.put(regimeKey, updated);
        regimeConfidence.put(regimeKey, Math.min(0.99, regimeConfidence.getOrDefault(regimeKey, 0.70) + 0.02));
        dualPenaltyController.recordOutcome(outcome);
        dualPenaltyController.decay();
    }

    public WeightSnapshot snapshot() {
        return WeightSnapshot.copyOf(regimeWeights, regimeConfidence, learningRates, dualPenaltyController.currentPenalties());
    }

    public void restore(WeightSnapshot snapshot) {
        regimeWeights.clear();
        regimeConfidence.clear();
        learningRates.clear();
        for (Map.Entry<RegimeKey, double[]> entry : snapshot.weights().entrySet()) {
            regimeWeights.put(entry.getKey(), entry.getValue().clone());
        }
        regimeConfidence.putAll(snapshot.confidences());
        learningRates.putAll(snapshot.learningRates());
        dualPenaltyController.restore(snapshot.dualPenalties());
    }

    private RegimeKey resolveRegime(CompactDispatchContext context) {
        RegimeClassifier.Result result = regimeClassifier.classify(
                context.trafficIntensity(),
                context.weatherProfile(),
                context.simulatedHour(),
                context.pendingOrderCount(),
                context.availableDriverCount());
        RegimeKey resolved = result.resolvedKey();
        regimeConfidence.putIfAbsent(resolved, result.confidence());
        return resolved;
    }

    private void seedRegime(RegimeKey key, double[] weights, double confidence, double learningRate) {
        regimeWeights.put(key, Arrays.copyOf(weights, weights.length));
        regimeConfidence.put(key, confidence);
        learningRates.put(key, learningRate);
    }

    private double dot(double[] weights, double[] values) {
        double total = 0.0;
        for (int i = 0; i < weights.length && i < values.length; i++) {
            total += weights[i] * values[i];
        }
        return total;
    }

    private double enforceSign(int index, double candidate) {
        return switch (index) {
            case 0, 2, 3, 4, 5 -> Math.max(0.0, candidate);
            case 1, 6, 7 -> Math.min(0.0, candidate);
            default -> candidate;
        };
    }

    private double clamp(double value) {
        return Math.max(MIN_WEIGHT, Math.min(MAX_WEIGHT, value));
    }
}
