package com.routechain.simulation;

import com.routechain.core.AdaptiveScoreBreakdown;
import com.routechain.core.AdaptiveWeightEngine;
import com.routechain.core.CompactPlanType;
import com.routechain.core.DecisionLogRecord;
import com.routechain.core.DecisionOutcomeStage;
import com.routechain.core.OutcomeVector;
import com.routechain.core.PlanFeatureVector;
import com.routechain.core.RegimeKey;
import com.routechain.core.ResolvedDecisionSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactLearningRuntimeTest {

    @Test
    void shouldIgnoreDuplicateResolvedDecisionIds() {
        CompactLearningRuntime runtime = new CompactLearningRuntime(com.routechain.core.CompactPolicyConfig.defaults());
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();

        for (int i = 0; i < 29; i++) {
            runtime.resolveAndApply(resolution("seed-" + i, 0.52), Instant.parse("2026-04-12T06:10:00Z"), engine);
        }
        double before = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        runtime.resolveAndApply(resolution("dup-1", 0.52), Instant.parse("2026-04-12T06:11:00Z"), engine);
        double afterFirst = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        runtime.resolveAndApply(resolution("dup-1", 0.52), Instant.parse("2026-04-12T06:12:00Z"), engine);
        double afterDuplicate = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        assertEquals(true, afterFirst > before);
        assertEquals(afterFirst, afterDuplicate, 1e-9);
    }

    @Test
    void shouldAllowDifferentStagesForSameDecisionWithoutDoubleApplyingFinalWeightUpdate() {
        CompactLearningRuntime runtime = new CompactLearningRuntime(com.routechain.core.CompactPolicyConfig.defaults());
        AdaptiveWeightEngine engine = new AdaptiveWeightEngine();

        for (int i = 0; i < 29; i++) {
            runtime.resolveAndApply(resolution("seed-" + i, 0.52, DecisionOutcomeStage.AFTER_POST_DROP_WINDOW), Instant.parse("2026-04-12T06:10:00Z"), engine);
        }

        double before = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];
        runtime.resolveAndApply(resolution("stage-1", 0.52, DecisionOutcomeStage.AFTER_ACCEPT), Instant.parse("2026-04-12T06:11:00Z"), engine);
        double afterAccept = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];
        runtime.resolveAndApply(resolution("stage-1", 0.52, DecisionOutcomeStage.AFTER_TERMINAL), Instant.parse("2026-04-12T06:12:00Z"), engine);
        double afterTerminal = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];
        runtime.resolveAndApply(resolution("stage-1", 0.52, DecisionOutcomeStage.AFTER_POST_DROP_WINDOW), Instant.parse("2026-04-12T06:13:00Z"), engine);
        double afterFinal = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];
        runtime.resolveAndApply(resolution("stage-1", 0.52, DecisionOutcomeStage.AFTER_POST_DROP_WINDOW), Instant.parse("2026-04-12T06:14:00Z"), engine);
        double afterFinalDuplicate = engine.snapshot().weights().get(RegimeKey.CLEAR_NORMAL)[0];

        assertEquals(before, afterAccept, 1e-9);
        assertEquals(afterAccept, afterTerminal, 1e-9);
        assertTrue(afterFinal > afterTerminal);
        assertEquals(afterFinal, afterFinalDuplicate, 1e-9);
    }

    private com.routechain.core.CompactDecisionResolution resolution(String decisionId,
                                                                     double predictedReward,
                                                                     DecisionOutcomeStage stage) {
        PlanFeatureVector phi = new PlanFeatureVector(0.84, 0.16, 0.74, 0.62, 0.68, 0.72, 0.18, 0.08);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                predictedReward + 0.03,
                0.02,
                predictedReward,
                Map.of("on_time_probability", 0.24),
                Map.of("lambda_empty_after", 0.03));
        DecisionLogRecord decisionLog = new DecisionLogRecord(
                decisionId,
                "driver-" + decisionId,
                "bundle-" + decisionId,
                CompactPlanType.SINGLE_LOCAL,
                List.of("order-" + decisionId),
                RegimeKey.CLEAR_NORMAL,
                phi,
                breakdown,
                new AdaptiveWeightEngine().snapshot(),
                Instant.parse("2026-04-12T06:00:00Z"),
                predictedReward,
                predictedReward,
                12.0,
                0.8,
                40000.0,
                0.70,
                0.60,
                0.5,
                2.2,
                phi.cancelRisk(),
                phi.onTimeProbability(),
                4.4);
        ResolvedDecisionSample sample = new ResolvedDecisionSample(
                decisionLog,
                new OutcomeVector(0.96, 1.0, 0.90, 0.84, 0.86, 0.90, 0.98),
                stage,
                stage == DecisionOutcomeStage.AFTER_ACCEPT ? Double.NaN : 11.0,
                false,
                stage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW,
                stage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW ? 0.4 : Double.NaN,
                stage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW ? 1.8 : Double.NaN,
                Instant.parse("2026-04-12T06:10:00Z"));
        return new com.routechain.core.CompactDecisionResolution(
                decisionId,
                decisionLog.driverId(),
                decisionLog.bundleId(),
                decisionLog.orderIds(),
                decisionLog.regimeKey(),
                decisionLog.featureVector(),
                sample.outcomeVector(),
                decisionLog.snapshotBefore(),
                null,
                decisionLog.scoreBreakdown(),
                decisionLog,
                sample,
                stage == DecisionOutcomeStage.AFTER_POST_DROP_WINDOW,
                sample.resolvedAt());
    }

    private com.routechain.core.CompactDecisionResolution resolution(String decisionId, double predictedReward) {
        return resolution(decisionId, predictedReward, DecisionOutcomeStage.AFTER_POST_DROP_WINDOW);
    }
}
