package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCompareResultPolicyMetricsTest {

    @Test
    void shouldCompareAssignmentWaitLaunchAndRecoverySignals() {
        RunReport baseline = createReport("baseline", 72.0, 28.0, 40.0, 12.0, 9.0, 3.0, 22.0);
        RunReport omega = createReport("omega", 86.0, 14.0, 62.0, 5.0, 4.0, 11.0, 9.0);

        ReplayCompareResult compare = ReplayCompareResult.compare(baseline, omega);

        assertEquals(14.0, compare.realAssignmentRateDelta(), 1e-9);
        assertEquals(-14.0, compare.waveAssemblyWaitRateDelta(), 1e-9);
        assertEquals(22.0, compare.thirdOrderLaunchRateDelta(), 1e-9);
        assertEquals(-7.0, compare.selectedSubThreeRateInCleanRegimeDelta(), 1e-9);
        assertEquals(-5.0, compare.stressDowngradeRateDelta(), 1e-9);
        assertTrue(compare.cleanWaveRecoveryRateDelta() > 0.0);
        assertTrue(compare.improvesRecoveryBehavior());
    }

    @Test
    void summaryShouldExposeRecoveryDeltasForHarnessOutput() {
        RunReport baseline = createReport("baseline", 72.0, 28.0, 40.0, 12.0, 9.0, 3.0, 22.0);
        RunReport omega = createReport("omega", 86.0, 14.0, 62.0, 5.0, 4.0, 11.0, 9.0);

        String summary = ReplayCompareResult.compare(baseline, omega).toSummary();

        assertTrue(summary.contains("realAssign="));
        assertTrue(summary.contains("wait3="));
        assertTrue(summary.contains("launch3="));
        assertTrue(summary.contains("recover3="));
        assertTrue(summary.contains("sub3="));
        assertTrue(summary.contains("downgrade="));
        assertTrue(summary.contains("tier="));
        assertTrue(summary.contains("prepMae="));
        assertTrue(summary.contains("contGap="));
    }

    private RunReport createReport(String runId,
                                   double realAssignmentRate,
                                   double waitRate,
                                   double launchRate,
                                   double subThreeRate,
                                   double downgradeRate,
                                   double augmentRate,
                                   double holdOnlyRate) {
        Instant now = Instant.parse("2026-03-24T00:00:00Z");
        return new RunReport(
                runId,
                "scenario-a",
                7L,
                now,
                now.plusSeconds(1800),
                900L,
                100,
                40,
                22.0,
                87.0,
                10.0,
                0.0,
                30.0,
                20.0,
                0.82,
                1.5,
                42000.0,
                5.0,
                72.0,
                2.1,
                3,
                8.0,
                0,
                180000.0,
                0.72,
                4.5,
                0,
                0,
                26000.0,
                3.8,
                55.0,
                1.2,
                12.0,
                29.0,
                0.10,
                realAssignmentRate,
                subThreeRate,
                waitRate,
                launchRate,
                downgradeRate,
                augmentRate,
                holdOnlyRate,
                1.20,
                1.80,
                1.30,
                45.0,
                1.05,
                1.15,
                0.98,
                new LatencyBreakdown(19.0, 15.0, 88.0, 116.0, 5.0, 9.0, 29.0, 52.0, 1550.0, 2350.0, 8.0, 10, 8),
                new IntelligenceScorecard(0.69, 0.65, 0.60, 0.57, 0.63, 0.59, 0.61, 2.0, 0.70, 0.67, 0.45, 0.25, 0.79, 0.16, "PASSING", "PASSING"),
                new ScenarioAcceptanceResult("scenario-a", "instant", "local-production-small-50", true, true, true, true, true, "PASSING", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, (int) Math.round(realAssignmentRate), realAssignmentRate, 41.0, 26000.0)),
                new ForecastCalibrationSummary(4.5, 3.0, -0.03, 0.45),
                DispatchRecoveryDecomposition.empty()
        );
    }
}
