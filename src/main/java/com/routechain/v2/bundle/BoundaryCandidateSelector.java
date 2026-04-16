package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.cluster.BufferedOrderWindow;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.PairEdge;
import com.routechain.v2.cluster.PairSimilarityGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BoundaryCandidateSelector {
    private final RouteChainDispatchV2Properties properties;

    public BoundaryCandidateSelector(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public Map<String, List<BoundaryCandidate>> select(BufferedOrderWindow window,
                                                       List<MicroCluster> microClusters,
                                                       PairSimilarityGraph graph) {
        Map<String, Order> orderById = window.orders().stream()
                .collect(java.util.stream.Collectors.toMap(Order::orderId, order -> order));
        Map<String, List<PairEdge>> edgesByOrder = new LinkedHashMap<>();
        for (PairEdge edge : graph.edges()) {
            edgesByOrder.computeIfAbsent(edge.leftOrderId(), ignored -> new ArrayList<>()).add(edge);
            edgesByOrder.computeIfAbsent(edge.rightOrderId(), ignored -> new ArrayList<>()).add(edge);
        }

        Map<String, List<BoundaryCandidate>> result = new LinkedHashMap<>();
        for (MicroCluster cluster : microClusters) {
            Set<String> clusterOrders = new java.util.LinkedHashSet<>(cluster.orderIds());
            Map<String, BoundaryCandidate> candidates = new LinkedHashMap<>();
            for (String clusterOrderId : cluster.orderIds()) {
                for (PairEdge edge : edgesByOrder.getOrDefault(clusterOrderId, List.of())) {
                    String candidateOrderId = edge.leftOrderId().equals(clusterOrderId) ? edge.rightOrderId() : edge.leftOrderId();
                    if (clusterOrders.contains(candidateOrderId)) {
                        continue;
                    }
                    Order candidateOrder = orderById.get(candidateOrderId);
                    if (candidateOrder == null) {
                        continue;
                    }
                    BoundaryCandidate current = candidates.get(candidateOrderId);
                    BoundaryCandidate next = new BoundaryCandidate(
                            candidateOrderId,
                            current == null ? edge.weight() : Math.max(current.supportScore(), edge.weight()),
                            !cluster.corridorSignature().equals(corridorSignature(candidateOrder)),
                            candidateOrder.urgent(),
                            corridorSignature(candidateOrder));
                    candidates.put(candidateOrderId, next);
                }
            }
            List<BoundaryCandidate> prioritized = candidates.values().stream()
                    .sorted(Comparator.comparingDouble(BoundaryCandidate::supportScore).reversed()
                            .thenComparing(candidate -> orderById.get(candidate.orderId()).readyAt())
                            .thenComparing(BoundaryCandidate::orderId))
                    .limit(properties.getBoundaryExpansion().getMaxBoundaryOrdersPerCluster())
                    .toList();
            result.put(cluster.clusterId(), prioritized);
        }
        return result;
    }

    private String corridorSignature(Order order) {
        return "%d:%d".formatted(
                Math.round(order.dropoffPoint().latitude() - order.pickupPoint().latitude()),
                Math.round(order.dropoffPoint().longitude() - order.pickupPoint().longitude()));
    }
}
