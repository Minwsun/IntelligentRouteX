package com.routechain.api.service;

import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.service.OrderLifecycleFactService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.RouteCoreRuntime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DispatchOrchestratorServiceTest {

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
        RouteCoreRuntime.stopLiveEngine();
    }

    @Test
    void shouldPromoteCompactRuntimeWinnerIntoOfferBatch() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, new OrderLifecycleFactService(store), eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService);

        store.saveDriverSession(new DriverSessionState(
                "drv-near", "device-near", true, 10.7762, 106.7008, Instant.now(), ""));
        store.saveDriverSession(new DriverSessionState(
                "drv-far", "device-far", true, 10.8015, 106.7310, Instant.now(), ""));

        Order order = new Order(
                "ord-runtime",
                "cust-runtime",
                "pickup-r1",
                new GeoPoint(10.7760, 106.7005),
                new GeoPoint(10.7820, 106.7080),
                "drop-r9",
                52000.0,
                35,
                Instant.now());
        order.setServiceType("instant");

        DriverOfferBatch batch = orchestratorService.publishOffersForOrder(order);

        assertFalse(batch.candidates().isEmpty());
        assertEquals("drv-near", batch.candidates().get(0).driverId());
        assertTrue(batch.candidates().get(0).rationale().contains("compact_runtime_candidate"));
    }

    @Test
    void shouldStillPublishRuntimeWinnerWhenHeuristicScreenFindsNoCandidate() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, new OrderLifecycleFactService(store), eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService);

        store.saveDriverSession(new DriverSessionState(
                "drv-runtime-only", "device-runtime-only", true, 10.7769, 106.7009, Instant.now(), ""));

        Order order = new Order(
                "ord-runtime-fallback",
                "cust-runtime",
                "pickup-r1",
                new GeoPoint(10.7763, 106.6706),
                new GeoPoint(10.7594, 106.6963),
                "drop-r9",
                48000.0,
                30,
                Instant.now());
        order.setServiceType("instant");

        DriverOfferBatch batch = orchestratorService.publishOffersForOrder(order);

        assertFalse(batch.candidates().isEmpty());
        assertEquals("drv-runtime-only", batch.candidates().get(0).driverId());
        assertTrue(batch.candidates().get(0).rationale().contains("compact_runtime_candidate"));
    }

    @Test
    void shouldReofferSingleOrderIntoNextWaveAfterFirstWaveDeclines() {
        InMemoryOperationalStore store = new InMemoryOperationalStore();
        InMemoryOfferStateStore offerStateStore = new InMemoryOfferStateStore();
        OperationalEventPublisher eventPublisher = new OperationalEventPublisher(store);
        OfferBrokerService offerBrokerService = new OfferBrokerService(offerStateStore, new OrderLifecycleFactService(store), eventPublisher);
        DispatchOrchestratorService orchestratorService = new DispatchOrchestratorService(store, store, offerStateStore, offerBrokerService);

        store.saveDriverSession(new DriverSessionState(
                "drv-wave-1", "device-wave-1", true, 10.7761, 106.7007, Instant.now(), ""));
        store.saveDriverSession(new DriverSessionState(
                "drv-wave-2", "device-wave-2", true, 10.7785, 106.7035, Instant.now(), ""));

        Order order = new Order(
                "ord-reoffer",
                "cust-reoffer",
                "pickup-r1",
                new GeoPoint(10.7760, 106.7005),
                new GeoPoint(10.7820, 106.7080),
                "drop-r9",
                52000.0,
                35,
                Instant.now());
        order.setServiceType("instant");
        store.saveOrder(order);

        DriverOfferBatch firstBatch = orchestratorService.publishOffersForOrder(order);
        assertEquals(1, firstBatch.wave());
        assertEquals(1, firstBatch.offerIds().size());

        offerBrokerService.declineOffer(firstBatch.offerIds().getFirst(), "drv-wave-1", "busy");

        var batches = offerStateStore.batchesForOrder(order.getId());
        assertEquals(2, batches.size());
        DriverOfferBatch secondBatch = batches.getLast();
        assertEquals(2, secondBatch.wave());
        assertEquals(firstBatch.offerBatchId(), secondBatch.previousBatchId());
        assertNotNull(secondBatch.createdAt());
        assertEquals(List.of("drv-wave-2"), secondBatch.candidates().stream().map(candidate -> candidate.driverId()).toList());
    }
}
