package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.cluster.PairSimilarityGraph;

import java.util.List;

public final class BundleValidator {
    private final RouteChainDispatchV2Properties.Bundle properties;

    public BundleValidator(RouteChainDispatchV2Properties.Bundle properties) {
        this.properties = properties;
    }

    public boolean allow(List<Order> orders, PairSimilarityGraph graph) {
        if (orders == null || orders.isEmpty()) {
            return false;
        }
        if (orders.size() > maxBundleSize()) {
            return false;
        }
        if (pickupSpreadKm(orders) > maxPickupSpreadKm()) {
            return false;
        }
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                double pairScore = graph.score(orders.get(i).getId(), orders.get(j).getId());
                if (pairScore <= 0.0 && orders.size() > 1) {
                    return false;
                }
            }
        }
        return true;
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

    private int maxBundleSize() {
        return properties == null ? 5 : properties.getMaxBundleSize();
    }

    private double maxPickupSpreadKm() {
        return properties == null ? 2.2 : properties.getMaxPickupSpreadKm();
    }
}
