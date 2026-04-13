package com.routechain.api.dto;

public record OpsOrderMonitorView(
        String orderId,
        String customerId,
        String merchantId,
        String status,
        OrderLifecycleStage lifecycleStage,
        String assignedDriverId,
        OrderOfferSnapshot offerSnapshot,
        String createdAt,
        String updatedAt
) {}
