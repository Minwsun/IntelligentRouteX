package com.routechain.backend.offer;

import com.routechain.infra.EventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OfferBrokerServiceTest {

    @AfterEach
    void tearDown() {
        EventBus.getInstance().clear();
    }

    @Test
    void publishOffersCapsFanoutAndReturnsHighestRankedCandidates() {
        OfferBrokerService broker = new OfferBrokerService();
        List<DriverOfferCandidate> candidates = List.of(
                new DriverOfferCandidate("ord-1", "drv-1", "instant", 0.95, 0.92, 0.8, false, "best"),
                new DriverOfferCandidate("ord-1", "drv-2", "instant", 0.90, 0.91, 0.9, false, "second"),
                new DriverOfferCandidate("ord-1", "drv-3", "instant", 0.80, 0.88, 1.0, false, "third"),
                new DriverOfferCandidate("ord-1", "drv-4", "instant", 0.70, 0.84, 1.4, false, "fourth")
        );

        DriverOfferBatch batch = broker.publishOffers("ord-1", "instant", candidates, 10);

        assertNotNull(batch);
        assertEquals(3, batch.fanout());
        assertEquals(3, batch.offerIds().size());
        assertEquals(List.of("drv-1", "drv-2", "drv-3"),
                batch.candidates().stream().map(DriverOfferCandidate::driverId).toList());
    }

    @Test
    void firstAcceptWinsAndSecondAcceptLoses() {
        OfferBrokerService broker = new OfferBrokerService();
        DriverOfferBatch batch = broker.publishOffers("ord-2", "instant", List.of(
                new DriverOfferCandidate("ord-2", "drv-a", "instant", 0.92, 0.91, 0.9, false, "a"),
                new DriverOfferCandidate("ord-2", "drv-b", "instant", 0.91, 0.90, 1.0, false, "b")
        ), 2);

        String firstOfferId = batch.offerIds().get(0);
        String secondOfferId = batch.offerIds().get(1);

        OfferDecision accepted = broker.acceptOffer(firstOfferId, "drv-a");
        OfferDecision lost = broker.acceptOffer(secondOfferId, "drv-b");

        assertEquals(DriverOfferStatus.ACCEPTED, accepted.status());
        assertEquals(DriverOfferStatus.LOST, lost.status());
        assertEquals("offer-lost", lost.reason());
        assertEquals(1, broker.activeReservations().size());
    }
}
