package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRoutePendingTest {

    @Test
    void driverStaysStillWhileRouteIsPendingInHeadlessMode() {
        SimulationEngine engine = new SimulationEngine();
        engine.setInitialDriverCount(0);
        engine.setDemandMultiplier(0.0);
        engine.setTrafficIntensity(0.7);
        engine.setWeatherProfile(WeatherProfile.HEAVY_RAIN);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC);

        engine.tickHeadless();

        GeoPoint driverStart = new GeoPoint(10.7765, 106.7009);
        engine.injectDriver(driverStart);
        engine.injectOrder(
                new GeoPoint(10.7772, 106.7014),
                new GeoPoint(10.7825, 106.7090),
                42000,
                45);

        Driver driver = engine.getDrivers().stream()
                .filter(d -> d.getId().startsWith("DMANUAL-"))
                .findFirst()
                .orElseThrow();

        boolean enteredRoutePending = false;
        for (int i = 0; i < 120; i++) {
            engine.tickHeadless();
            if (driver.getState() == DriverState.ROUTE_PENDING) {
                enteredRoutePending = true;
                break;
            }
        }

        assertTrue(enteredRoutePending, "Expected driver to enter ROUTE_PENDING after dispatch");
        assertNotNull(driver.getPendingTargetLocation(), "Expected a pending target while route is loading");

        GeoPoint pendingLocation = driver.getCurrentLocation();
        engine.tickHeadless();

        assertEquals(DriverState.ROUTE_PENDING, driver.getState(),
                "Driver should remain in ROUTE_PENDING before route activation");
        assertEquals(pendingLocation.lat(), driver.getCurrentLocation().lat(), 1e-9,
                "Driver latitude should remain unchanged while route is pending");
        assertEquals(pendingLocation.lng(), driver.getCurrentLocation().lng(), 1e-9,
                "Driver longitude should remain unchanged while route is pending");
    }
}
