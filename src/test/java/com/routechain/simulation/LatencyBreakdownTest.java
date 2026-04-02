package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatencyBreakdownTest {

    @Test
    void shouldComputePercentilesAndThroughputFromSamples() {
        LatencyBreakdown latency = LatencyBreakdown.fromSamples(
                List.of(10L, 20L, 30L, 40L, 50L),
                List.of(5L, 7L, 9L),
                List.of(15L, 25L, 35L),
                List.of(100L, 200L, 300L),
                6.5
        );

        assertEquals(30.0, latency.avgDispatchDecisionLatencyMs(), 1e-9);
        assertEquals(30.0, latency.dispatchP50Ms(), 1e-9);
        assertTrue(latency.dispatchP95Ms() >= 48.0);
        assertEquals(6.5, latency.tickThroughputPerSec(), 1e-9);
        assertEquals(5, latency.dispatchSampleCount());
        assertEquals(3, latency.assignmentSampleCount());
    }
}
