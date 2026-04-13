package com.routechain.api.dto;

public record MerchantOrderView(
        String merchantId,
        String orderId,
        String customerId,
        String status,
        OrderLifecycleStage lifecycleStage,
        String assignedDriverId,
        OrderOfferSnapshot offerSnapshot,
        double quotedFee,
        String createdAt,
        String updatedAt
) {}
