package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationEngineHeadlessProgressTest {

    @Test
    void headlessRunProducesAssignmentsAndDeliveries() {
        SimulationEngine engine = new SimulationEngine();
        engine.setInitialDriverCount(40);
        engine.setDemandMultiplier(1.25);
        engine.setTrafficIntensity(0.35);
        engine.setWeatherProfile(WeatherProfile.CLEAR);

        for (int i = 0; i < 1500; i++) {
            engine.tickHeadless();
        }

        assertTrue(engine.getTotalAssignments() > 0,
                "Expected the dispatch pipeline to assign at least one order");
        assertTrue(engine.getTotalDelivered() > 0,
                "Expected the simulator to complete at least one delivery in headless mode");
    }
}
