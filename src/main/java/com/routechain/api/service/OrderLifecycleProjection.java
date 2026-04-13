package com.routechain.api.service;

import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.OrderOfferSnapshot;

import java.util.List;

public record OrderLifecycleProjection(
        OrderLifecycleStage lifecycleStage,
        List<OrderLifecycleEventView> lifecycleHistory,
        OrderOfferSnapshot offerSnapshot
) {
    public OrderLifecycleProjection {
        lifecycleStage = lifecycleStage == null ? OrderLifecycleStage.CREATED : lifecycleStage;
        lifecycleHistory = lifecycleHistory == null ? List.of() : List.copyOf(lifecycleHistory);
        offerSnapshot = offerSnapshot == null ? OrderOfferViewMapper.emptySnapshot() : offerSnapshot;
    }
}
