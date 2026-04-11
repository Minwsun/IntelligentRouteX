package com.routechain.infra;

import com.routechain.simulation.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

class RouteCoreRuntimeTest {

    @AfterEach
    void stopLiveEngine() {
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void shouldExposeSingleSharedLiveEngine() {
        SimulationEngine first = RouteCoreRuntime.liveEngine();
        SimulationEngine second = RouteCoreRuntime.liveEngine();

        assertSame(first, second);
    }
}
