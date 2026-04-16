package com.routechain.v2.bundle;

import com.routechain.domain.Order;
import com.routechain.v2.BundleScore;

import java.util.List;

public record BundleCandidate(
        String bundleId,
        String clusterId,
        String boundaryExpansionId,
        List<Order> orders,
        BundleScore bundleScore) {

    public int size() {
        return orders == null ? 0 : orders.size();
    }
}
