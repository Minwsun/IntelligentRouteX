package com.routechain.api.dto;

import java.util.List;

public record OpsRealtimeSnapshot(
        List<OpsOrderMonitorView> activeOrders
) {}
