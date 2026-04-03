package com.routechain.api.service;

import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.dto.UserOrderResponse;
import com.routechain.api.dto.UserQuoteRequest;
import com.routechain.api.dto.UserQuoteResponse;
import com.routechain.backend.offer.DriverOfferBatch;
import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.data.model.QuoteRecord;
import com.routechain.data.port.OfferStateStore;
import com.routechain.data.port.OrderRepository;
import com.routechain.data.port.QuoteRepository;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.Events;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserOrderingService {
    private final OrderRepository orderRepository;
    private final QuoteRepository quoteRepository;
    private final OfferStateStore offerStateStore;
    private final DispatchOrchestratorService dispatchOrchestratorService;
    private final IdempotencyService idempotencyService;
    private final OperationalEventPublisher eventPublisher;

    public UserOrderingService(OrderRepository orderRepository,
                               QuoteRepository quoteRepository,
                               OfferStateStore offerStateStore,
                               DispatchOrchestratorService dispatchOrchestratorService,
                               IdempotencyService idempotencyService,
                               OperationalEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.quoteRepository = quoteRepository;
        this.offerStateStore = offerStateStore;
        this.dispatchOrchestratorService = dispatchOrchestratorService;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    public UserQuoteResponse quote(UserQuoteRequest request) {
        GeoPoint pickup = new GeoPoint(request.pickupLat(), request.pickupLng());
        GeoPoint dropoff = new GeoPoint(request.dropoffLat(), request.dropoffLng());
        double distanceKm = pickup.distanceTo(dropoff) / 1000.0;
        double baseFee = "2h".equalsIgnoreCase(request.serviceTier()) ? 18000.0 : 24000.0;
        double estimatedFee = baseFee + Math.max(0.0, distanceKm - 1.0) * 4500.0;
        int estimatedEta = Math.max(10, Math.min(request.promisedEtaMinutes(), (int) Math.round(distanceKm * 5.5 + 12)));
        String quoteId = "quote-" + UUID.randomUUID().toString().substring(0, 8);
        quoteRepository.storeQuote(new QuoteRecord(
                quoteId,
                request.customerId(),
                request.serviceTier(),
                distanceKm,
                estimatedFee,
                estimatedEta,
                Instant.now()
        ));
        return new UserQuoteResponse(quoteId, request.customerId(), request.serviceTier(), distanceKm, estimatedFee, estimatedEta);
    }

    @Transactional
    public UserOrderResponse createOrder(UserOrderRequest request, String idempotencyKey) {
        return idempotencyService.executeOnce(
                "user.create_order",
                request.customerId(),
                idempotencyKey,
                UserOrderResponse.class,
                () -> {
                    Instant now = Instant.now();
                    String orderId = "ord-" + UUID.randomUUID().toString().substring(0, 8);
                    GeoPoint pickup = new GeoPoint(request.pickupLat(), request.pickupLng());
                    GeoPoint dropoff = new GeoPoint(request.dropoffLat(), request.dropoffLng());
                    double distanceKm = pickup.distanceTo(dropoff) / 1000.0;
                    double quotedFee = ("2h".equalsIgnoreCase(request.serviceTier()) ? 18000.0 : 24000.0)
                            + Math.max(0.0, distanceKm - 1.0) * 4500.0;
                    int promisedEta = request.promisedEtaMinutes() <= 0
                            ? ("2h".equalsIgnoreCase(request.serviceTier()) ? 120 : 30)
                            : request.promisedEtaMinutes();

                    Order order = new Order(
                            orderId,
                            request.customerId(),
                            request.pickupRegionId(),
                            pickup,
                            dropoff,
                            request.dropoffRegionId(),
                            quotedFee,
                            promisedEta,
                            now
                    );
                    order.setServiceType(request.serviceTier());
                    order.setMerchantId(request.merchantId());
                    orderRepository.saveOrder(order);
                    orderRepository.appendStatusHistory(statusHistory(order.getId(), order.getStatus().name(), "order_created", now));
                    eventPublisher.publish("order.created.v1", "ORDER", order.getId(), new Events.OrderCreated(order));

                    DriverOfferBatch batch = dispatchOrchestratorService.publishOffersForOrder(order);
                    return toResponse(order, batch == null ? "" : batch.offerBatchId());
                }
        );
    }

    public Optional<UserOrderResponse> order(String orderId) {
        return orderRepository.findOrder(orderId)
                .map(order -> toResponse(order, latestOfferBatch(order.getId())));
    }

    @Transactional
    public Optional<UserOrderResponse> cancel(String orderId, String reason, String idempotencyKey) {
        return orderRepository.findOrder(orderId).map(order -> {
            String resolvedReason = reason == null || reason.isBlank() ? "user_cancelled" : reason;
            return idempotencyService.executeOnce(
                    "user.cancel_order",
                    order.getCustomerId(),
                    idempotencyKey,
                    UserOrderResponse.class,
                    () -> {
                        Instant now = Instant.now();
                        order.markCancelled(resolvedReason, now);
                        orderRepository.saveOrder(order);
                        orderRepository.appendStatusHistory(statusHistory(order.getId(), order.getStatus().name(), resolvedReason, now));
                        eventPublisher.publish("order.status_changed.v1", "ORDER", order.getId(),
                                new Events.OrderCancelled(order.getId(), order.getCancellationReason()));
                        return toResponse(order, latestOfferBatch(order.getId()));
                    }
            );
        });
    }

    private UserOrderResponse toResponse(Order order, String offerBatchId) {
        return new UserOrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getServiceType(),
                order.getStatus().name(),
                order.getQuotedFee(),
                order.getAssignedDriverId(),
                offerBatchId
        );
    }

    private String latestOfferBatch(String orderId) {
        return offerStateStore.latestBatchForOrder(orderId)
                .map(DriverOfferBatch::offerBatchId)
                .orElse("");
    }

    private OrderStatusHistoryRecord statusHistory(String orderId, String status, String reason, Instant recordedAt) {
        return new OrderStatusHistoryRecord(
                orderId,
                status,
                reason,
                recordedAt
        );
    }
}
