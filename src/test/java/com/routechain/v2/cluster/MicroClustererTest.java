package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MicroClustererTest {

    @Test
    void connectedComponentsAndSingletonsBecomeClustersDeterministically() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getCluster().setMaxSize(4);
        MicroClusterer clusterer = new MicroClusterer(properties);
        Order order1 = ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7800, 106.7100, "2026-04-16T12:00:00Z", false);
        Order order2 = ClusterTestFixtures.order("order-2", 10.7758, 106.7008, 10.7808, 106.7108, "2026-04-16T12:02:00Z", false);
        Order order3 = ClusterTestFixtures.order("order-3", 10.7765, 106.7015, 10.7815, 106.7115, "2026-04-16T12:04:00Z", false);
        Order order4 = ClusterTestFixtures.order("order-4", 10.8500, 106.7900, 10.8600, 106.8000, "2026-04-16T12:06:00Z", false);
        BufferedOrderWindow window = ClusterTestFixtures.window(List.of(order4, order2, order1, order3));
        PairSimilarityGraph graph = new PairSimilarityGraph(
                "pair-similarity-graph/v1",
                4,
                2,
                List.of(
                        new PairEdge("order-1", "order-2", 0.8),
                        new PairEdge("order-2", "order-3", 0.75)));

        List<MicroCluster> first = clusterer.cluster(window, graph);
        List<MicroCluster> second = clusterer.cluster(window, graph);

        assertEquals(2, first.size());
        assertEquals(first, second);
        assertEquals(List.of("order-1", "order-2", "order-3"), first.getFirst().orderIds());
        assertEquals(List.of("order-4"), first.get(1).orderIds());
        assertEquals("cluster-001", first.getFirst().clusterId());
        assertEquals("cluster-002", first.get(1).clusterId());
    }

    @Test
    void oversizedComponentIsSplitAndNeverExceedsConfiguredMaxSize() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getCluster().setMaxSize(2);
        MicroClusterer clusterer = new MicroClusterer(properties);
        List<Order> orders = List.of(
                ClusterTestFixtures.order("order-1", 10.7750, 106.7000, 10.7850, 106.7100, "2026-04-16T12:00:00Z", false),
                ClusterTestFixtures.order("order-2", 10.7755, 106.7005, 10.7855, 106.7105, "2026-04-16T12:01:00Z", false),
                ClusterTestFixtures.order("order-3", 10.7760, 106.7010, 10.7860, 106.7110, "2026-04-16T12:02:00Z", false),
                ClusterTestFixtures.order("order-4", 10.7765, 106.7015, 10.7865, 106.7115, "2026-04-16T12:03:00Z", false),
                ClusterTestFixtures.order("order-5", 10.7770, 106.7020, 10.7870, 106.7120, "2026-04-16T12:04:00Z", false));
        PairSimilarityGraph graph = new PairSimilarityGraph(
                "pair-similarity-graph/v1",
                5,
                4,
                List.of(
                        new PairEdge("order-1", "order-2", 0.8),
                        new PairEdge("order-2", "order-3", 0.8),
                        new PairEdge("order-3", "order-4", 0.8),
                        new PairEdge("order-4", "order-5", 0.8)));

        List<MicroCluster> clusters = clusterer.cluster(ClusterTestFixtures.window(orders), graph);

        assertTrue(clusters.size() >= 3);
        assertTrue(clusters.stream().allMatch(cluster -> cluster.orderIds().size() <= 2));
    }
}
