package com.routechain.v2.route;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.v2.bundle.BoundaryExpansion;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.bundle.DispatchBundleStage;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.PairEdge;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class DispatchCandidateContext {
    private final Map<String, Order> orderById;
    private final Map<String, Driver> driverById;
    private final Map<String, BundleCandidate> bundleById;
    private final Map<String, MicroCluster> clusterById;
    private final Map<String, BoundaryExpansion> boundaryExpansionByClusterId;
    private final Map<String, Map<String, Double>> pairSupport;
    private final List<Driver> availableDrivers;

    DispatchCandidateContext(List<Order> orders,
                             List<Driver> availableDrivers,
                             DispatchPairClusterStage pairClusterStage,
                             DispatchBundleStage bundleStage) {
        this.orderById = orders.stream().collect(java.util.stream.Collectors.toMap(Order::orderId, order -> order));
        this.bundleById = bundleStage.bundleCandidates().stream().collect(java.util.stream.Collectors.toMap(BundleCandidate::bundleId, bundle -> bundle));
        this.clusterById = pairClusterStage.microClusters().stream().collect(java.util.stream.Collectors.toMap(MicroCluster::clusterId, cluster -> cluster));
        this.boundaryExpansionByClusterId = bundleStage.boundaryExpansions().stream()
                .collect(java.util.stream.Collectors.toMap(BoundaryExpansion::clusterId, expansion -> expansion));
        this.pairSupport = new HashMap<>();
        for (PairEdge edge : pairClusterStage.pairSimilarityGraph().edges()) {
            pairSupport.computeIfAbsent(edge.leftOrderId(), ignored -> new HashMap<>()).put(edge.rightOrderId(), edge.weight());
            pairSupport.computeIfAbsent(edge.rightOrderId(), ignored -> new HashMap<>()).put(edge.leftOrderId(), edge.weight());
        }
        this.availableDrivers = availableDrivers == null ? List.of() : availableDrivers.stream()
                .sorted(java.util.Comparator.comparing(Driver::driverId))
                .toList();
        this.driverById = this.availableDrivers.stream()
                .collect(java.util.stream.Collectors.toMap(Driver::driverId, driver -> driver));
    }

    Order order(String orderId) {
        return orderById.get(orderId);
    }

    BundleCandidate bundle(String bundleId) {
        return bundleById.get(bundleId);
    }

    List<String> bundleIds() {
        return bundleById.keySet().stream().sorted().toList();
    }

    List<Driver> availableDrivers() {
        return availableDrivers;
    }

    Driver driver(String driverId) {
        return driverById.get(driverId);
    }

    double pairSupport(String leftOrderId, String rightOrderId) {
        return pairSupport.getOrDefault(leftOrderId, Map.of()).getOrDefault(rightOrderId, 0.0);
    }

    double averagePairSupport(List<String> orderIds) {
        if (orderIds.size() <= 1) {
            return 1.0;
        }
        double total = 0.0;
        int count = 0;
        List<String> sorted = orderIds.stream().sorted().toList();
        for (int i = 0; i < sorted.size(); i++) {
            for (int j = i + 1; j < sorted.size(); j++) {
                double score = pairSupport(sorted.get(i), sorted.get(j));
                if (score > 0.0) {
                    total += score;
                    count++;
                }
            }
        }
        return count == 0 ? 0.0 : total / count;
    }

    String orderSetSignature(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? "" : bundle.orderSetSignature();
    }

    String corridorSignature(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? "unknown" : bundle.corridorSignature();
    }

    double bundleScore(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? 0.0 : bundle.score();
    }

    MicroCluster clusterForBundle(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? null : clusterById.get(bundle.clusterId());
    }

    BoundaryExpansion boundaryExpansionForBundle(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? null : boundaryExpansionByClusterId.get(bundle.clusterId());
    }

    boolean isAcceptedBoundaryOrder(String bundleId, String orderId) {
        BoundaryExpansion expansion = boundaryExpansionForBundle(bundleId);
        return expansion != null && expansion.acceptedBoundaryOrderIds().contains(orderId);
    }

    double acceptedBoundarySupport(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.acceptedBoundaryOrderIds().isEmpty()) {
            return 0.0;
        }
        BoundaryExpansion expansion = boundaryExpansionForBundle(bundleId);
        if (expansion == null) {
            return 0.0;
        }
        return bundle.acceptedBoundaryOrderIds().stream()
                .mapToDouble(orderId -> expansion.supportScoreByOrder().getOrDefault(orderId, 0.0))
                .average()
                .orElse(0.0);
    }
}
