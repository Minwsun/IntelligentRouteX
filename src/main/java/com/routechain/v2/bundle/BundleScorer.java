package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;

import java.time.Duration;
import java.util.List;
import java.util.Map;

public final class BundleScorer {
    private final RouteChainDispatchV2Properties properties;

    public BundleScorer(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public BundleCandidate score(BundleCandidate candidate, BundleContext context) {
        List<Order> orders = context.orders(candidate.orderIds());
        double pairSupport = context.averagePairSupport(candidate.orderIds());
        double pickupCompactness = pickupCompactness(orders);
        double readyCompatibility = readyCompatibility(orders);
        double corridorCoherence = corridorCoherence(orders);
        double landingCompatibility = Math.max(0.2, 1.0 - (Math.abs(orders.size() - 2) * 0.1));
        double urgencyBonus = candidate.family() == BundleFamily.URGENT_COMPANION ? 0.08 : 0.0;
        double weakBoundaryPenalty = candidate.family() == BundleFamily.BOUNDARY_CROSS
                ? Math.max(0.0, 0.2 - boundarySupport(candidate, context))
                : 0.0;
        double score = Math.max(0.0, Math.min(1.0,
                0.34 * pairSupport
                        + 0.18 * pickupCompactness
                        + 0.16 * readyCompatibility
                        + 0.16 * corridorCoherence
                        + 0.16 * landingCompatibility
                        + urgencyBonus
                        - weakBoundaryPenalty));
        return new BundleCandidate(
                candidate.schemaVersion(),
                candidate.bundleId(),
                candidate.family(),
                candidate.clusterId(),
                candidate.boundaryCross(),
                candidate.acceptedBoundaryOrderIds(),
                candidate.orderIds(),
                candidate.orderSetSignature(),
                candidate.seedOrderId(),
                candidate.corridorSignature(),
                score,
                candidate.feasible(),
                candidate.degradeReasons());
    }

    private double pickupCompactness(List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }
        double minLat = orders.stream().mapToDouble(order -> order.pickupPoint().latitude()).min().orElse(0.0);
        double maxLat = orders.stream().mapToDouble(order -> order.pickupPoint().latitude()).max().orElse(0.0);
        double minLon = orders.stream().mapToDouble(order -> order.pickupPoint().longitude()).min().orElse(0.0);
        double maxLon = orders.stream().mapToDouble(order -> order.pickupPoint().longitude()).max().orElse(0.0);
        double spread = Math.abs(maxLat - minLat) + Math.abs(maxLon - minLon);
        return Math.max(0.0, 1.0 - (spread * 10.0));
    }

    private double readyCompatibility(List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }
        java.time.Instant earliest = orders.stream().map(Order::readyAt).min(java.util.Comparator.naturalOrder()).orElse(java.time.Instant.EPOCH);
        java.time.Instant latest = orders.stream().map(Order::readyAt).max(java.util.Comparator.naturalOrder()).orElse(java.time.Instant.EPOCH);
        long gapMinutes = Duration.between(earliest, latest).toMinutes();
        return Math.max(0.0, 1.0 - ((double) gapMinutes / Math.max(1, properties.getPair().getReadyGapMinutesThreshold())));
    }

    private double corridorCoherence(List<Order> orders) {
        long distinct = orders.stream()
                .map(order -> "%d:%d".formatted(
                        Math.round(order.dropoffPoint().latitude() - order.pickupPoint().latitude()),
                        Math.round(order.dropoffPoint().longitude() - order.pickupPoint().longitude())))
                .distinct()
                .count();
        return Math.max(0.1, 1.0 - (distinct - 1) * 0.25);
    }

    private double boundarySupport(BundleCandidate candidate, BundleContext context) {
        if (!candidate.boundaryCross()) {
            return 0.0;
        }
        BoundaryExpansion expansion = context.expansionsByClusterId().get(candidate.clusterId());
        if (expansion == null) {
            return 0.0;
        }
        return expansion.supportScoreByOrder().entrySet().stream()
                .filter(entry -> candidate.acceptedBoundaryOrderIds().contains(entry.getKey()))
                .mapToDouble(Map.Entry::getValue)
                .average()
                .orElse(0.0);
    }
}
