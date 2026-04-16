package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.OrderSimilarity;
import com.routechain.v2.context.EtaService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PairSimilarityScorerTest {

    @Test
    void shouldGateInCompactAlignedOrders() {
        PairSimilarityScorer scorer = new PairSimilarityScorer(new EtaService(RouteChainDispatchV2Properties.defaults()));
        Order left = order("ord-sim-1", 10.7768, 106.7010, 10.7820, 106.7070);
        Order right = order("ord-sim-2", 10.7769, 106.7012, 10.7822, 106.7072);
        DispatchV2Request request = new DispatchV2Request(List.of(left, right), List.of(), List.of(), 12, 0.18, WeatherProfile.CLEAR, Instant.parse("2026-04-16T04:30:00Z"), "run");

        OrderSimilarity similarity = scorer.scorePair(left, right, request);

        assertTrue(similarity.gatedIn());
        assertTrue(similarity.similarityScore() > 0.5);
    }

    @Test
    void shouldRejectFarPickupOrders() {
        PairSimilarityScorer scorer = new PairSimilarityScorer(new EtaService(RouteChainDispatchV2Properties.defaults()));
        Order left = order("ord-sim-3", 10.7768, 106.7010, 10.7820, 106.7070);
        Order right = order("ord-sim-4", 10.7900, 106.7200, 10.7920, 106.7250);
        DispatchV2Request request = new DispatchV2Request(List.of(left, right), List.of(), List.of(), 12, 0.18, WeatherProfile.CLEAR, Instant.parse("2026-04-16T04:30:00Z"), "run");

        OrderSimilarity similarity = scorer.scorePair(left, right, request);

        assertTrue(!similarity.gatedIn());
        assertEquals("pickup_distance", similarity.rejectionReason());
    }

    private Order order(String id, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Order order = new Order(
                id,
                "CUS-" + id,
                "R1",
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R2",
                42_000.0,
                55,
                Instant.parse("2026-04-16T04:30:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
