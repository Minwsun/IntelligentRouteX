package com.routechain.api.dto;

import java.util.List;

public record UserOrderResponse(
        String orderId,
        String customerId,
        String serviceTier,
        String status,
        OrderLifecycleStage lifecycleStage,
        double quotedFee,
        String assignedDriverId,
        String offerBatchId,
        OrderOfferSnapshot offerSnapshot,
        String createdAt,
        String assignedAt,
        String arrivedPickupAt,
        String pickedUpAt,
        String arrivedDropoffAt,
        String droppedOffAt,
        String cancelledAt,
        String failedAt,
        List<OrderLifecycleEventView> lifecycleHistory
) {}
