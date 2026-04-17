package com.routechain.v2.bundle;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.cluster.BufferedOrderWindow;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.MicroClusterSummary;
import com.routechain.v2.cluster.PairEdge;
import com.routechain.v2.cluster.PairGraphSummary;
import com.routechain.v2.cluster.PairSimilarityGraph;

import java.time.Instant;
import java.util.List;

final class BundleTestFixtures {
    private BundleTestFixtures() {
    }

    static Order order(String orderId,
                       double pickupLat,
                       double pickupLon,
                       double dropLat,
                       double dropLon,
                       String readyAt,
                       boolean urgent) {
        Instant readyTime = Instant.parse(readyAt);
        return new Order(
                orderId,
                new GeoPoint(pickupLat, pickupLon),
                new GeoPoint(dropLat, dropLon),
                readyTime.minusSeconds(300),
                readyTime,
                20,
                urgent);
    }

    static List<Order> orders() {
        return List.of(
                order("order-1", 10.7750, 106.7000, 10.7800, 106.7100, "2026-04-16T12:00:00Z", false),
                order("order-2", 10.7757, 106.7007, 10.7808, 106.7108, "2026-04-16T12:02:00Z", false),
                order("order-3", 10.7764, 106.7014, 10.7815, 106.7115, "2026-04-16T12:04:00Z", true),
                order("order-4", 10.7770, 106.7020, 10.7890, 106.7250, "2026-04-16T12:06:00Z", false));
    }

    static BufferedOrderWindow window() {
        List<Order> orders = orders();
        return new BufferedOrderWindow(
                "buffered-order-window/v1",
                "trace-bundle",
                Instant.parse("2026-04-16T12:00:00Z"),
                45_000L,
                orders,
                orders.size(),
                1);
    }

    static PairSimilarityGraph graph() {
        return new PairSimilarityGraph(
                "pair-similarity-graph/v1",
                4,
                4,
                List.of(
                        new PairEdge("order-1", "order-2", 0.90),
                        new PairEdge("order-1", "order-3", 0.82),
                        new PairEdge("order-2", "order-3", 0.78),
                        new PairEdge("order-2", "order-4", 0.70)));
    }

    static List<MicroCluster> microClusters() {
        return List.of(
                new MicroCluster(
                        "micro-cluster/v1",
                        "cluster-001",
                        List.of("order-1", "order-2"),
                        List.of("order-1"),
                        List.of("order-2"),
                        new GeoPoint(10.77535, 106.70035),
                        42.0,
                        "0:0",
                        2L),
                new MicroCluster(
                        "micro-cluster/v1",
                        "cluster-002",
                        List.of("order-3"),
                        List.of("order-3"),
                        List.of(),
                        new GeoPoint(10.7764, 106.7014),
                        45.0,
                        "0:0",
                        0L));
    }

    static DispatchPairClusterStage pairClusterStage() {
        return new DispatchPairClusterStage(
                "dispatch-pair-cluster-stage/v1",
                window(),
                new PairGraphSummary("pair-graph-summary/v1", 6, 4, 4, 0.8, List.of()),
                graph(),
                microClusters(),
                new MicroClusterSummary("micro-cluster-summary/v1", 2, 2, 1, List.of()),
                List.of(),
                List.of());
    }

    static EtaContext clearEtaContext() {
        return new EtaContext(
                "dispatch-eta-context/v1",
                "trace-bundle",
                1,
                6.0,
                6.0,
                0.3,
                false,
                false,
                "corridor-a",
                "baseline-profile-weather");
    }

    static EtaContext weatherBadEtaContext() {
        return new EtaContext(
                "dispatch-eta-context/v1",
                "trace-bundle",
                1,
                8.0,
                8.0,
                0.5,
                true,
                true,
                "corridor-a",
                "baseline-profile-weather");
    }

    static DispatchV2Request request() {
        return new DispatchV2Request(
                "dispatch-v2-request/v1",
                "trace-bundle",
                orders(),
                List.of(),
                List.of(),
                WeatherProfile.CLEAR,
                Instant.parse("2026-04-16T12:00:00Z"));
    }
}
