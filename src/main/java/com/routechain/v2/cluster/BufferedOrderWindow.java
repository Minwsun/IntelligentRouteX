package com.routechain.v2.cluster;

import com.routechain.domain.Order;
import com.routechain.v2.SchemaVersioned;

import java.time.Instant;
import java.util.List;

public record BufferedOrderWindow(
        String schemaVersion,
        String traceId,
        Instant decisionTime,
        long holdWindowMs,
        List<Order> orders,
        int orderCount,
        int urgentOrderCount) implements SchemaVersioned {
}

