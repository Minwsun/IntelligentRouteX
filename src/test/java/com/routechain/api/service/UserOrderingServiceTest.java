package com.routechain.api.service;

import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserOrderingServiceTest {

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void createOrderPublishesScreenedOfferBatchWhenDriverIsAvailable() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        store.saveDriverSession(new DriverSessionState("drv-100", "device-1", true, 10.775, 106.701, Instant.now(), ""));
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, offerBrokerService);
        UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                orchestratorService,
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                eventPublisher
        );

        var response = userOrderingService.createOrder(new UserOrderRequest(
                "cust-1",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-1"
        ), "idem-1");

        assertNotNull(response.orderId());
        assertFalse(response.offerBatchId().isBlank());
        assertTrue(store.findOrder(response.orderId()).isPresent());
        assertEquals(1, offerBrokerService.offersForDriver("drv-100").size());
        assertEquals(response, userOrderingService.createOrder(new UserOrderRequest(
                "cust-1",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-1"
        ), "idem-1"));
    }

    @Test
    void cancelOrderWithSameIdempotencyKeyReplaysSingleCancellation() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        store.saveDriverSession(new DriverSessionState("drv-200", "device-2", true, 10.775, 106.701, Instant.now(), ""));
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, offerBrokerService);
        UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                orchestratorService,
                new IdempotencyService(store, new RouteChainRuntimeProperties()),
                eventPublisher
        );

        var created = userOrderingService.createOrder(new UserOrderRequest(
                "cust-2",
                "pickup-r1",
                "drop-r9",
                10.776,
                106.700,
                10.780,
                106.710,
                "instant",
                25,
                "merchant-2"
        ), "idem-create-2");

        var cancelled = userOrderingService.cancel(created.orderId(), "changed_plan", "idem-cancel-2").orElseThrow();
        var replayed = userOrderingService.cancel(created.orderId(), "ignored_reason", "idem-cancel-2").orElseThrow();

        assertEquals(cancelled, replayed);
        assertEquals("CANCELLED", store.findOrder(created.orderId()).orElseThrow().getStatus().name());
        assertEquals("changed_plan", store.findOrder(created.orderId()).orElseThrow().getCancellationReason());
    }
}
