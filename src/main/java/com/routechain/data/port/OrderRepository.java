package com.routechain.data.port;

import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.domain.Order;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {
    void saveOrder(Order order);
    Optional<Order> findOrder(String orderId);
    Collection<Order> allOrders();
    void appendStatusHistory(OrderStatusHistoryRecord historyRecord);
    List<OrderStatusHistoryRecord> historyForOrder(String orderId);
}
