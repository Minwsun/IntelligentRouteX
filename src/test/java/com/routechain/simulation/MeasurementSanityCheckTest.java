package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeasurementSanityCheckTest {

    @Test
    void shouldPassForReasonableLatencyProfile() {
        LatencyBreakdown latency = new LatencyBreakdown(
                22.0, 18.0, 94.0, 118.0,
                6.0, 10.0,
                31.0, 52.0,
                1400.0, 2200.0,
                8.3,
                16,
                12
        );

        MeasurementSanityCheck check = MeasurementSanityCheck.evaluate(latency, 1800.0);

        assertTrue(check.valid());
        assertTrue(check.warnings().stream().anyMatch(w -> w.contains("assignment aging")));
    }

    @Test
    void shouldFailForUnrealisticDispatchLatency() {
        LatencyBreakdown latency = new LatencyBreakdown(
                1500.0, 900.0, 12_500.0, 13_000.0,
                5.0, 8.0,
                15.0, 25.0,
                1400.0, 2200.0,
                1.0,
                8,
                8
        );

        MeasurementSanityCheck check = MeasurementSanityCheck.evaluate(latency, 1600.0);

        assertFalse(check.valid());
    }
}
