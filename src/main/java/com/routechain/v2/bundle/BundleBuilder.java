package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.cluster.BoundaryExpansion;
import com.routechain.v2.cluster.MicroCluster;
import com.routechain.v2.cluster.PairSimilarityGraph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class BundleBuilder {
    private final RouteChainDispatchV2Properties.Bundle properties;
    private final BundleCompatibilityModel compatibilityModel;
    private final BundleValidator bundleValidator;

    public BundleBuilder(RouteChainDispatchV2Properties.Bundle properties) {
        this.properties = properties;
        this.compatibilityModel = new BundleCompatibilityModel();
        this.bundleValidator = new BundleValidator(properties);
    }

    public List<BundleCandidate> build(List<MicroCluster> clusters,
                                       List<BoundaryExpansion> expansions,
                                       PairSimilarityGraph graph) {
        List<BundleCandidate> bundles = new ArrayList<>();
        Set<String> dedupe = new LinkedHashSet<>();
        int sequence = 0;
        Map<String, BoundaryExpansion> expansionByCluster = expansions.stream()
                .collect(Collectors.toMap(BoundaryExpansion::sourceClusterId, expansion -> expansion, (left, right) -> left, LinkedHashMap::new));

        for (MicroCluster cluster : clusters) {
            List<Order> clusterOrders = cluster.allOrders();
            for (Order seed : clusterOrders) {
                ArrayDeque<List<Order>> beam = new ArrayDeque<>();
                beam.add(List.of(seed));
                List<Order> seedNeighbors = neighborOrders(seed, clusterOrders, graph);
                while (!beam.isEmpty()) {
                    List<Order> current = beam.removeFirst();
                    if (bundleValidator.allow(current, graph)) {
                        String signature = signature(current);
                        if (dedupe.add(signature)) {
                            BoundaryExpansion expansion = expansionByCluster.get(cluster.clusterId());
                            bundles.add(new BundleCandidate(
                                    "bundle-" + (++sequence),
                                    cluster.clusterId(),
                                    expansion == null ? null : expansion.expansionId(),
                                    current,
                                    compatibilityModel.score(current, graph)));
                        }
                    }
                    if (current.size() >= maxBundleSize()) {
                        continue;
                    }
                    List<List<Order>> expansionsForBeam = expand(current, seedNeighbors, graph);
                    expansionsForBeam.stream()
                            .sorted(Comparator.comparingDouble(
                                    (List<Order> candidate) -> compatibilityModel.score(candidate, graph).totalScore()).reversed())
                            .limit(beamWidth())
                            .forEach(beam::addLast);
                }
            }
        }

        return bundles.stream()
                .sorted(Comparator.comparingDouble((BundleCandidate candidate) -> candidate.bundleScore().totalScore()).reversed())
                .toList();
    }

    private List<List<Order>> expand(List<Order> current, List<Order> neighbors, PairSimilarityGraph graph) {
        List<List<Order>> next = new ArrayList<>();
        double currentScore = compatibilityModel.score(current, graph).totalScore();
        for (Order neighbor : neighbors) {
            if (current.contains(neighbor)) {
                continue;
            }
            List<Order> candidate = new ArrayList<>(current);
            candidate.add(neighbor);
            if (!bundleValidator.allow(candidate, graph)) {
                continue;
            }
            double nextScore = compatibilityModel.score(candidate, graph).totalScore();
            if (nextScore - currentScore > 0.0) {
                next.add(List.copyOf(candidate));
            }
        }
        return next;
    }

    private List<Order> neighborOrders(Order seed, List<Order> clusterOrders, PairSimilarityGraph graph) {
        Map<String, Order> byId = clusterOrders.stream()
                .collect(Collectors.toMap(Order::getId, order -> order, (left, right) -> left, LinkedHashMap::new));
        List<Order> neighbors = new ArrayList<>();
        graph.neighbors(seed.getId()).stream()
                .limit(topNeighbors())
                .forEach(similarity -> {
                    String otherId = similarity.leftOrderId().equals(seed.getId())
                            ? similarity.rightOrderId()
                            : similarity.leftOrderId();
                    Order other = byId.get(otherId);
                    if (other != null) {
                        neighbors.add(other);
                    }
                });
        return neighbors;
    }

    private String signature(List<Order> orders) {
        return orders.stream().map(Order::getId).sorted().collect(Collectors.joining("|"));
    }

    private int topNeighbors() {
        return properties == null ? 12 : properties.getTopNeighbors();
    }

    private int beamWidth() {
        return properties == null ? 16 : properties.getBeamWidth();
    }

    private int maxBundleSize() {
        return properties == null ? 5 : properties.getMaxBundleSize();
    }
}
