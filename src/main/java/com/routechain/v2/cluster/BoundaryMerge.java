package com.routechain.v2.cluster;

import com.routechain.v2.OrderSimilarity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BoundaryMerge {
    private final int maxNeighborClusters;

    public BoundaryMerge(int maxNeighborClusters) {
        this.maxNeighborClusters = Math.max(1, maxNeighborClusters);
    }

    public List<BoundaryExpansion> expand(List<MicroCluster> clusters, PairSimilarityGraph graph) {
        List<BoundaryExpansion> expansions = new ArrayList<>();
        int sequence = 0;
        for (MicroCluster cluster : clusters) {
            List<MicroCluster> neighbors = clusters.stream()
                    .filter(other -> !other.clusterId().equals(cluster.clusterId()))
                    .sorted(Comparator.comparingDouble(other -> centroidDistanceKm(cluster, other)))
                    .limit(maxNeighborClusters)
                    .toList();
            for (MicroCluster neighbor : neighbors) {
                double centroidDistanceKm = centroidDistanceKm(cluster, neighbor);
                double directionGap = angleGap(cluster.dominantDropDirectionDegrees(), neighbor.dominantDropDirectionDegrees());
                double affinity = affinity(cluster, neighbor, graph);
                if (centroidDistanceKm <= 1.5 && directionGap <= 60.0 && affinity >= 0.55) {
                    List<String> candidateOrderIds = new ArrayList<>();
                    cluster.boundaryOrders().forEach(order -> candidateOrderIds.add(order.getId()));
                    neighbor.boundaryOrders().forEach(order -> {
                        if (!candidateOrderIds.contains(order.getId())) {
                            candidateOrderIds.add(order.getId());
                        }
                    });
                    expansions.add(new BoundaryExpansion(
                            "boundary-" + (++sequence),
                            cluster.clusterId(),
                            neighbor.clusterId(),
                            List.copyOf(candidateOrderIds),
                            affinity));
                }
            }
        }
        return List.copyOf(expansions);
    }

    private double affinity(MicroCluster left, MicroCluster right, PairSimilarityGraph graph) {
        List<OrderSimilarity> affinities = new ArrayList<>();
        left.boundaryOrders().forEach(order -> graph.neighbors(order.getId()).stream()
                .filter(similarity -> contains(right, similarity.leftOrderId()) || contains(right, similarity.rightOrderId()))
                .forEach(affinities::add));
        return affinities.stream().mapToDouble(OrderSimilarity::similarityScore).average().orElse(0.0);
    }

    private boolean contains(MicroCluster cluster, String orderId) {
        return cluster.allOrders().stream().anyMatch(order -> order.getId().equals(orderId));
    }

    private double centroidDistanceKm(MicroCluster left, MicroCluster right) {
        return left.pickupCentroid().distanceTo(right.pickupCentroid()) / 1000.0;
    }

    private double angleGap(double left, double right) {
        double diff = Math.abs(left - right);
        return diff > 180.0 ? 360.0 - diff : diff;
    }
}
