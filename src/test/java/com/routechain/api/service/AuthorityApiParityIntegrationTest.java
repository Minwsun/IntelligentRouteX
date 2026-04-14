package com.routechain.api.service;

import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.OrderOfferStage;
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
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.infra.EventBus;
import com.routechain.infra.RouteCoreRuntime;
import com.routechain.simulation.SimulationEngine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AuthorityApiParityIntegrationTest {

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clear();
        SimulationEngine engine = RouteCoreRuntime.liveEngine();
        engine.reset();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void userDriverMerchantAndOpsShouldSeeSameAcceptedAuthorityState() {
        TestContext context = new TestContext();
        context.driverOperationsService.login(new DriverLoginRequest("drv-authority", "device-authority", 10.7765, 106.7009));

        var created = context.userOrderingService.createOrder(new UserOrderRequest(
                "cust-authority",
                "pickup-r1",
                "drop-r9",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-authority"
        ), "idem-authority-order");

        String offerId = context.driverOperationsService.offers("drv-authority").getFirst().offerId();
        context.driverOperationsService.accept("drv-authority", offerId, "idem-authority-accept");

        TripTrackingView tracking = context.runtimeBridge.tripTracking(created.orderId()).orElseThrow();
        var activeTask = context.runtimeBridge.activeTask("drv-authority").orElseThrow();
        var merchantOrder = context.runtimeBridge.merchantOrders("merchant-authority").getFirst();
        var opsOrder = context.runtimeBridge.opsRealtimeSnapshot().activeOrders().stream()
                .filter(view -> created.orderId().equals(view.orderId()))
                .findFirst()
                .orElseThrow();

        assertEquals(OrderLifecycleStage.ACCEPTED, tracking.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), activeTask.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), merchantOrder.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), opsOrder.lifecycleStage());

        assertEquals(OrderOfferStage.LOCKED_ASSIGNMENT, tracking.offerSnapshot().stage());
        assertEquals(tracking.offerSnapshot().stage(), merchantOrder.offerSnapshot().stage());
        assertEquals(tracking.offerSnapshot().stage(), opsOrder.offerSnapshot().stage());

        assertNotNull(tracking.offerSnapshot().assignmentLock());
        assertEquals("drv-authority", tracking.offerSnapshot().assignmentLock().driverId());
        assertEquals("drv-authority", merchantOrder.offerSnapshot().assignmentLock().driverId());
        assertEquals("drv-authority", opsOrder.offerSnapshot().assignmentLock().driverId());
    }

    @Test
    void userDriverMerchantAndOpsShouldSeeSameExecutionTruthAfterPickup() {
        TestContext context = new TestContext();
        context.driverOperationsService.login(new DriverLoginRequest("drv-execution", "device-execution", 10.7765, 106.7009));

        var created = context.userOrderingService.createOrder(new UserOrderRequest(
                "cust-execution",
                "pickup-r1",
                "drop-r9",
                10.7770,
                106.7012,
                10.7826,
                106.7079,
                "instant",
                35,
                "merchant-execution"
        ), "idem-execution-order");

        String offerId = context.driverOperationsService.offers("drv-execution").getFirst().offerId();
        context.driverOperationsService.accept("drv-execution", offerId, "idem-execution-accept");
        context.driverOperationsService.updateTaskStatus("drv-execution", "task-" + created.orderId(),
                new DriverTaskStatusUpdate("picked_up"));

        TripTrackingView tracking = context.runtimeBridge.tripTracking(created.orderId()).orElseThrow();
        var activeTask = context.runtimeBridge.activeTask("drv-execution").orElseThrow();
        var merchantOrder = context.runtimeBridge.merchantOrders("merchant-execution").getFirst();
        var opsOrder = context.runtimeBridge.opsRealtimeSnapshot().activeOrders().stream()
                .filter(view -> created.orderId().equals(view.orderId()))
                .findFirst()
                .orElseThrow();

        assertEquals(OrderLifecycleStage.PICKED_UP, tracking.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), activeTask.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), merchantOrder.lifecycleStage());
        assertEquals(tracking.lifecycleStage(), opsOrder.lifecycleStage());

        assertFalse(tracking.lifecycleHistory().isEmpty());
        assertEquals(OrderLifecycleStage.PICKED_UP, tracking.lifecycleHistory().getLast().stage());
    }

    private static final class TestContext {
        private final InMemoryOperationalStore store = new InMemoryOperationalStore();
        private final InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        private final OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        private final OrderLifecycleFactService lifecycleFactService = new OrderLifecycleFactService(store);
        private final OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, lifecycleFactService, eventPublisher);
        private final OrderLifecycleProjectionService projectionService = new OrderLifecycleProjectionService(store, store, offerStateStore);
        private final DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService);
        private final SingleOrderDispatchExpiryCoordinator expiryCoordinator = new SingleOrderDispatchExpiryCoordinator(
                offerBrokerService,
                store,
                store);
        private final RuntimeBridge runtimeBridge = new RuntimeBridge(
                store,
                store,
                offerStateStore,
                offerBrokerService,
                orchestratorService,
                projectionService,
                expiryCoordinator);
        private final UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                offerBrokerService,
                runtimeBridge,
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                lifecycleFactService,
                eventPublisher);
        private final DriverOperationsService driverOperationsService = new DriverOperationsService(
                store,
                new InMemoryDriverPresenceStore(),
                store,
                offerBrokerService,
                runtimeBridge,
                Mockito.mock(OpsArtifactService.class),
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                lifecycleFactService,
                eventPublisher,
                new RouteChainPersistenceProperties());
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
