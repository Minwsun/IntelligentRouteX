package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunReportPolicyMetricsTest {

    @Test
    void shouldExposeDerivedAssignmentAndRecoveryMetricsWithoutChangingExistingFields() {
        RunReport report = createReport(18.0, 54.0, 4.0, 3.0);

        assertEquals(82.0, report.realAssignmentRate(), 1e-9);
        assertEquals(18.0, report.holdBehaviorRate(), 1e-9);
        assertEquals(4.0, report.prePickupAugmentRate(), 1e-9);
        assertEquals(54.0 / 82.0 * 100.0, report.cleanWaveRecoveryRate(), 1e-9);
    }

    @Test
    void summaryShouldAppendNewPolicyMetricsWithoutDroppingExistingRouteMetrics() {
        RunReport report = createReport(18.0, 54.0, 4.0, 3.0);

        String summary = report.toSummary();

        assertTrue(summary.contains("corridor="));
        assertTrue(summary.contains("goodLast="));
        assertTrue(summary.contains("emptyKm="));
        assertTrue(summary.contains("wait3="));
        assertTrue(summary.contains("launch3="));
        assertTrue(summary.contains("downgrade="));
        assertTrue(summary.contains("realAssign="));
        assertTrue(summary.contains("augment="));
        assertTrue(summary.contains("holdOnly="));
        assertTrue(summary.contains("recover3="));
    }

    private RunReport createReport(double waitRate,
                                   double launchRate,
                                   double subThreeRate,
                                   double downgradeRate) {
        Instant now = Instant.parse("2026-03-24T00:00:00Z");
        return new RunReport(
                "run-a",
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
                82.0,
                subThreeRate,
                waitRate,
                launchRate,
                downgradeRate,
                4.0,
                waitRate
        );
    }
}
