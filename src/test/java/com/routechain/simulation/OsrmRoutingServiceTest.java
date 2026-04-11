package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OsrmRoutingServiceTest {
    private final OsrmRoutingService routingService = new OsrmRoutingService();

    @AfterEach
    void tearDown() {
        routingService.tearDown();
    }

    @Test
    void shouldParseOsrmGeometryIntoDriverWaypoints() throws Exception {
        Driver driver = new Driver("drv-osrm", "Driver Osrm", new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
        driver.prepareRouteRequest("route-osrm", new GeoPoint(10.7820, 106.7075), DriverState.PICKUP_EN_ROUTE, 0, 0);

        Method parser = OsrmRoutingService.class.getDeclaredMethod(
                "parseAndInjectRoute",
                Driver.class,
                String.class,
                String.class);
        parser.setAccessible(true);
        boolean parsed = (boolean) parser.invoke(
                routingService,
                driver,
                "route-osrm",
                """
                        {
                          "code": "Ok",
                          "routes": [
                            {
                              "geometry": {
                                "coordinates": [
                                  [106.700900, 10.776500],
                                  [106.702100, 10.777400],
                                  [106.707500, 10.782000]
                                ]
                              }
                            }
                          ]
                        }
                        """);

        assertTrue(parsed);
        assertEquals(3, driver.getRemainingRoutePoints().size());
        assertEquals(106.7021, driver.getRemainingRoutePoints().get(1)[0], 1e-6);
        assertEquals(10.7774, driver.getRemainingRoutePoints().get(1)[1], 1e-6);
    }

    @Test
    void shouldInjectHeadlessFallbackOnlyAfterLatencyExpires() {
        routingService.setHeadlessMode(true);
        Driver driver = new Driver("drv-headless", "Driver Headless", new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
        GeoPoint destination = new GeoPoint(10.7820, 106.7075);
        driver.prepareRouteRequest("route-headless", destination, DriverState.PICKUP_EN_ROUTE, 2, 0);

        routingService.requestRouteAsync(driver, driver.getCurrentLocation(), destination, "route-headless", 2);
        routingService.advanceSimulationSubTick();
        assertFalse(driver.hasRouteWaypoints());

        routingService.advanceSimulationSubTick();
        assertTrue(driver.hasRouteWaypoints());
        assertEquals(3, driver.getRemainingRoutePoints().size());
    }

    @Test
    void staleRouteRequestMustNotOverwriteNewerRequest() {
        routingService.setHeadlessMode(true);
        Driver driver = new Driver("drv-stale", "Driver Stale", new GeoPoint(10.7765, 106.7009), "R1", VehicleType.MOTORBIKE);
        GeoPoint oldDestination = new GeoPoint(10.7810, 106.7050);
        GeoPoint newDestination = new GeoPoint(10.7850, 106.7100);

        driver.prepareRouteRequest("route-old", oldDestination, DriverState.PICKUP_EN_ROUTE, 2, 0);
        routingService.requestRouteAsync(driver, driver.getCurrentLocation(), oldDestination, "route-old", 2);

        driver.prepareRouteRequest("route-new", newDestination, DriverState.PICKUP_EN_ROUTE, 1, 0);
        routingService.requestRouteAsync(driver, driver.getCurrentLocation(), newDestination, "route-new", 1);

        routingService.advanceSimulationSubTick();
        List<double[]> afterNewRoute = driver.getRemainingRoutePoints();
        assertFalse(afterNewRoute.isEmpty());
        assertEquals(newDestination.lng(), afterNewRoute.get(afterNewRoute.size() - 1)[0], 1e-6);
        assertEquals(newDestination.lat(), afterNewRoute.get(afterNewRoute.size() - 1)[1], 1e-6);

        routingService.advanceSimulationSubTick();
        List<double[]> afterOldRouteWouldExpire = driver.getRemainingRoutePoints();
        assertEquals(newDestination.lng(), afterOldRouteWouldExpire.get(afterOldRouteWouldExpire.size() - 1)[0], 1e-6);
        assertEquals(newDestination.lat(), afterOldRouteWouldExpire.get(afterOldRouteWouldExpire.size() - 1)[1], 1e-6);
    }
}
