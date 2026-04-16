package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class MicroClusterer {
    private final RouteChainDispatchV2Properties properties;

    public MicroClusterer(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<MicroCluster> cluster(BufferedOrderWindow window, PairSimilarityGraph graph) {
        List<Order> orders = window.orders().stream().sorted(Comparator.comparing(Order::orderId)).toList();
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (Order order : orders) {
            adjacency.put(order.orderId(), new HashSet<>());
        }
        for (PairEdge edge : graph.edges()) {
            adjacency.get(edge.leftOrderId()).add(edge.rightOrderId());
            adjacency.get(edge.rightOrderId()).add(edge.leftOrderId());
        }

        List<List<Order>> components = connectedComponents(orders, adjacency);
        List<List<Order>> normalized = new ArrayList<>();
        for (List<Order> component : components) {
            if (component.size() > properties.getCluster().getMaxSize()) {
                normalized.addAll(splitComponent(component));
            } else {
                normalized.add(component);
            }
        }

        List<List<Order>> sortedComponents = normalized.stream()
                .map(component -> component.stream().sorted(Comparator.comparing(Order::orderId)).toList())
                .sorted(Comparator.comparing(component -> component.getFirst().orderId()))
                .toList();

        List<MicroCluster> clusters = new ArrayList<>();
        int index = 1;
        for (List<Order> component : sortedComponents) {
            clusters.add(toCluster(index++, component));
        }
        return List.copyOf(clusters);
    }

    private List<List<Order>> connectedComponents(List<Order> orders, Map<String, Set<String>> adjacency) {
        Map<String, Order> orderById = orders.stream().collect(java.util.stream.Collectors.toMap(Order::orderId, order -> order));
        Set<String> visited = new HashSet<>();
        List<List<Order>> components = new ArrayList<>();
        for (Order order : orders) {
            if (visited.contains(order.orderId())) {
                continue;
            }
            ArrayDeque<String> queue = new ArrayDeque<>();
            List<Order> component = new ArrayList<>();
            queue.add(order.orderId());
            visited.add(order.orderId());
            while (!queue.isEmpty()) {
                String current = queue.removeFirst();
                component.add(orderById.get(current));
                List<String> neighbors = adjacency.getOrDefault(current, Set.of()).stream().sorted().toList();
                for (String neighbor : neighbors) {
                    if (visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            components.add(component);
        }
        return components;
    }

    private List<List<Order>> splitComponent(List<Order> component) {
        List<Order> sorted = component.stream()
                .sorted(Comparator.comparingDouble(this::dropBearing).thenComparing(Order::orderId))
                .toList();
        List<List<Order>> parts = new ArrayList<>();
        int maxSize = properties.getCluster().getMaxSize();
        for (int start = 0; start < sorted.size(); start += maxSize) {
            parts.add(sorted.subList(start, Math.min(sorted.size(), start + maxSize)));
        }
        return parts;
    }

    private MicroCluster toCluster(int index, List<Order> component) {
        List<String> orderIds = component.stream().map(Order::orderId).toList();
        List<String> boundaryOrderIds = component.size() > 1 ? List.of(component.getLast().orderId()) : List.of();
        List<String> coreOrderIds = component.stream()
                .map(Order::orderId)
                .filter(orderId -> !boundaryOrderIds.contains(orderId))
                .toList();
        return new MicroCluster(
                "micro-cluster/v1",
                "cluster-%03d".formatted(index),
                orderIds,
                coreOrderIds,
                boundaryOrderIds,
                pickupCentroid(component),
                component.stream().mapToDouble(this::dropBearing).average().orElse(0.0),
                corridorSignature(component),
                timeSpanMinutes(component));
    }

    private GeoPoint pickupCentroid(List<Order> component) {
        double avgLat = component.stream().mapToDouble(order -> order.pickupPoint().latitude()).average().orElse(0.0);
        double avgLon = component.stream().mapToDouble(order -> order.pickupPoint().longitude()).average().orElse(0.0);
        return new GeoPoint(avgLat, avgLon);
    }

    private String corridorSignature(List<Order> component) {
        long lat = Math.round(component.stream().mapToDouble(order -> order.dropoffPoint().latitude() - order.pickupPoint().latitude()).average().orElse(0.0));
        long lon = Math.round(component.stream().mapToDouble(order -> order.dropoffPoint().longitude() - order.pickupPoint().longitude()).average().orElse(0.0));
        return "%d:%d".formatted(lat, lon);
    }

    private long timeSpanMinutes(List<Order> component) {
        return component.stream().map(Order::readyAt).min(java.util.Comparator.naturalOrder())
                .map(min -> component.stream().map(Order::readyAt).max(java.util.Comparator.naturalOrder())
                        .map(max -> Duration.between(min, max).toMinutes())
                        .orElse(0L))
                .orElse(0L);
    }

    private double dropBearing(Order order) {
        double y = order.dropoffPoint().longitude() - order.pickupPoint().longitude();
        double x = order.dropoffPoint().latitude() - order.pickupPoint().latitude();
        double degrees = Math.toDegrees(Math.atan2(y, x));
        return degrees < 0 ? degrees + 360.0 : degrees;
    }
}

