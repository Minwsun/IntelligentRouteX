package com.routechain.api.dto;

public record UserOrderResponse(
        String orderId,
        String customerId,
        String serviceTier,
        String status,
        double quotedFee,
        String assignedDriverId,
        String offerBatchId
) {}
