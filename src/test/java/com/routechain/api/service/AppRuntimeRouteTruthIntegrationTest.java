package com.routechain.api.service;

import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.api.dto.RoutePreviewSourceView;
import com.routechain.api.dto.RouteSourceView;
import com.routechain.api.dto.TripTrackingView;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.data.config.RouteChainPersistenceProperties;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.port.DriverPresenceStore;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.Driver;
import com.routechain.infra.EventBus;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppRuntimeRouteTruthIntegrationTest {

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clear();
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void acceptedAppTaskShouldExposeRuntimeOsrmRouteToRiderAndDriver() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, offerBrokerService);
        RuntimeBridge runtimeBridge = new RuntimeBridge(
                store,
                store,
                offerStateStore,
                offerBrokerService,
                orchestratorService);
        UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                runtimeBridge,
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                eventPublisher);
        DriverOperationsService driverOperationsService = new DriverOperationsService(
                store,
                new InMemoryDriverPresenceStore(),
                store,
                offerBrokerService,
                runtimeBridge,
                Mockito.mock(OpsArtifactService.class),
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                eventPublisher,
                new RouteChainPersistenceProperties());

        RouteCoreRuntime.liveEngine().reset();
        driverOperationsService.login(new DriverLoginRequest("drv-app-route", "device-app-route", 10.7765, 106.7009));

        userOrderingService.createOrder(new UserOrderRequest(
                "cust-app-route",
                "pickup-r1",
                "drop-r9",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-1"
        ), "idem-order-1");

        var offers = driverOperationsService.offers("drv-app-route");
        assertEquals(1, offers.size(), "Runtime-authoritative app dispatch should publish only the compact winner");

        driverOperationsService.accept("drv-app-route", offers.getFirst().offerId(), "idem-accept-1");

        Driver runtimeDriver = RouteCoreRuntime.liveEngine().getDrivers().stream()
                .filter(driver -> "drv-app-route".equals(driver.getId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(runtimeDriver.getRouteRequestId(), "Accepting an app offer should materialize a live runtime route request");

        runtimeDriver.setRouteWaypoints(
                List.of(
                        new double[]{106.7009, 10.7765},
                        new double[]{106.7024, 10.7778},
                        new double[]{106.7079, 10.7826}
                ),
                Driver.RouteGeometrySource.OSRM,
                Instant.parse("2026-04-12T02:05:00Z"));

        String orderId = offers.getFirst().orderId();
        TripTrackingView trackingView = runtimeBridge.tripTracking(orderId).orElseThrow();
        var activeTask = runtimeBridge.activeTask("drv-app-route").orElseThrow();

        assertEquals(RouteSourceView.RUNTIME_OSRM, trackingView.routeSource());
        assertEquals(RouteSourceView.RUNTIME_OSRM, activeTask.routeSource());
        assertFalse(trackingView.routePolyline().isEmpty());
        assertFalse(activeTask.routePolyline().isEmpty());
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, trackingView.remainingRoutePreviewSource());
        assertEquals(RoutePreviewSourceView.RUNTIME_PREVIEW, activeTask.remainingRoutePreviewSource());
        assertEquals("2026-04-12T02:05:00Z", trackingView.routeGeneratedAt());
        assertEquals("2026-04-12T02:05:00Z", activeTask.routeGeneratedAt());
        assertNotNull(trackingView.runtimeDriverLocation());
        assertNotNull(activeTask.runtimeDriverLocation());
        assertEquals(OrderLifecycleStage.ACCEPTED, trackingView.lifecycleStage());
        assertEquals(OrderLifecycleStage.ACCEPTED, activeTask.lifecycleStage());

        driverOperationsService.updateTaskStatus("drv-app-route", "task-" + orderId, new DriverTaskStatusUpdate("PICKED_UP"));
        Optional<TripTrackingView> postPickupTracking = runtimeBridge.tripTracking(orderId);
        assertTrue(postPickupTracking.isPresent());
        assertEquals("PICKED_UP", postPickupTracking.get().status());
        assertEquals(OrderLifecycleStage.PICKED_UP, postPickupTracking.get().lifecycleStage());
        assertEquals(RoutePreviewSourceView.NONE, postPickupTracking.get().remainingRoutePreviewSource());
    }

    private static final class InMemoryDriverPresenceStore implements DriverPresenceStore {
        @Override
        public void heartbeat(String driverId, double lat, double lng, boolean available, Duration ttl) {
        }

        @Override
        public Optional<PresenceSnapshot> find(String driverId) {
            return Optional.empty();
        }
    }
}
