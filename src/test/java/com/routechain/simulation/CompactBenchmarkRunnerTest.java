package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CompactBenchmarkRunnerTest {

    @Test
    void aggregateCalibrationShouldWeightByObservedSupport() {
        CompactBenchmarkCase highSupport = new CompactBenchmarkCase(
                1L,
                "clear-normal",
                null,
                null,
                null,
                "snapshot-a",
                true,
                List.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                0,
                Map.of(),
                new CalibrationSnapshot(1.0, 0.20, 0.30, 2.0, 3.0, 100L, 80L, 60L),
                0.0,
                0.0,
                0.0);
        CompactBenchmarkCase lowSupport = new CompactBenchmarkCase(
                2L,
                "clear-shortage",
                null,
                null,
                null,
                "snapshot-b",
                true,
                List.of(),
                Map.of(),
                Map.of(),
                0,
                0,
                0,
                Map.of(),
                new CalibrationSnapshot(9.0, 0.80, 0.90, 8.0, 9.0, 1L, 1L, 1L),
                0.0,
                0.0,
                0.0);

        CalibrationSnapshot aggregate = CompactBenchmarkRunner.aggregateCalibration(List.of(highSupport, lowSupport));

        assertEquals((1.0 * 100.0 + 9.0) / 101.0, aggregate.etaResidualMaeMinutes(), 1e-9);
        assertEquals((0.20 * 80.0 + 0.80) / 81.0, aggregate.cancelCalibrationGap(), 1e-9);
        assertEquals((0.30 * 60.0 + 0.90) / 61.0, aggregate.postDropHitCalibrationGap(), 1e-9);
        assertEquals((2.0 * 60.0 + 8.0) / 61.0, aggregate.nextIdleMaeMinutes(), 1e-9);
        assertEquals((3.0 * 60.0 + 9.0) / 61.0, aggregate.emptyKmMae(), 1e-9);
        assertEquals(101L, aggregate.etaSamples());
        assertEquals(81L, aggregate.cancelSamples());
        assertEquals(61L, aggregate.postDropSamples());
    }
}
