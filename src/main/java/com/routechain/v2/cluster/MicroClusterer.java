package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.v2.OrderSimilarity;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MicroClusterer {
    private static final ZoneId ASIA_SAIGON = ZoneId.of("Asia/Saigon");
    private final RouteChainDispatchV2Properties.Cluster properties;

    public MicroClusterer(RouteChainDispatchV2Properties.Cluster properties) {
        this.properties = properties;
    }

    public List<MicroCluster> cluster(BufferedOrderWindow window, PairSimilarityGraph graph) {
        List<Order> orders = window == null ? List.of() : window.releasedOrders();
        if (orders.isEmpty()) {
            return List.of();
        }
        Map<String, List<Order>> buckets = new LinkedHashMap<>();
        for (Order order : orders) {
            buckets.computeIfAbsent(bucketKey(order), ignored -> new ArrayList<>()).add(order);
        }

        List<MicroCluster> clusters = new ArrayList<>();
        int clusterSequence = 0;
        for (List<Order> bucketOrders : buckets.values()) {
            Set<String> visited = new LinkedHashSet<>();
            for (Order seed : bucketOrders) {
                if (!visited.add(seed.getId())) {
                    continue;
                }
                List<Order> component = connectedComponent(seed, bucketOrders, graph, visited);
                if (component.size() > maxClusterSize()) {
                    clusters.addAll(splitLargeComponent(component, ++clusterSequence));
                } else {
                    clusters.add(toCluster(component, ++clusterSequence, graph));
                }
            }
        }
        return List.copyOf(clusters);
    }

    private List<Order> connectedComponent(Order seed,
                                           List<Order> bucketOrders,
                                           PairSimilarityGraph graph,
                                           Set<String> visited) {
        List<Order> component = new ArrayList<>();
        Map<String, Order> byId = bucketOrders.stream().collect(LinkedHashMap::new, (map, order) -> map.put(order.getId(), order), Map::putAll);
        ArrayDeque<Order> queue = new ArrayDeque<>();
        queue.add(seed);
        while (!queue.isEmpty()) {
            Order current = queue.removeFirst();
            component.add(current);
            for (OrderSimilarity neighborEdge : graph.neighbors(current.getId())) {
                if (neighborEdge.similarityScore() < similarityThreshold()) {
                    continue;
                }
                String otherId = neighborEdge.leftOrderId().equals(current.getId())
                        ? neighborEdge.rightOrderId()
                        : neighborEdge.leftOrderId();
                Order other = byId.get(otherId);
                if (other != null && visited.add(otherId)) {
                    queue.add(other);
                }
            }
        }
        return component;
    }

    private List<MicroCluster> splitLargeComponent(List<Order> component, int clusterSequenceStart) {
        List<Order> sorted = component.stream()
                .sorted(Comparator.comparingDouble(this::directionDegrees))
                .toList();
        List<MicroCluster> split = new ArrayList<>();
        int chunkSize = Math.max(4, maxClusterSize());
        int sequence = clusterSequenceStart;
        for (int index = 0; index < sorted.size(); index += chunkSize) {
            List<Order> chunk = sorted.subList(index, Math.min(sorted.size(), index + chunkSize));
            split.add(toCluster(chunk, sequence++, new PairSimilarityGraph(List.of())));
        }
        return split;
    }

    private MicroCluster toCluster(List<Order> orders, int clusterSequence, PairSimilarityGraph graph) {
        GeoPoint centroid = centroid(orders);
        double dominantDirection = orders.stream()
                .mapToDouble(this::directionDegrees)
                .average()
                .orElse(0.0);
        double averageScore = orders.size() <= 1
                ? 1.0
                : orders.stream()
                .mapToDouble(order -> averageAdjacencyScore(order, graph))
                .average()
                .orElse(0.0);
        double coreCutoff = Math.max(similarityThreshold(), averageScore * 0.85);
        List<Order> core = new ArrayList<>();
        List<Order> boundary = new ArrayList<>();
        for (Order order : orders) {
            if (averageAdjacencyScore(order, graph) >= coreCutoff) {
                core.add(order);
            } else {
                boundary.add(order);
            }
        }
        if (core.isEmpty() && !orders.isEmpty()) {
            core.add(orders.getFirst());
            boundary.remove(orders.getFirst());
        }
        Instant start = orders.stream().map(this::timeOf).min(Instant::compareTo).orElse(Instant.EPOCH);
        Instant end = orders.stream().map(this::timeOf).max(Instant::compareTo).orElse(start);
        String clusterId = "cluster-" + clusterSequence;
        String corridorSignature = corridorSignature(dominantDirection);
        return new MicroCluster(clusterId, core, boundary, centroid, dominantDirection, corridorSignature, start, end);
    }

    private double averageAdjacencyScore(Order order, PairSimilarityGraph graph) {
        return graph.neighbors(order.getId()).stream()
                .mapToDouble(OrderSimilarity::similarityScore)
                .average()
                .orElse(0.0);
    }

    private String bucketKey(Order order) {
        Instant time = timeOf(order);
        int minuteBucket = time.atZone(ASIA_SAIGON).getHour() * 6 + (time.atZone(ASIA_SAIGON).getMinute() / 10);
        return order.getPickupRegionId() + ":" + minuteBucket;
    }

    private Instant timeOf(Order order) {
        return order.getPredictedReadyAt() != null ? order.getPredictedReadyAt() : order.getCreatedAt();
    }

    private GeoPoint centroid(List<Order> orders) {
        double lat = 0.0;
        double lng = 0.0;
        for (Order order : orders) {
            lat += order.getPickupPoint().lat();
            lng += order.getPickupPoint().lng();
        }
        return new GeoPoint(lat / orders.size(), lng / orders.size());
    }

    private double directionDegrees(Order order) {
        double dx = order.getDropoffPoint().lng() - order.getPickupPoint().lng();
        double dy = order.getDropoffPoint().lat() - order.getPickupPoint().lat();
        double degrees = Math.toDegrees(Math.atan2(dy, dx));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }

    private String corridorSignature(double dominantDirection) {
        if (dominantDirection <= 45.0 || dominantDirection > 315.0) {
            return "EAST";
        }
        if (dominantDirection <= 135.0) {
            return "NORTH";
        }
        if (dominantDirection <= 225.0) {
            return "WEST";
        }
        return "SOUTH";
    }

    private double similarityThreshold() {
        return properties == null ? 0.62 : properties.getSimilarityThreshold();
    }

    private int maxClusterSize() {
        return properties == null ? 24 : properties.getMaxClusterSize();
    }
}
