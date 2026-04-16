package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OrderBufferTest {

    @Test
    void materializesWindowWithCountsAndHoldWindow() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        OrderBuffer orderBuffer = new OrderBuffer(properties);
        List<Order> orders = List.of(
                ClusterTestFixtures.order("order-2", 10.776, 106.701, 10.781, 106.711, "2026-04-16T12:02:00Z", true),
                ClusterTestFixtures.order("order-1", 10.775, 106.700, 10.780, 106.710, "2026-04-16T12:00:00Z", false));

        BufferedOrderWindow window = orderBuffer.buffer(ClusterTestFixtures.request(orders));

        assertEquals(2, window.orderCount());
        assertEquals(1, window.urgentOrderCount());
        assertEquals(45_000L, window.holdWindowMs());
        assertEquals(List.of("order-1", "order-2"), window.orders().stream().map(Order::orderId).toList());
    }
}
