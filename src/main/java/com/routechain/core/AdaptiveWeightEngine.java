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

    private final CompactPolicyConfig policyConfig;
    private final RegimeClassifier regimeClassifier = new RegimeClassifier();
    private final DualPenaltyController dualPenaltyController = new DualPenaltyController();
    private final Map<RegimeKey, double[]> regimeWeights = new EnumMap<>(RegimeKey.class);
    private final Map<RegimeKey, Double> regimeConfidence = new EnumMap<>(RegimeKey.class);
    private final Map<RegimeKey, Double> learningRates = new EnumMap<>(RegimeKey.class);
    private final Map<RegimeKey, Integer> sampleCounts = new EnumMap<>(RegimeKey.class);
    private boolean learningFrozen = false;
    private double learningRateMultiplier = 1.0;

    public AdaptiveWeightEngine() {
        this(CompactPolicyConfig.defaults());
    }

    public AdaptiveWeightEngine(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
        dualPenaltyController.configureWindow(this.policyConfig.rollingPenaltyWindow());
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
        if (learningFrozen) {
            dualPenaltyController.recordOutcome(outcome);
            dualPenaltyController.decay();
            return;
        }
        double[] current = regimeWeights.get(regimeKey);
        double[] updated = current.clone();
        double prediction = dot(current, phi.values());
        double error = outcome.totalReward() - prediction;
        int samples = sampleCounts.merge(regimeKey, 1, Integer::sum);
        double lr = effectiveLearningRate(regimeKey, samples);
        if (Math.abs(error) >= policyConfig.noisyOutcomeErrorThreshold()) {
            regimeConfidence.put(regimeKey, Math.max(0.25, regimeConfidence.getOrDefault(regimeKey, 0.70) - 0.04));
            dualPenaltyController.recordOutcome(outcome);
            dualPenaltyController.decay();
            return;
        }
        double[] values = phi.values();
        for (int i = 0; i < values.length; i++) {
            double gradient = clip(error * values[i] - (L2 * current[i]), policyConfig.gradientClip());
            double candidate = current[i] + clip(lr * gradient, policyConfig.maxFeatureUpdate());
            candidate = enforceSign(i, candidate);
            candidate = clamp(candidate);
            updated[i] = current[i] + (candidate - current[i]) * EMA_ALPHA;
        }
        regimeWeights.put(regimeKey, updated);
        regimeConfidence.put(regimeKey, confidenceFor(regimeKey, samples, error, outcome.totalReward()));
        dualPenaltyController.recordOutcome(outcome);
        dualPenaltyController.decay();
    }

    public WeightSnapshot snapshot() {
        return WeightSnapshot.copyOf(
                regimeWeights,
                regimeConfidence,
                learningRates,
                sampleCounts,
                dualPenaltyController.currentPenalties());
    }

    public void restore(WeightSnapshot snapshot) {
        regimeWeights.clear();
        regimeConfidence.clear();
        learningRates.clear();
        sampleCounts.clear();
        for (Map.Entry<RegimeKey, double[]> entry : snapshot.weights().entrySet()) {
            regimeWeights.put(entry.getKey(), entry.getValue().clone());
        }
        regimeConfidence.putAll(snapshot.confidences());
        learningRates.putAll(snapshot.learningRates());
        sampleCounts.putAll(snapshot.sampleCounts());
        dualPenaltyController.restore(snapshot.dualPenalties());
    }

    public Map<RegimeKey, Integer> sampleCounts() {
        return Map.copyOf(sampleCounts);
    }

    public boolean isLearningFrozen() {
        return learningFrozen;
    }

    public void setLearningFrozen(boolean learningFrozen) {
        this.learningFrozen = learningFrozen;
    }

    public void setLearningRateMultiplier(double learningRateMultiplier) {
        this.learningRateMultiplier = Math.max(0.05, Math.min(1.0, learningRateMultiplier));
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
        sampleCounts.put(key, 0);
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

    private double clip(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private double effectiveLearningRate(RegimeKey regimeKey, int samples) {
        double base = learningRates.getOrDefault(regimeKey, 0.16);
        double supportDecay = 1.0 / Math.sqrt(1.0 + samples / 8.0);
        return base * learningRateMultiplier * supportDecay;
    }

    private double confidenceFor(RegimeKey regimeKey, int samples, double error, double reward) {
        double support = Math.min(1.0, samples / (double) policyConfig.supportSamplesForFullConfidence());
        double fit = 1.0 - PlanFeatureVector.clamp01(Math.abs(error));
        double quality = PlanFeatureVector.clamp01(reward);
        double confidence = 0.25 + 0.35 * support + 0.20 * fit + 0.20 * quality;
        return PlanFeatureVector.clamp01(confidence);
    }
}
