package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                holdOnlyRate
        );
    }
}
