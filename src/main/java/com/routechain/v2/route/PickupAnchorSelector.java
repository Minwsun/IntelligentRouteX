package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.bundle.BundleCandidate;
import com.routechain.v2.bundle.BundleFamily;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PickupAnchorSelector {
    private final RouteChainDispatchV2Properties properties;

    public PickupAnchorSelector(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<PickupAnchor> select(List<com.routechain.v2.bundle.BundleCandidate> bundles, DispatchCandidateContext context) {
        return selectDetailed(bundles, context).selectedAnchors();
    }

    AnchorSelectionResult selectDetailed(List<com.routechain.v2.bundle.BundleCandidate> bundles, DispatchCandidateContext context) {
        List<PickupAnchor> anchors = new ArrayList<>();
        List<AnchorCandidateTrace> candidateTraces = new ArrayList<>();
        for (BundleCandidate bundle : bundles.stream().sorted(Comparator.comparing(BundleCandidate::bundleId)).toList()) {
            List<Order> orders = bundle.orderIds().stream()
                    .map(context::order)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            List<PickupAnchor> allAnchors = orders.stream()
                    .map(order -> anchor(bundle, order, context, orders))
                    .sorted(Comparator.comparingDouble(PickupAnchor::score).reversed()
                            .thenComparing(PickupAnchor::anchorOrderId))
                    .toList();
            List<PickupAnchor> rankedAnchors = allAnchors.stream()
                    .sorted(Comparator.comparingDouble(PickupAnchor::score).reversed()
                            .thenComparing(PickupAnchor::anchorOrderId))
                    .limit(Math.max(1, properties.getCandidate().getMaxAnchors()))
                    .toList();
            java.util.Set<String> retained = rankedAnchors.stream().map(PickupAnchor::anchorOrderId).collect(java.util.stream.Collectors.toSet());
            for (PickupAnchor candidate : allAnchors) {
                candidateTraces.add(new AnchorCandidateTrace(
                        candidate,
                        retained.contains(candidate.anchorOrderId()),
                        retained.contains(candidate.anchorOrderId()) ? "" : "anchor-top-n-truncated"));
            }
            int rank = 1;
            for (PickupAnchor anchor : rankedAnchors) {
                anchors.add(new PickupAnchor(
                        anchor.schemaVersion(),
                        anchor.bundleId(),
                        anchor.bundleOrderSetSignature(),
                        anchor.anchorOrderId(),
                        rank++,
                        anchor.score(),
                        anchor.reasons()));
            }
        }
        return new AnchorSelectionResult(List.copyOf(anchors), List.copyOf(candidateTraces));
    }

    private PickupAnchor anchor(BundleCandidate bundle, Order anchorOrder, DispatchCandidateContext context, List<Order> bundleOrders) {
        double pickupCompactness = pickupCompactness(anchorOrder, bundleOrders);
        double readyCentrality = readyCentrality(anchorOrder, bundleOrders);
        double urgencyBoost = anchorOrder.urgent() ? 0.08 : 0.0;
        double boundaryPenalty = context.isAcceptedBoundaryOrder(bundle.bundleId(), anchorOrder.orderId()) ? 0.12 : 0.0;
        if (bundle.family() == BundleFamily.BOUNDARY_CROSS) {
            boundaryPenalty += 0.05;
        }
        double score = Math.max(0.0, Math.min(1.0,
                0.42 * pickupCompactness
                        + 0.38 * readyCentrality
                        + urgencyBoost
                        - boundaryPenalty));
        List<String> reasons = new ArrayList<>();
        if (anchorOrder.urgent()) {
            reasons.add("urgent-anchor-boost");
        }
        if (boundaryPenalty > 0.0) {
            reasons.add("boundary-cross-caution");
        }
        return new PickupAnchor(
                "pickup-anchor/v1",
                bundle.bundleId(),
                bundle.orderSetSignature(),
                anchorOrder.orderId(),
                0,
                score,
                List.copyOf(reasons));
    }

    private double pickupCompactness(Order anchorOrder, List<Order> bundleOrders) {
        if (bundleOrders.size() <= 1) {
            return 1.0;
        }
        double averageDistance = bundleOrders.stream()
                .filter(order -> !order.orderId().equals(anchorOrder.orderId()))
                .mapToDouble(order -> distance(anchorOrder, order))
                .average()
                .orElse(0.0);
        return Math.max(0.0, 1.0 - (averageDistance * 10.0));
    }

    private double readyCentrality(Order anchorOrder, List<Order> bundleOrders) {
        if (bundleOrders.size() <= 1) {
            return 1.0;
        }
        java.time.Instant earliest = bundleOrders.stream().map(Order::readyAt).min(java.util.Comparator.naturalOrder()).orElse(anchorOrder.readyAt());
        java.time.Instant latest = bundleOrders.stream().map(Order::readyAt).max(java.util.Comparator.naturalOrder()).orElse(anchorOrder.readyAt());
        double totalSpan = Math.max(1.0, Duration.between(earliest, latest).toMinutes());
        java.time.Instant midpoint = earliest.plusSeconds(Duration.between(earliest, latest).getSeconds() / 2);
        double distanceFromMid = Math.abs(Duration.between(midpoint, anchorOrder.readyAt()).toMinutes());
        return Math.max(0.0, 1.0 - (distanceFromMid / totalSpan));
    }

    private double distance(Order left, Order right) {
        double lat = left.pickupPoint().latitude() - right.pickupPoint().latitude();
        double lon = left.pickupPoint().longitude() - right.pickupPoint().longitude();
        return Math.sqrt((lat * lat) + (lon * lon));
    }
}
