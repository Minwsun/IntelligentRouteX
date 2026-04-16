package com.routechain.v2.bundle;

import com.routechain.domain.Order;
import com.routechain.v2.BundleScore;
import com.routechain.v2.cluster.PairSimilarityGraph;

import java.util.List;

public final class BundleCompatibilityModel {

    public BundleScore score(List<Order> orders, PairSimilarityGraph graph) {
        if (orders == null || orders.isEmpty()) {
            return new BundleScore(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        double pickupSpreadKm = pickupSpreadKm(orders);
        double pickupCompactness = clamp01(1.0 - pickupSpreadKm / 2.2);
        double dropCoherence = dropCoherence(orders);
        double etaIncrease = etaIncreaseScore(orders, graph);
        double slaSafety = slaSafety(orders);
        double lowZigzag = lowZigzag(graph, orders);
        double landingValue = landingValue(orders);
        double resilience = clamp01(0.45 * lowZigzag + 0.35 * slaSafety + 0.20 * etaIncrease);
        double total = 0.22 * pickupCompactness
                + 0.18 * dropCoherence
                + 0.16 * etaIncrease
                + 0.10 * slaSafety
                + 0.10 * lowZigzag
                + 0.12 * landingValue
                + 0.12 * resilience;
        return new BundleScore(
                pickupCompactness,
                dropCoherence,
                etaIncrease,
                slaSafety,
                lowZigzag,
                landingValue,
                resilience,
                clamp01(total));
    }

    private double pickupSpreadKm(List<Order> orders) {
        double max = 0.0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                max = Math.max(max, orders.get(i).getPickupPoint().distanceTo(orders.get(j).getPickupPoint()) / 1000.0);
            }
        }
        return max;
    }

    private double dropCoherence(List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }
        double total = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                double angle = angleDiff(orders.get(i), orders.get(j));
                total += clamp01(1.0 - angle / 90.0);
                pairs++;
            }
        }
        return pairs == 0 ? 1.0 : total / pairs;
    }

    private double etaIncreaseScore(List<Order> orders, PairSimilarityGraph graph) {
        if (orders.size() <= 1) {
            return 1.0;
        }
        double average = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                average += graph.score(orders.get(i).getId(), orders.get(j).getId());
                pairs++;
            }
        }
        return pairs == 0 ? 0.4 : clamp01(average / pairs);
    }

    private double slaSafety(List<Order> orders) {
        return orders.stream()
                .mapToDouble(order -> clamp01(order.getPromisedEtaMinutes() / 60.0))
                .average()
                .orElse(0.5);
    }

    private double lowZigzag(PairSimilarityGraph graph, List<Order> orders) {
        if (orders.size() <= 1) {
            return 1.0;
        }
        double penalty = 0.0;
        int pairs = 0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                penalty += 1.0 - graph.score(orders.get(i).getId(), orders.get(j).getId());
                pairs++;
            }
        }
        return pairs == 0 ? 1.0 : clamp01(1.0 - penalty / pairs);
    }

    private double landingValue(List<Order> orders) {
        if (orders.isEmpty()) {
            return 0.0;
        }
        Order last = orders.getLast();
        double corridorValue = orders.stream()
                .mapToDouble(order -> clamp01(1.0 - order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 6_000.0))
                .average()
                .orElse(0.4);
        return clamp01(0.55 * corridorValue + 0.45 * clamp01(last.getPromisedEtaMinutes() / 60.0));
    }

    private double angleDiff(Order left, Order right) {
        double leftAngle = angle(left);
        double rightAngle = angle(right);
        double diff = Math.abs(leftAngle - rightAngle);
        return diff > 180.0 ? 360.0 - diff : diff;
    }

    private double angle(Order order) {
        double dx = order.getDropoffPoint().lng() - order.getPickupPoint().lng();
        double dy = order.getDropoffPoint().lat() - order.getPickupPoint().lat();
        double angle = Math.toDegrees(Math.atan2(dy, dx));
        return angle < 0 ? angle + 360.0 : angle;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
