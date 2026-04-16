package com.routechain.v2.cluster;

import com.routechain.domain.Order;

import java.time.Instant;
import java.util.List;

public record BufferedOrderWindow(
        String windowId,
        Instant decisionTime,
        List<Order> releasedOrders,
        List<Order> heldOrders) {
}
