package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayCompareResultRouteMetricsTest {

    @Test
    void summaryShouldExposeRouteAndLandingDeltas() {
        RunReport baseline = createReport("baseline", 0.8, 25.0, 40.0, 20.0);
        RunReport omega = createReport("omega", 2.6, 29.0, 68.0, 1.3);

        ReplayCompareResult compare = ReplayCompareResult.compare(baseline, omega);
        String summary = compare.toSummary();

        assertTrue(summary.contains("3plus="));
        assertTrue(summary.contains("corridor="));
        assertTrue(summary.contains("goodLast="));
        assertTrue(summary.contains("emptyKm="));
        assertTrue(summary.contains("realAssign="));
        assertTrue(summary.contains("wait3="));
        assertTrue(summary.contains("launch3="));
        assertTrue(summary.contains("recover3="));
        assertTrue(summary.contains("sub3="));
        assertTrue(summary.contains("downgrade="));
        assertTrue(summary.contains("postDropHit="));
        assertTrue(summary.contains("tier="));
        assertTrue(summary.contains("prepMae="));
        assertTrue(summary.contains("contGap="));
    }

    private RunReport createReport(String id,
                                   double visibleBundleThreePlusRate,
                                   double deliveryCorridorQuality,
                                   double lastDropGoodZoneRate,
                                   double expectedEmptyKm) {
        Instant now = Instant.parse("2026-03-24T00:00:00Z");
        return new RunReport(
                id,
                id,
                42L,
                now,
                now.plusSeconds(3600),
                1000L,
                100,
                40,
                20.0,
                88.0,
                12.0,
                0.0,
                35.0,
                25.0,
                0.84,
                1.6,
                45000.0,
                4.0,
                75.0,
                2.2,
                3,
                12.0,
                0,
                240000.0,
                0.74,
                4.8,
                0,
                0,
                28000.0,
                visibleBundleThreePlusRate,
                lastDropGoodZoneRate,
                expectedEmptyKm,
                15.0,
                deliveryCorridorQuality,
                0.12,
                82.0,
                0.0,
                18.0,
                82.0,
                6.0,
                4.0,
                18.0,
                1.15,
                1.75,
                1.28,
                41.0,
                1.02,
                1.10,
                0.96,
                new LatencyBreakdown(20.0, 16.0, 92.0, 118.0, 6.0, 10.0, 31.0, 56.0, 1580.0, 2380.0, 7.9, 11, 8),
                new IntelligenceScorecard(0.71, 0.67, 0.62, 0.59, 0.65, 0.61, 0.63, 1.0, 0.73, 0.69, 0.46, 0.23, 0.81, 0.15, "PASSING", "PASSING"),
                new ScenarioAcceptanceResult(id, "instant", "local-production-small-50", true, true, true, true, true, "PASSING", "PASSING", ""),
                "instant",
                Map.of("instant", new ServiceTierMetrics("instant", 100, 82, 82.0, 45.0, 28000.0)),
                new ForecastCalibrationSummary(4.8, 3.4, -0.02, 0.41),
                DispatchRecoveryDecomposition.empty()
        );
    }
}
