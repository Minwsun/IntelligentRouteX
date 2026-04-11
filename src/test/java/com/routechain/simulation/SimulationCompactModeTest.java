package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.core.CompactEvidenceBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationCompactModeTest {

    @Test
    void compactModeShouldAssignManualOrderToAvailableDriver() {
        SimulationEngine engine = new SimulationEngine(20260411L);
        engine.setDispatchMode(SimulationEngine.DispatchMode.COMPACT);
        engine.setInitialDriverCount(0);
        engine.setDemandMultiplier(0.0);
        engine.setTrafficIntensity(0.22);
        engine.setWeatherProfile(WeatherProfile.CLEAR);

        engine.tickHeadless();
        engine.injectDriver(new GeoPoint(10.7765, 106.7009));
        engine.injectOrder(
                new GeoPoint(10.7770, 106.7012),
                new GeoPoint(10.7825, 106.7078),
                52000,
                70);

        Driver driver = engine.getDrivers().stream()
                .filter(candidate -> candidate.getId().startsWith("DMANUAL-"))
                .findFirst()
                .orElseThrow();

        boolean assigned = false;
        for (int i = 0; i < 30; i++) {
            engine.tickHeadless();
            if (driver.getCurrentOrderCount() > 0
                    && (driver.getState() == DriverState.ROUTE_PENDING
                    || driver.getState() == DriverState.PICKUP_EN_ROUTE
                    || driver.getState() == DriverState.WAITING_PICKUP)) {
                assigned = true;
                break;
            }
        }

        assertTrue(assigned, "Compact mode should produce a real assignment through the live simulation engine");
        assertFalse(engine.getActiveOrders().isEmpty(), "The assigned order should remain active until pickup/delivery progress continues");
        CompactEvidenceBundle evidenceAfterAssignment = engine.getLatestCompactEvidence();
        assertFalse(evidenceAfterAssignment.explanations().isEmpty(),
                "Compact mode should expose a real explanation bundle after assignment");

        boolean resolved = false;
        for (int i = 0; i < 240; i++) {
            engine.tickHeadless();
            if (engine.getLatestCompactEvidence().latestResolution() != null) {
                resolved = true;
                break;
            }
        }

        assertTrue(resolved, "Compact mode should resolve delayed learning from real runtime outcomes");
        assertNotNull(engine.getCurrentCompactWeightSnapshot(),
                "Compact mode should keep a readable runtime weight snapshot");
    }
}
