package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ControlRoomFrameBuilderTest {

    @Test
    void shouldBuildControlRoomFrameFromSimulationState() {
        SimulationEngine engine = new SimulationEngine(42L);
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.IMMEDIATE);
        engine.setInitialDriverCount(25);
        engine.setDemandMultiplier(1.05);
        engine.setTrafficIntensity(0.42);
        engine.setWeatherProfile(WeatherProfile.CLEAR);
        engine.getOmegaAgent().setDiagnosticLoggingEnabled(false);

        for (int i = 0; i < 60; i++) {
            engine.tickHeadless();
        }

        RunReport report = engine.createRunReport("instant-normal", 42L);
        ControlRoomFrame frame = engine.createControlRoomFrame(report);

        assertEquals(report.runId(), frame.runId());
        assertEquals(report.scenarioName(), frame.scenarioName());
        assertNotNull(frame.routePolicyProfile());
        assertNotNull(frame.forecastDrift());
        assertFalse(frame.cityTwinCells().isEmpty());
        assertFalse(frame.driverFutureValues().isEmpty());
        assertFalse(frame.marketplaceEdges().isEmpty());
        assertFalse(frame.riderCopilot().isEmpty());
        assertFalse(frame.modelPromotions().isEmpty());
        assertNotNull(frame.adminSnapshot());
        assertTrue(!frame.cityTwinCells().get(0).spatialIndex().contains("H3-ready"),
                "City twin should expose a concrete spatial index, not the legacy placeholder");
    }
}
