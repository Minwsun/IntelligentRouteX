package com.routechain.api.service;

import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import java.time.Instant;

final class OrderLifecycleViewMapper {
    private OrderLifecycleViewMapper() {
    }

    static OrderLifecycleStage stageFor(OrderLifecycleProjection projection) {
        return projection == null ? OrderLifecycleStage.CREATED : projection.lifecycleStage();
    }

    static String legacyStageToken(OrderLifecycleStage stage) {
        if (stage == null) {
            return "created";
        }
        return switch (stage) {
            case CREATED -> "created";
            case OFFERED -> "searching";
            case ACCEPTED -> "driver_en_route";
            case ARRIVED_PICKUP -> "arrived_pickup";
            case PICKED_UP -> "picked_up";
            case ARRIVED_DROPOFF -> "arrived_dropoff";
            case DROPPED_OFF -> "completed";
            case CANCELLED -> "cancelled";
            case FAILED -> "failed";
            case EXPIRED -> "expired";
        };
    }

    static java.util.List<OrderLifecycleEventView> historyView(OrderLifecycleProjection projection) {
        return projection == null ? java.util.List.of() : projection.lifecycleHistory();
    }

    private static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
