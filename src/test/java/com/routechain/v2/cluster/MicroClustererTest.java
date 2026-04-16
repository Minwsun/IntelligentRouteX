package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.v2.OrderSimilarity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroClustererTest {

    @Test
    void shouldSplitDistantOrdersIntoSeparateClusters() {
        Order a = order("ord-cluster-1", "R1", 10.7768, 106.7010, 10.7820, 106.7070);
        Order b = order("ord-cluster-2", "R1", 10.7769, 106.7012, 10.7822, 106.7072);
        Order c = order("ord-cluster-3", "R2", 10.7900, 106.7200, 10.7920, 106.7250);
        BufferedOrderWindow window = new BufferedOrderWindow("buffer-1", Instant.parse("2026-04-16T04:40:00Z"), List.of(a, b, c), List.of());
        PairSimilarityGraph graph = new PairSimilarityGraph(List.of(
                similarity(a, b, 0.82),
                similarity(a, c, 0.0),
                similarity(b, c, 0.0)));

        MicroClusterer clusterer = new MicroClusterer(RouteChainDispatchV2Properties.defaults().getCluster());
        List<MicroCluster> clusters = clusterer.cluster(window, graph);

        assertEquals(2, clusters.size());
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.allOrders().size() == 2));
        assertTrue(clusters.stream().anyMatch(cluster -> cluster.allOrders().size() == 1));
    }

    private OrderSimilarity similarity(Order left, Order right, double score) {
        return new OrderSimilarity(
                left.getId(),
                right.getId(),
                score > 0.0,
                score > 0.0 ? "" : "pickup_distance",
                0.2,
                1.0,
                5.0,
                1.05,
                OrderSimilarity.GeometryClass.STRAIGHT_LINE,
                score,
                score,
                score,
                score,
                score,
                score,
                score,
                score);
    }

    private Order order(String id, String pickupRegionId, double pickupLat, double pickupLng, double dropLat, double dropLng) {
        Order order = new Order(
                id,
                "CUS-" + id,
                pickupRegionId,
                new GeoPoint(pickupLat, pickupLng),
                new GeoPoint(dropLat, dropLng),
                "R9",
                42_000.0,
                55,
                Instant.parse("2026-04-16T04:40:00Z"));
        order.setServiceType("instant");
        return order;
    }
}
