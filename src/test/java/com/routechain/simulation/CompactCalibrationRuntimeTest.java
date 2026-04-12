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

import static org.junit.jupiter.api.Assertions.assertTrue;

class CompactCalibrationRuntimeTest {

    @Test
    void calibrationSnapshotShouldReflectObservedEtaCancelAndPostDropErrors() {
        CompactCalibrationRuntime runtime = new CompactCalibrationRuntime();
        runtime.recordResolvedSample(sample("decision-1", 18.0, 12.0, false, true, 3.0, 1.0));
        runtime.recordResolvedSample(sample("decision-2", 10.0, 15.0, true, false, Double.NaN, 4.0));
        DecisionLogRecord calibrated = runtime.calibrateDecisionLog(sample("decision-3", 18.0, 18.0, false, true, 2.0, 1.5).decisionLog());

        CalibrationSnapshot snapshot = runtime.snapshot();

        assertTrue(snapshot.etaSamples() >= 2);
        assertTrue(snapshot.cancelSamples() >= 2);
        assertTrue(snapshot.postDropSamples() >= 2);
        assertTrue(snapshot.etaResidualMaeMinutes() > 0.0);
        assertTrue(snapshot.cancelCalibrationGap() >= 0.0);
        assertTrue(snapshot.postDropHitCalibrationGap() >= 0.0);
        assertTrue(calibrated.predictedEtaMinutes() != 18.0 || calibrated.predictedCancelRisk() != 0.10
                || calibrated.predictedNextOrderIdleMinutes() != 2.6);
    }

    private ResolvedDecisionSample sample(String id,
                                          double predictedEta,
                                          double actualEta,
                                          boolean actualCancelled,
                                          boolean actualPostDropHit,
                                          double actualEmptyKm,
                                          double actualIdleMinutes) {
        PlanFeatureVector phi = new PlanFeatureVector(0.82, 0.18, 0.72, 0.64, 0.68, 0.70, 0.20, 0.10);
        AdaptiveScoreBreakdown breakdown = AdaptiveScoreBreakdown.of(
                RegimeKey.CLEAR_NORMAL,
                0.52,
                0.05,
                0.47,
                Map.of("on_time_probability", 0.21),
                Map.of("lambda_empty_after", 0.03));
        DecisionLogRecord log = new DecisionLogRecord(
                id,
                "driver-" + id,
                "bundle-" + id,
                CompactPlanType.SINGLE_LOCAL,
                List.of("order-" + id),
                RegimeKey.CLEAR_NORMAL,
                phi,
                breakdown,
                new AdaptiveWeightEngine().snapshot(),
                Instant.parse("2026-04-12T07:00:00Z"),
                0.47,
                0.47,
                predictedEta,
                0.9,
                42000.0,
                0.74,
                0.60,
                0.6,
                2.6,
                0.10,
                phi.onTimeProbability(),
                4.2);
        return new ResolvedDecisionSample(
                log,
                new OutcomeVector(0.92, actualCancelled ? 0.0 : 1.0, 0.88, 0.80, 0.82, actualPostDropHit ? 1.0 : 0.40, actualCancelled ? 0.0 : 1.0),
                DecisionOutcomeStage.AFTER_POST_DROP_WINDOW,
                actualEta,
                actualCancelled,
                actualPostDropHit,
                actualEmptyKm,
                actualIdleMinutes,
                Instant.parse("2026-04-12T07:20:00Z"));
    }
}
