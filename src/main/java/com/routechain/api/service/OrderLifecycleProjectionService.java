package com.routechain.api.service;

import com.routechain.api.dto.OrderLifecycleEventView;
import com.routechain.api.dto.OrderLifecycleStage;
import com.routechain.api.dto.OrderOfferSnapshot;
import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.port.OrderLifecycleFactRepository;
import com.routechain.data.port.OrderRepository;
import com.routechain.domain.Order;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class OrderLifecycleProjectionService {
    private final OrderLifecycleFactRepository factRepository;
    private final OrderRepository orderRepository;
    private final OfferStateStore offerStateStore;

    public OrderLifecycleProjectionService(OrderLifecycleFactRepository factRepository,
                                           OrderRepository orderRepository,
                                           OfferStateStore offerStateStore) {
        this.factRepository = factRepository;
        this.orderRepository = orderRepository;
        this.offerStateStore = offerStateStore;
    }

    public OrderLifecycleProjection project(String orderId) {
        var facts = factRepository.factsForOrder(orderId);
        if (!facts.isEmpty()) {
            return OrderLifecycleFactProjector.project(facts);
        }
        return orderRepository.findOrder(orderId)
                .map(this::compatibilityProjection)
                .orElse(new OrderLifecycleProjection(
                        OrderLifecycleStage.CREATED,
                        List.of(),
                        OrderOfferViewMapper.emptySnapshot()
                ));
    }

    private OrderLifecycleProjection compatibilityProjection(Order order) {
        OrderOfferSnapshot offerSnapshot = OrderOfferViewMapper.snapshot(
                offerStateStore.batchesForOrder(order.getId()),
                offerStateStore.offersForOrder(order.getId()),
                offerStateStore.decisionsForOrder(order.getId()),
                offerStateStore.findReservation(order.getId()).orElse(null),
                order.getAssignedDriverId());
        OrderLifecycleStage stage = compatibilityStage(order, offerSnapshot);
        List<OrderLifecycleEventView> history = compatibilityHistory(orderRepository.historyForOrder(order.getId()));
        return new OrderLifecycleProjection(stage, history, offerSnapshot);
    }

    private OrderLifecycleStage compatibilityStage(Order order, OrderOfferSnapshot offerSnapshot) {
        if (order == null) {
            return OrderLifecycleStage.CREATED;
        }
        if (isTerminal(order.getStatus().name())) {
            return switch (order.getStatus().name()) {
                case "DELIVERED" -> OrderLifecycleStage.DROPPED_OFF;
                case "CANCELLED" -> OrderLifecycleStage.CANCELLED;
                case "FAILED" -> OrderLifecycleStage.FAILED;
                case "EXPIRED" -> OrderLifecycleStage.EXPIRED;
                default -> OrderLifecycleStage.CREATED;
            };
        }
        if (order.getArrivedDropoffAt() != null) {
            return OrderLifecycleStage.ARRIVED_DROPOFF;
        }
        if (order.getPickedUpAt() != null || "PICKED_UP".equalsIgnoreCase(order.getStatus().name())
                || "DROPOFF_EN_ROUTE".equalsIgnoreCase(order.getStatus().name())) {
            return OrderLifecycleStage.PICKED_UP;
        }
        if (order.getArrivedPickupAt() != null) {
            return OrderLifecycleStage.ARRIVED_PICKUP;
        }
        if (offerSnapshot != null && (offerSnapshot.stage() == com.routechain.api.dto.OrderOfferStage.ACCEPTED
                || offerSnapshot.stage() == com.routechain.api.dto.OrderOfferStage.LOCKED_ASSIGNMENT)) {
            return OrderLifecycleStage.ACCEPTED;
        }
        if (offerSnapshot != null && offerSnapshot.stage() != com.routechain.api.dto.OrderOfferStage.NONE) {
            return OrderLifecycleStage.OFFERED;
        }
        return OrderLifecycleStage.CREATED;
    }

    private List<OrderLifecycleEventView> compatibilityHistory(List<OrderStatusHistoryRecord> historyRecords) {
        if (historyRecords == null || historyRecords.isEmpty()) {
            return List.of();
        }
        return historyRecords.stream()
                .sorted(Comparator.comparing(OrderStatusHistoryRecord::recordedAt))
                .map(record -> new OrderLifecycleEventView(
                        stageForHistoryRecord(record.status(), record.reason()),
                        normalizeStatus(record.status()),
                        record.reason(),
                        record.recordedAt() == null ? null : record.recordedAt().toString()))
                .toList();
    }

    private OrderLifecycleStage stageForHistoryRecord(String status, String reason) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedReason = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedStatus) {
            case "OFFERED" -> OrderLifecycleStage.OFFERED;
            case "ASSIGNED", "PICKUP_EN_ROUTE" -> OrderLifecycleStage.ACCEPTED;
            case "ARRIVED_PICKUP" -> OrderLifecycleStage.ARRIVED_PICKUP;
            case "PICKED_UP", "DROPOFF_EN_ROUTE" -> OrderLifecycleStage.PICKED_UP;
            case "ARRIVED_DROPOFF" -> OrderLifecycleStage.ARRIVED_DROPOFF;
            case "DELIVERED" -> OrderLifecycleStage.DROPPED_OFF;
            case "CANCELLED" -> OrderLifecycleStage.CANCELLED;
            case "FAILED" -> OrderLifecycleStage.FAILED;
            case "EXPIRED" -> OrderLifecycleStage.EXPIRED;
            case "QUOTED", "CONFIRMED", "PENDING_ASSIGNMENT" ->
                    "offers_published".equals(normalizedReason) ? OrderLifecycleStage.OFFERED : OrderLifecycleStage.CREATED;
            default -> OrderLifecycleStage.CREATED;
        };
    }

    private boolean isTerminal(String status) {
        return "DELIVERED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(status);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return "UNKNOWN";
        }
        return status.trim().toUpperCase(Locale.ROOT)
                .replace('-', '_')
                .replace(' ', '_');
    }
}
