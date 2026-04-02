package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

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
        assertTrue(summary.contains("postDropHit="));
        assertTrue(summary.contains("tier="));
        assertTrue(summary.contains("prepMae="));
        assertTrue(summary.contains("contGap="));
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
                waitRate,
                1.25,
                1.90,
                1.35,
                44.0,
                1.10,
                1.20,
                1.05,
                new LatencyBreakdown(18.0, 14.0, 86.0, 112.0, 5.0, 9.0, 30.0, 54.0, 1600.0, 2400.0, 8.2, 10, 8),
                new IntelligenceScorecard(0.70, 0.66, 0.61, 0.58, 0.64, 0.60, 0.62, 2.0, 0.72, 0.68, 0.44, 0.24, 0.80, 0.15, "PASSING", "PASSING"),
                new ScenarioAcceptanceResult("scenario-a", "instant", "local-production-small-50", true, true, true, true, true, "PASSING", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, 82, 82.0, 42.0, 26000.0)),
                new ForecastCalibrationSummary(4.5, 3.2, -0.04, 0.44),
                DispatchRecoveryDecomposition.empty()
        );
    }
}
