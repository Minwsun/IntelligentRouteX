package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import com.routechain.v2.DispatchV2Request;

import java.util.Comparator;
import java.util.List;

public final class OrderBuffer {
    private final RouteChainDispatchV2Properties properties;

    public OrderBuffer(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public BufferedOrderWindow buffer(DispatchV2Request request) {
        List<Order> orders = (request.openOrders() == null ? List.<Order>of() : request.openOrders()).stream()
                .sorted(Comparator.comparing(Order::orderId))
                .toList();
        int urgentOrderCount = (int) orders.stream().filter(Order::urgent).count();
        return new BufferedOrderWindow(
                "buffered-order-window/v1",
                request.traceId(),
                request.decisionTime(),
                properties.getBuffer().getHoldWindow().toMillis(),
                orders,
                orders.size(),
                urgentOrderCount);
    }
}

