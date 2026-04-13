package com.routechain.data.port;

import com.routechain.data.model.OrderLifecycleFact;

import java.util.List;

public interface OrderLifecycleFactRepository {
    void append(OrderLifecycleFact fact);
    List<OrderLifecycleFact> factsForOrder(String orderId);
    List<OrderLifecycleFact> recentFacts(int limit);
}
