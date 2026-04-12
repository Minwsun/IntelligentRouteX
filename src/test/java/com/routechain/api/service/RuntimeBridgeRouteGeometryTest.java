package com.routechain.api.service;

import com.routechain.api.dto.DriverActiveTaskView;
import com.routechain.api.dto.RoutePreviewSourceView;
import com.routechain.api.dto.RouteSourceView;
import com.routechain.api.dto.TripTrackingView;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RuntimeBridgeRouteGeometryTest {

    @AfterEach
    void tearDown() {
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void tripTrackingShouldUseRuntimeGeometryWhenDriverHasLiveWaypoints() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        RuntimeBridge bridge = new RuntimeBridge(
                store,
                store,
                new InMemoryOfferStateStore(),
                org.mockito.Mockito.mock(com.routechain.backend.offer.OfferBrokerService.class),
                org.mockito.Mockito.mock(DispatchOrchestratorService.class));

        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        engine.setInitialDriverCount(0);
        engine.setDemandMultiplier(0.0);
        engine.tickHeadless();
        engine.injectDriver(new GeoPoint(10.7765, 106.7009));

        Driver runtimeDriver = engine.getDrivers().stream()
                .filter(driver -> driver.getId().startsWith("DMANUAL-"))
                .findFirst()
                .orElseThrow();
        runtimeDriver.setRouteWaypoints(List.of(
                new double[]{106.7009, 10.7765},
                new double[]{106.7021, 10.7773},
                new double[]{106.7045, 10.7791}
        ));

        store.saveDriverSession(new DriverSessionState(
                runtimeDriver.getId(),
                "device-1",
                false,
                10.7765,
                106.7009,
                Instant.parse("2026-04-12T03:00:00Z"),
                ""));

        Order order = new Order(
                "ord-route-runtime",
                "cust-1",
                "pickup-r1",
                new GeoPoint(10.7770, 106.7012),
                new GeoPoint(10.7825, 106.7078),
                "drop-r9",
                52000.0,
                35,
                Instant.parse("2026-04-12T03:00:00Z"));
        order.setServiceType("instant");
        order.assignDriver(runtimeDriver.getId(), Instant.parse("2026-04-12T03:01:00Z"));
        store.saveOrder(order);

        TripTrackingView tracking = bridge.tripTracking(order.getId()).orElseThrow();

        assertEquals(RouteSourceView.RUNTIME_OSRM, tracking.routeSource());
        assertEquals(RouteSourceView.RUNTIME_OSRM, tracking.activeRouteSource());
        assertEquals(3, tracking.routePolyline().size());
        assertEquals(10.7773, tracking.routePolyline().get(1).lat(), 1e-6);
        assertEquals(106.7021, tracking.routePolyline().get(1).lng(), 1e-6);
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, tracking.remainingRoutePreviewSource());
        assertEquals(2, tracking.remainingRoutePreviewPolyline().size());
        assertEquals(10.7765, tracking.runtimeDriverLocation().lat(), 1e-6);
        assertNotNull(tracking.routeGeneratedAt());
    }

    @Test
    void activeTaskShouldFallBackAndLabelApproximateRouteWhenRuntimeGeometryIsMissing() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        RuntimeBridge bridge = new RuntimeBridge(
                store,
                store,
                new InMemoryOfferStateStore(),
                org.mockito.Mockito.mock(com.routechain.backend.offer.OfferBrokerService.class),
                org.mockito.Mockito.mock(DispatchOrchestratorService.class));

        store.saveDriverSession(new DriverSessionState(
                "drv-fallback",
                "device-2",
                false,
                10.7762,
                106.7008,
                Instant.parse("2026-04-12T03:10:00Z"),
                ""));

        Order order = new Order(
                "ord-route-fallback",
                "cust-2",
                "pickup-r1",
                new GeoPoint(10.7768, 106.7010),
                new GeoPoint(10.7818, 106.7064),
                "drop-r9",
                46000.0,
                30,
                Instant.parse("2026-04-12T03:10:00Z"));
        order.setServiceType("instant");
        order.assignDriver("drv-fallback", Instant.parse("2026-04-12T03:11:00Z"));
        order.markPickupStarted(Instant.parse("2026-04-12T03:12:00Z"));
        store.saveOrder(order);

        DriverActiveTaskView activeTask = bridge.activeTask("drv-fallback").orElseThrow();

        assertEquals(RouteSourceView.RUNTIME_FALLBACK, activeTask.routeSource());
        assertFalse(activeTask.routePolyline().isEmpty());
        assertEquals("driver", activeTask.routePolyline().get(0).label());
        assertEquals(2, activeTask.routePolyline().size());
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, activeTask.remainingRoutePreviewSource());
        assertEquals(2, activeTask.remainingRoutePreviewPolyline().size());
        assertNotNull(activeTask.routeGeneratedAt());
    }
}
