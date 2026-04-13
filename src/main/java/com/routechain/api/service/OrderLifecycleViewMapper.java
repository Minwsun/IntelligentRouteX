package com.routechain.api.service;

import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.domain.Enums.OrderStatus;
import com.routechain.domain.Order;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class OrderLifecycleViewMapper {
    private OrderLifecycleViewMapper() {
    }

    static OrderLifecycleStage stageFor(Order order, boolean hasOfferBatch) {
        if (order == null) {
            return OrderLifecycleStage.CREATED;
        }
        if (isTerminal(order.getStatus())) {
            return switch (order.getStatus()) {
                case DELIVERED -> OrderLifecycleStage.DROPPED_OFF;
                case CANCELLED -> OrderLifecycleStage.CANCELLED;
                case FAILED -> OrderLifecycleStage.FAILED;
                case EXPIRED -> OrderLifecycleStage.EXPIRED;
                default -> OrderLifecycleStage.CREATED;
            };
        }
        if (order.getArrivedDropoffAt() != null) {
            return OrderLifecycleStage.ARRIVED_DROPOFF;
        }
        if (order.getPickedUpAt() != null || order.getStatus() == OrderStatus.PICKED_UP
                || order.getStatus() == OrderStatus.DROPOFF_EN_ROUTE) {
            return OrderLifecycleStage.PICKED_UP;
        }
        if (order.getArrivedPickupAt() != null) {
            return OrderLifecycleStage.ARRIVED_PICKUP;
        }
        if (order.getAssignedDriverId() != null && !order.getAssignedDriverId().isBlank()
                || order.getStatus() == OrderStatus.ASSIGNED
                || order.getStatus() == OrderStatus.PICKUP_EN_ROUTE) {
            return OrderLifecycleStage.ACCEPTED;
        }
        if (hasOfferBatch) {
            return OrderLifecycleStage.OFFERED;
        }
        return OrderLifecycleStage.CREATED;
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

    static List<OrderLifecycleEventView> historyView(List<OrderStatusHistoryRecord> historyRecords) {
        if (historyRecords == null || historyRecords.isEmpty()) {
            return List.of();
        }
        return historyRecords.stream()
                .sorted(Comparator.comparing(OrderStatusHistoryRecord::recordedAt))
                .map(record -> new OrderLifecycleEventView(
                        stageForHistoryRecord(record),
                        normalizeStatus(record.status()),
                        record.reason(),
                        toIso(record.recordedAt())))
                .toList();
    }

    private static OrderLifecycleStage stageForHistoryRecord(OrderStatusHistoryRecord record) {
        String status = normalizeStatus(record == null ? null : record.status());
        String reason = record == null || record.reason() == null ? "" : record.reason().trim().toLowerCase(Locale.ROOT);
        return switch (status) {
            case "OFFERED" -> OrderLifecycleStage.OFFERED;
            case "ASSIGNED" -> OrderLifecycleStage.ACCEPTED;
            case "ARRIVED_PICKUP" -> OrderLifecycleStage.ARRIVED_PICKUP;
            case "PICKED_UP", "DROPOFF_EN_ROUTE" -> OrderLifecycleStage.PICKED_UP;
            case "ARRIVED_DROPOFF" -> OrderLifecycleStage.ARRIVED_DROPOFF;
            case "DELIVERED" -> OrderLifecycleStage.DROPPED_OFF;
            case "CANCELLED" -> OrderLifecycleStage.CANCELLED;
            case "FAILED" -> OrderLifecycleStage.FAILED;
            case "EXPIRED" -> OrderLifecycleStage.EXPIRED;
            case "PICKUP_EN_ROUTE" -> OrderLifecycleStage.ACCEPTED;
            case "QUOTED", "CONFIRMED", "PENDING_ASSIGNMENT" ->
                    "offers_published".equals(reason) ? OrderLifecycleStage.OFFERED : OrderLifecycleStage.CREATED;
            default -> OrderLifecycleStage.CREATED;
        };
    }

    private static boolean isTerminal(OrderStatus status) {
        return status == OrderStatus.DELIVERED
                || status == OrderStatus.CANCELLED
                || status == OrderStatus.FAILED
                || status == OrderStatus.EXPIRED;
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return status.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }

    private static String toIso(Instant instant) {
        return instant == null ? null : instant.toString();
    }
}
