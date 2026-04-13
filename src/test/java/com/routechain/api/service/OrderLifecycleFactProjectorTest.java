package com.routechain.api.service;

import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.OrderOfferStage;
import com.routechain.data.model.OrderLifecycleFact;
import com.routechain.data.model.OrderLifecycleFactType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderLifecycleFactProjectorTest {

    @Test
    void shouldReplayAcceptedExecutionFlowFromFactsOnly() {
        Instant base = Instant.parse("2026-04-13T03:00:00Z");
        OrderLifecycleProjection projection = OrderLifecycleFactProjector.project(List.of(
                fact("fact-1", OrderLifecycleFactType.ORDER_CREATED, base, Map.of()),
                fact("fact-2", OrderLifecycleFactType.OFFER_BATCH_CREATED, base.plusSeconds(1), Map.of(
                        "offerBatchId", "batch-1",
                        "wave", 1,
                        "fanout", 1,
                        "expiresAt", base.plusSeconds(30).toString())),
                fact("fact-3", OrderLifecycleFactType.OFFERS_PUBLISHED, base.plusSeconds(1), Map.of(
                        "offerBatchId", "batch-1",
                        "wave", 1,
                        "fanout", 1)),
                fact("fact-4", OrderLifecycleFactType.OFFER_ACCEPTED, base.plusSeconds(3), Map.of(
                        "offerId", "offer-1",
                        "offerBatchId", "batch-1",
                        "driverId", "drv-1",
                        "reservationVersion", 1L,
                        "reason", "accepted")),
                fact("fact-5", OrderLifecycleFactType.ASSIGNMENT_LOCKED, base.plusSeconds(4), Map.of(
                        "reservationId", "res-1",
                        "offerId", "offer-1",
                        "offerBatchId", "batch-1",
                        "driverId", "drv-1",
                        "reservationVersion", 1L,
                        "status", "ACCEPTED",
                        "expiresAt", base.plusSeconds(30).toString())),
                fact("fact-6", OrderLifecycleFactType.ARRIVED_PICKUP, base.plusSeconds(8), Map.of()),
                fact("fact-7", OrderLifecycleFactType.PICKED_UP, base.plusSeconds(12), Map.of())
        ));

        assertEquals(OrderLifecycleStage.PICKED_UP, projection.lifecycleStage());
        assertEquals(OrderOfferStage.LOCKED_ASSIGNMENT, projection.offerSnapshot().stage());
        assertEquals("drv-1", projection.offerSnapshot().assignmentLock().driverId());
        assertEquals(7, projection.lifecycleHistory().size());
    }

    @Test
    void shouldReplayReofferWaveAndKeepOrderInOfferedStageWithoutLock() {
        Instant base = Instant.parse("2026-04-13T04:00:00Z");
        OrderLifecycleProjection projection = OrderLifecycleFactProjector.project(List.of(
                fact("fact-1", OrderLifecycleFactType.ORDER_CREATED, base, Map.of()),
                fact("fact-2", OrderLifecycleFactType.OFFER_BATCH_CREATED, base.plusSeconds(1), Map.of(
                        "offerBatchId", "batch-1",
                        "wave", 1,
                        "fanout", 1,
                        "expiresAt", base.plusSeconds(30).toString())),
                fact("fact-3", OrderLifecycleFactType.OFFER_DECLINED, base.plusSeconds(5), Map.of(
                        "offerId", "offer-1",
                        "offerBatchId", "batch-1",
                        "driverId", "drv-1",
                        "reason", "busy")),
                fact("fact-4", OrderLifecycleFactType.OFFER_BATCH_CLOSED, base.plusSeconds(5), Map.of(
                        "offerBatchId", "batch-1",
                        "wave", 1,
                        "closeReason", "declined")),
                fact("fact-5", OrderLifecycleFactType.ORDER_REOFFERED, base.plusSeconds(7), Map.of(
                        "previousBatchId", "batch-1",
                        "nextBatchId", "batch-2",
                        "wave", 2,
                        "reason", "reoffer_wave_created")),
                fact("fact-6", OrderLifecycleFactType.OFFER_BATCH_CREATED, base.plusSeconds(7), Map.of(
                        "offerBatchId", "batch-2",
                        "wave", 2,
                        "previousBatchId", "batch-1",
                        "fanout", 1,
                        "expiresAt", base.plusSeconds(45).toString()))
        ));

        assertEquals(OrderLifecycleStage.OFFERED, projection.lifecycleStage());
        assertEquals(OrderOfferStage.REOFFERED, projection.offerSnapshot().stage());
        assertEquals("batch-2", projection.offerSnapshot().activeBatchId());
        assertTrue(projection.offerSnapshot().pendingOffersPresent());
    }

    private OrderLifecycleFact fact(String factId,
                                    OrderLifecycleFactType type,
                                    Instant recordedAt,
                                    Map<String, Object> payload) {
        return new OrderLifecycleFact(
                factId,
                "ord-projection",
                type,
                recordedAt,
                "SYSTEM",
                "test",
                "",
                "",
                com.routechain.infra.GsonSupport.compact().toJson(payload)
        );
    }
}
