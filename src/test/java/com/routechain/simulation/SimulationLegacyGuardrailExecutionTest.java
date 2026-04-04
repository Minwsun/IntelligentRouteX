package com.routechain.simulation;

import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationLegacyGuardrailExecutionTest {

    @Test
    void omegaLegacyGuardrailPlanShouldSkipPrePickupAugmentation() {
        SimulationEngine engine = new SimulationEngine(314159L);
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);
        engine.setInitialDriverCount(0);
        engine.setDemandMultiplier(0.0);
        engine.setTrafficIntensity(0.18);
        engine.setWeatherProfile(WeatherProfile.CLEAR);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC);

        engine.tickHeadless();

        engine.injectDriver(new GeoPoint(10.7765, 106.7009));
        engine.injectOrder(
                new GeoPoint(10.7895, 106.7145),
                new GeoPoint(10.7960, 106.7200),
                52000,
                80);

        Driver driver = engine.getDrivers().stream()
                .filter(candidate -> candidate.getId().startsWith("DMANUAL-"))
                .findFirst()
                .orElseThrow();

        boolean firstRouteAssigned = false;
        for (int i = 0; i < 90; i++) {
            engine.tickHeadless();
            if (driver.getCurrentOrderCount() == 1
                    && (driver.getState() == DriverState.ROUTE_PENDING
                    || driver.getState() == DriverState.PICKUP_EN_ROUTE
                    || driver.getState() == DriverState.WAITING_PICKUP)) {
                firstRouteAssigned = true;
                break;
            }
        }

        assertTrue(firstRouteAssigned, "First guardrail route should be assigned before the test injects a second order");
        assertFalse(driver.hasCompletedFirstPickup(), "The guardrail route should still be before first pickup");

        engine.injectOrder(
                new GeoPoint(10.7897, 106.7147),
                new GeoPoint(10.7962, 106.7202),
                50000,
                80);

        Order trailingOrder = engine.getActiveOrders().stream()
                .max(Comparator.comparing(Order::getCreatedAt))
                .orElseThrow();
        assertNotNull(trailingOrder.getId());

        boolean stayedSingleBeforePickup = true;
        boolean enteredPickup = false;
        for (int i = 0; i < 120; i++) {
            engine.tickHeadless();
            if (driver.getCurrentOrderCount() > 1 && !driver.hasCompletedFirstPickup()) {
                stayedSingleBeforePickup = false;
                break;
            }
            if (driver.hasCompletedFirstPickup()) {
                enteredPickup = true;
                break;
            }
        }

        assertTrue(enteredPickup, "Driver should eventually reach the first pickup window");
        assertTrue(stayedSingleBeforePickup,
                "Legacy guardrail plans should not be re-augmented by Omega before the first pickup completes");
        assertEquals(1, driver.getCurrentOrderCount(),
                "Driver should still carry only the original guardrail assignment before pickup lock-in");
        assertFalse(driver.getId().equals(trailingOrder.getAssignedDriverId()),
                "The injected trailing order should stay out of the guardrail driver's pre-pickup route");
    }
}
