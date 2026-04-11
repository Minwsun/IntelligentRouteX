package com.routechain.api.service;

import com.routechain.config.RouteChainRuntimeProperties;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverOfferCandidate;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.Order;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserOrderingServiceTest {

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void createOrderPersistsOrderAndDispatchesThroughRuntimeBridge() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        RuntimeBridge runtimeBridge = mock(RuntimeBridge.class);
        when(runtimeBridge.dispatchOrder(any())).thenReturn(new DriverOfferBatch(
                "offer-batch-123",
                "ord-any",
                "instant",
                1,
                Instant.now(),
                Instant.now().plusSeconds(30),
                List.of("offer-1"),
                List.of(new DriverOfferCandidate("ord-any", "drv-100", "instant", 0.9, 0.7, 1.2, false, "compact winner"))
        ));
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                runtimeBridge,
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
        verify(runtimeBridge, times(1)).dispatchOrder(any(Order.class));
    }

    @Test
    void cancelOrderWithSameIdempotencyKeyReplaysSingleCancellation() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        RuntimeBridge runtimeBridge = mock(RuntimeBridge.class);
        when(runtimeBridge.dispatchOrder(any())).thenReturn(new DriverOfferBatch(
                "offer-batch-456",
                "ord-any",
                "instant",
                0,
                Instant.now(),
                Instant.now().plusSeconds(30),
                List.of(),
                List.of()
        ));
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        UserOrderingService userOrderingService = new UserOrderingService(
                store,
                store,
                offerStateStore,
                runtimeBridge,
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
