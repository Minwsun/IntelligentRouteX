package com.routechain.core;

import com.routechain.domain.Enums.WeatherProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdaptiveWeightEngineTest {

    @Test
    void positiveOutcomeShouldLiftScoreForSimilarPlan() {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        CompactDispatchContext context = new CompactDispatchContext(
                java.util.List.of(),
                12,
                0.22,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T05:00:00Z"),
                6,
                4);
        PlanFeatureVector phi = new PlanFeatureVector(0.82, 0.18, 0.72, 0.64, 0.68, 0.70, 0.20, 0.10);

        double before = engine.score(phi, context);
        engine.recordOutcome(phi, context, new OutcomeVector(0.95, 1.0, 0.90, 0.82, 0.84, 0.88, 0.96));
        double after = engine.score(phi, context);

        assertTrue(after > before, "A strong realized outcome should increase the score for a similar compact plan");
    }

    @Test
    void restoreShouldRecoverPreviousSnapshot() {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        CompactDispatchContext context = new CompactDispatchContext(
                java.util.List.of(),
                19,
                0.30,
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-11T12:00:00Z"),
                8,
                5);
        PlanFeatureVector phi = new PlanFeatureVector(0.78, 0.22, 0.60, 0.58, 0.62, 0.66, 0.28, 0.14);

        WeightSnapshot snapshot = engine.snapshot();
        double baseline = engine.score(phi, context);

        engine.recordOutcome(phi, context, new OutcomeVector(0.90, 1.0, 0.84, 0.70, 0.76, 0.80, 0.92));
        engine.restore(snapshot);

        assertEquals(baseline, engine.score(phi, context), 1e-9,
                "Restoring a snapshot should recover the original compact scoring behavior");
    }

    @Test
    void resolvedSampleShouldWaitForSupportThresholdBeforeUpdatingWeights() {
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();
        PlanFeatureVector phi = new PlanFeatureVector(0.82, 0.18, 0.72, 0.64, 0.68, 0.70, 0.20, 0.10);
        double baselineWeight = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        for (int i = 0; i < 29; i++) {
            boolean applied = engine.recordResolvedSample(sample("decision-" + i, phi, 0.50));
            assertEquals(false, applied);
        }

        double beforeThresholdWeight = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];
        boolean appliedAtThreshold = engine.recordResolvedSample(sample("decision-29", phi, 0.50));
        double afterThresholdWeight = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        assertEquals(baselineWeight, beforeThresholdWeight, 1e-9);
        assertEquals(true, appliedAtThreshold);
        assertTrue(afterThresholdWeight > beforeThresholdWeight,
                "Weight updates should only begin after enough resolved support has accumulated");
    }

    private ResolvedDecisionSample sample(String decisionId, PlanFeatureVector phi, double predictedReward) {
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                predictedReward + 0.04,
                0.02,
                predictedReward,
                java.util.Map.of("on_time_probability", 0.20),
                java.util.Map.of("lambda_empty_after", 0.03));
        DecisionLogRecord decisionLog = new DecisionLogRecord(
                decisionId,
                "driver-" + decisionId,
                "bundle-" + decisionId,
                CompactPlanType.SINGLE_LOCAL,
                java.util.List.of("order-" + decisionId),
                RegimeKey.CLEAR_NORMAL,
                phi,
                breakdown,
                engineSnapshot(),
                Instant.parse("2026-04-12T05:00:00Z"),
                predictedReward,
                0.9,
                42000.0,
                0.74,
                0.6,
                phi.cancelRisk(),
                phi.onTimeProbability());
        return new ResolvedDecisionSample(
                decisionLog,
                new OutcomeVector(0.94, 1.0, 0.90, 0.82, 0.84, 0.88, 0.98),
                DecisionOutcomeStage.AFTER_POST_DROP_WINDOW,
                Instant.parse("2026-04-12T05:10:00Z"));
    }

    private WeightSnapshot engineSnapshot() {
        return new AdaptiveWeightEngine().snapshot();
    }
}
