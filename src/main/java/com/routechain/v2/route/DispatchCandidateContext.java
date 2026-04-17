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

public final class DispatchCandidateContext {
    private final Map<String, Order> orderById;
    private final Map<String, Driver> driverById;
    private final Map<String, BundleCandidate> bundleById;
    private final Map<String, MicroCluster> clusterById;
    private final Map<String, BoundaryExpansion> boundaryExpansionByClusterId;
    private final Map<String, Map<String, Double>> pairSupport;
    private final List<Driver> availableDrivers;

    public DispatchCandidateContext(List<Order> orders,
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

    public Order order(String orderId) {
        return orderById.get(orderId);
    }

    public BundleCandidate bundle(String bundleId) {
        return bundleById.get(bundleId);
    }

    public List<String> bundleIds() {
        return bundleById.keySet().stream().sorted().toList();
    }

    public List<Driver> availableDrivers() {
        return availableDrivers;
    }

    public Driver driver(String driverId) {
        return driverById.get(driverId);
    }

    public double pairSupport(String leftOrderId, String rightOrderId) {
        return pairSupport.getOrDefault(leftOrderId, Map.of()).getOrDefault(rightOrderId, 0.0);
    }

    public double averagePairSupport(List<String> orderIds) {
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

    public String orderSetSignature(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? "" : bundle.orderSetSignature();
    }

    public String corridorSignature(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? "unknown" : bundle.corridorSignature();
    }

    public double bundleScore(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? 0.0 : bundle.score();
    }

    public MicroCluster clusterForBundle(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? null : clusterById.get(bundle.clusterId());
    }

    public BoundaryExpansion boundaryExpansionForBundle(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        return bundle == null ? null : boundaryExpansionByClusterId.get(bundle.clusterId());
    }

    public boolean isAcceptedBoundaryOrder(String bundleId, String orderId) {
        BoundaryExpansion expansion = boundaryExpansionForBundle(bundleId);
        return expansion != null && expansion.acceptedBoundaryOrderIds().contains(orderId);
    }

    public double acceptedBoundarySupport(String bundleId) {
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

    public long readyTimeSpread(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.orderIds().size() <= 1) {
            return 0L;
        }
        java.time.Instant earliest = bundle.orderIds().stream()
                .map(this::order)
                .filter(java.util.Objects::nonNull)
                .map(Order::readyAt)
                .min(java.util.Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
        java.time.Instant latest = bundle.orderIds().stream()
                .map(this::order)
                .filter(java.util.Objects::nonNull)
                .map(Order::readyAt)
                .max(java.util.Comparator.naturalOrder())
                .orElse(java.time.Instant.EPOCH);
        return java.time.Duration.between(earliest, latest).toMinutes();
    }

    public double pickupCompactness(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.orderIds().size() <= 1) {
            return 1.0;
        }
        List<Order> orders = bundle.orderIds().stream()
                .map(this::order)
                .filter(java.util.Objects::nonNull)
                .toList();
        double minLat = orders.stream().mapToDouble(order -> order.pickupPoint().latitude()).min().orElse(0.0);
        double maxLat = orders.stream().mapToDouble(order -> order.pickupPoint().latitude()).max().orElse(0.0);
        double minLon = orders.stream().mapToDouble(order -> order.pickupPoint().longitude()).min().orElse(0.0);
        double maxLon = orders.stream().mapToDouble(order -> order.pickupPoint().longitude()).max().orElse(0.0);
        double spread = Math.abs(maxLat - minLat) + Math.abs(maxLon - minLon);
        return Math.max(0.0, 1.0 - (spread * 10.0));
    }

    public double acceptedBoundaryParticipation(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.orderIds().isEmpty()) {
            return 0.0;
        }
        return (double) bundle.acceptedBoundaryOrderIds().size() / bundle.orderIds().size();
    }

    public java.time.Instant readyWindowStart(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.orderIds().isEmpty()) {
            return null;
        }
        return bundle.orderIds().stream()
                .map(this::order)
                .filter(java.util.Objects::nonNull)
                .map(Order::readyAt)
                .min(java.util.Comparator.naturalOrder())
                .orElse(null);
    }

    public java.time.Instant readyWindowEnd(String bundleId) {
        BundleCandidate bundle = bundle(bundleId);
        if (bundle == null || bundle.orderIds().isEmpty()) {
            return null;
        }
        return bundle.orderIds().stream()
                .map(this::order)
                .filter(java.util.Objects::nonNull)
                .map(Order::readyAt)
                .max(java.util.Comparator.naturalOrder())
                .orElse(null);
    }
}
