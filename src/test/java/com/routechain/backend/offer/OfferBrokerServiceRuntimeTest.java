package com.routechain.backend.offer;

import com.routechain.api.store.InMemoryOperationalStore;
import com.routechain.data.memory.InMemoryOfferRuntimeStore;
import com.routechain.data.memory.InMemoryOfferStateStore;
import com.routechain.data.service.OperationalEventPublisher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OfferBrokerServiceRuntimeTest {

    @AfterEach
    void tearDown() {
        com.routechain.infra.EventBus.getInstance().clear();
    }

    @Test
    void publishOffersSkipsDriversInCooldownAndUsesActualFanout() {
        InMemoryOfferStateStore stateStore = new InMemoryOfferStateStore();
        InMemoryOfferRuntimeStore runtimeStore = new InMemoryOfferRuntimeStore();
        OfferBrokerService broker = new OfferBrokerService(
                stateStore,
                runtimeStore,
                new OperationalEventPublisher(new InMemoryOperationalStore()),
                Duration.ofSeconds(30),
                Duration.ofSeconds(45)
        );
        runtimeStore.markDriverCooldown("drv-cooldown", Instant.now().plusSeconds(30));

        DriverOfferBatch batch = broker.publishOffers("ord-runtime", "instant", List.of(
                new DriverOfferCandidate("ord-runtime", "drv-cooldown", "instant", 0.98, 0.95, 0.7, false, "cooldown"),
                new DriverOfferCandidate("ord-runtime", "drv-ready", "instant", 0.90, 0.90, 0.8, false, "ready")
        ), 2);

        assertEquals(1, batch.fanout());
        assertEquals(List.of("drv-ready"), batch.candidates().stream().map(DriverOfferCandidate::driverId).toList());
        assertEquals(1, batch.offerIds().size());
    }

    @Test
    void acceptingOfferAfterRuntimeExpiryMarksItExpired() {
        InMemoryOfferStateStore stateStore = new InMemoryOfferStateStore();
        InMemoryOfferRuntimeStore runtimeStore = new InMemoryOfferRuntimeStore();
        OfferBrokerService broker = new OfferBrokerService(
                stateStore,
                runtimeStore,
                new OperationalEventPublisher(new InMemoryOperationalStore()),
                Duration.ofSeconds(30),
                Duration.ofSeconds(45)
        );

        DriverOfferBatch batch = broker.publishOffers("ord-expire", "instant", List.of(
                new DriverOfferCandidate("ord-expire", "drv-expire", "instant", 0.93, 0.92, 0.6, false, "single")
        ), 1);
        String offerId = batch.offerIds().getFirst();
        runtimeStore.clearOffer(offerId);

        OfferDecision decision = broker.acceptOffer(offerId, "drv-expire");

        assertEquals(DriverOfferStatus.EXPIRED, decision.status());
        assertEquals("offer-expired", decision.reason());
        assertTrue(broker.offersForDriver("drv-expire").stream()
                .filter(view -> view.offerId().equals(offerId))
                .anyMatch(view -> view.status() == DriverOfferStatus.EXPIRED));
    }
}
