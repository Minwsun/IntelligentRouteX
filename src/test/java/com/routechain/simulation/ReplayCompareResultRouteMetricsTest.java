package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.time.Instant;

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
                1.02,
                1.10,
                0.96,
                DispatchRecoveryDecomposition.empty()
        );
    }
}
