package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;

public final class BundleFamilyEnumerator {
    private final RouteChainDispatchV2Properties properties;

    public BundleFamilyEnumerator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<BundleCandidate> enumerate(BundleSeed seed, BundleContext context) {
        List<Order> prioritizedOrders = seed.prioritizedOrderIds().stream().map(context::order).toList();
        List<Order> workingOrders = seed.workingOrderIds().stream().map(context::order).sorted(Comparator.comparing(Order::orderId)).toList();
        if (workingOrders.isEmpty()) {
            return List.of();
        }

        Order preferredSeed = prioritizedOrders.isEmpty() ? workingOrders.getFirst() : prioritizedOrders.getFirst();
        java.util.ArrayList<BundleCandidate> candidates = new java.util.ArrayList<>();
        candidates.add(candidate(seed, BundleFamily.COMPACT_CLIQUE, bundleOrders(preferredSeed, prioritizedOrders, properties.getBundle().getMaxSize()), context));
        candidates.add(candidate(seed, BundleFamily.CORRIDOR_CHAIN, corridorChain(preferredSeed, prioritizedOrders), context));
        candidates.add(candidate(seed, BundleFamily.FAN_OUT_LIGHT, fanOutLight(preferredSeed, prioritizedOrders), context));
        if (!seed.acceptedBoundaryOrderIds().isEmpty()) {
            candidates.add(candidate(seed, BundleFamily.BOUNDARY_CROSS, boundaryCross(preferredSeed, prioritizedOrders, seed.acceptedBoundaryOrderIds()), context));
        }
        Order urgentOrder = workingOrders.stream().filter(Order::urgent).findFirst().orElse(null);
        if (urgentOrder != null) {
            candidates.add(candidate(seed, BundleFamily.URGENT_COMPANION, urgentCompanion(urgentOrder, prioritizedOrders), context));
        }
        candidates.add(candidate(seed, BundleFamily.LANDING_VALUE_BUNDLE, landingValueBundle(preferredSeed, prioritizedOrders), context));
        return candidates.stream().filter(candidate -> !candidate.orderIds().isEmpty()).toList();
    }

    private BundleCandidate candidate(BundleSeed seed, BundleFamily family, List<String> orderIds, BundleContext context) {
        List<String> distinctOrders = orderIds.stream().distinct().sorted().toList();
        String orderSetSignature = context.orderSetSignature(distinctOrders);
        String seedOrderId = distinctOrders.isEmpty() ? "none" : distinctOrders.getFirst();
        String corridorSignature = distinctOrders.isEmpty() ? "unknown" : corridorSignature(context.order(seedOrderId));
        List<String> acceptedBoundaryOrderIds = distinctOrders.stream()
                .filter(seed.acceptedBoundaryOrderIds()::contains)
                .toList();
        return new BundleCandidate(
                "bundle-candidate/v1",
                "%s|%s|%s".formatted(family.name(), orderSetSignature, seedOrderId),
                family,
                seed.cluster().clusterId(),
                family == BundleFamily.BOUNDARY_CROSS,
                acceptedBoundaryOrderIds,
                distinctOrders,
                orderSetSignature,
                seedOrderId,
                corridorSignature,
                0.0,
                false,
                List.of());
    }

    private List<String> bundleOrders(Order seedOrder, List<Order> prioritizedOrders, int maxSize) {
        LinkedHashSet<String> orderIds = new LinkedHashSet<>();
        orderIds.add(seedOrder.orderId());
        for (Order order : prioritizedOrders) {
            if (orderIds.size() >= maxSize) {
                break;
            }
            orderIds.add(order.orderId());
        }
        return List.copyOf(orderIds);
    }

    private List<String> corridorChain(Order seedOrder, List<Order> prioritizedOrders) {
        return bundleOrders(seedOrder, prioritizedOrders.stream()
                .sorted(Comparator.comparing((Order order) -> corridorSignature(order))
                        .thenComparing(Order::readyAt)
                        .thenComparing(Order::orderId))
                .toList(), Math.min(3, properties.getBundle().getMaxSize()));
    }

    private List<String> fanOutLight(Order seedOrder, List<Order> prioritizedOrders) {
        return bundleOrders(seedOrder, prioritizedOrders.stream()
                .sorted(Comparator.comparing(Order::readyAt).thenComparing(Order::orderId))
                .toList(), Math.min(3, properties.getBundle().getMaxSize()));
    }

    private List<String> boundaryCross(Order seedOrder, List<Order> prioritizedOrders, List<String> acceptedBoundaryOrderIds) {
        LinkedHashSet<String> orderIds = new LinkedHashSet<>();
        orderIds.add(seedOrder.orderId());
        acceptedBoundaryOrderIds.stream().sorted().limit(1).forEach(orderIds::add);
        for (Order order : prioritizedOrders) {
            if (orderIds.size() >= Math.min(3, properties.getBundle().getMaxSize())) {
                break;
            }
            orderIds.add(order.orderId());
        }
        return List.copyOf(orderIds);
    }

    private List<String> urgentCompanion(Order urgentOrder, List<Order> prioritizedOrders) {
        LinkedHashSet<String> orderIds = new LinkedHashSet<>();
        orderIds.add(urgentOrder.orderId());
        for (Order order : prioritizedOrders) {
            if (orderIds.size() >= Math.min(3, properties.getBundle().getMaxSize())) {
                break;
            }
            if (!order.urgent()) {
                orderIds.add(order.orderId());
            }
        }
        return List.copyOf(orderIds);
    }

    private List<String> landingValueBundle(Order seedOrder, List<Order> prioritizedOrders) {
        return bundleOrders(seedOrder, prioritizedOrders.stream()
                .sorted(Comparator.comparing((Order order) -> corridorSignature(order)).reversed()
                        .thenComparing(Order::readyAt)
                        .thenComparing(Order::orderId))
                .toList(), Math.min(4, properties.getBundle().getMaxSize()));
    }

    private String corridorSignature(Order order) {
        return "%d:%d".formatted(
                Math.round(order.dropoffPoint().latitude() - order.pickupPoint().latitude()),
                Math.round(order.dropoffPoint().longitude() - order.pickupPoint().longitude()));
    }
}
