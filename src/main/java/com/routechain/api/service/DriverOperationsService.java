package com.routechain.api.service;

import com.routechain.api.dto.DriverAvailabilityUpdate;
import com.routechain.api.dto.DriverLocationUpdate;
import com.routechain.api.dto.DriverLoginRequest;
import com.routechain.api.dto.DriverTaskStatusUpdate;
import com.routechain.backend.offer.DriverOfferStatus;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.data.model.OrderStatusHistoryRecord;
import com.routechain.data.port.DriverFleetRepository;
import com.routechain.data.port.OrderRepository;
import com.routechain.data.service.IdempotencyService;
import com.routechain.data.service.OperationalEventPublisher;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.infra.Events;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DriverOperationsService {
    private final DriverFleetRepository driverFleetRepository;
    private final OrderRepository orderRepository;
    private final OfferBrokerService offerBrokerService;
    private final OpsArtifactService opsArtifactService;
    private final IdempotencyService idempotencyService;
    private final OperationalEventPublisher eventPublisher;

    public DriverOperationsService(DriverFleetRepository driverFleetRepository,
                                   OrderRepository orderRepository,
                                   OfferBrokerService offerBrokerService,
                                   OpsArtifactService opsArtifactService,
                                   IdempotencyService idempotencyService,
                                   OperationalEventPublisher eventPublisher) {
        this.driverFleetRepository = driverFleetRepository;
        this.orderRepository = orderRepository;
        this.offerBrokerService = offerBrokerService;
        this.opsArtifactService = opsArtifactService;
        this.idempotencyService = idempotencyService;
        this.eventPublisher = eventPublisher;
    }

    public DriverSessionState login(DriverLoginRequest request) {
        DriverSessionState state = new DriverSessionState(
                request.driverId(),
                request.deviceId(),
                true,
                request.lat(),
                request.lng(),
                Instant.now(),
                ""
        );
        driverFleetRepository.saveDriverSession(state);
        eventPublisher.publish("driver.session_started.v1", "DRIVER", request.driverId(), new Events.DriverOnline(request.driverId()));
        return state;
    }

    public Optional<DriverSessionState> heartbeat(String driverId) {
        return driverFleetRepository.findDriverSession(driverId).map(existing -> {
            DriverSessionState updated = existing.withHeartbeat();
            driverFleetRepository.saveDriverSession(updated);
            return updated;
        });
    }

    public Optional<DriverSessionState> setAvailability(String driverId, DriverAvailabilityUpdate request) {
        return driverFleetRepository.findDriverSession(driverId).map(existing -> {
            DriverSessionState updated = existing.withAvailability(request.available());
            driverFleetRepository.saveDriverSession(updated);
            return updated;
        });
    }

    public Optional<DriverSessionState> updateLocation(String driverId, DriverLocationUpdate request) {
        return driverFleetRepository.findDriverSession(driverId).map(existing -> {
            Instant now = Instant.now();
            DriverSessionState updated = existing.withLocation(request.lat(), request.lng());
            driverFleetRepository.saveDriverSession(updated);
            driverFleetRepository.recordDriverLocation(driverId, new GeoPoint(request.lat(), request.lng()), now);
            eventPublisher.publish("driver.location_updated.v1", "DRIVER", driverId,
                    new Events.DriverLocationUpdated(driverId, new GeoPoint(request.lat(), request.lng()), request.speedKmh()));
            return updated;
        });
    }

    public List<OfferBrokerService.OfferView> offers(String driverId) {
        return offerBrokerService.offersForDriver(driverId);
    }

    @Transactional
    public OfferDecision accept(String driverId, String offerId, String idempotencyKey) {
        return idempotencyService.executeOnce(
                "driver.accept_offer",
                driverId,
                idempotencyKey,
                OfferDecision.class,
                () -> {
                    OfferDecision decision = offerBrokerService.acceptOffer(offerId, driverId);
                    if (decision.status() == DriverOfferStatus.ACCEPTED) {
                        Instant now = Instant.now();
                        orderRepository.findOrder(decision.orderId()).ifPresent(order -> {
                            order.assignDriver(driverId, now);
                            orderRepository.saveOrder(order);
                            orderRepository.appendStatusHistory(statusHistory(order.getId(), order.getStatus().name(), "offer_accepted", now));
                            eventPublisher.publish("assignment.created.v1", "ORDER", order.getId(), new Events.OrderAssigned(order.getId(), driverId));
                        });
                        driverFleetRepository.findDriverSession(driverId).ifPresent(existing ->
                                driverFleetRepository.saveDriverSession(existing.withActiveOffer(offerId)));
                    }
                    return decision;
                }
        );
    }

    public OfferDecision decline(String driverId, String offerId, String reason) {
        return offerBrokerService.declineOffer(offerId, driverId, reason);
    }

    @Transactional
    public Optional<Order> updateTaskStatus(String taskId, DriverTaskStatusUpdate request) {
        String orderId = taskId.startsWith("task-") ? taskId.substring("task-".length()) : taskId;
        return orderRepository.findOrder(orderId).map(order -> {
            Instant now = Instant.now();
            String status = request.status().trim().toUpperCase();
            switch (status) {
                case "PICKUP_EN_ROUTE" -> order.markPickupStarted(now);
                case "PICKED_UP" -> order.markPickedUp(now);
                case "DROPOFF_EN_ROUTE" -> order.markDropoffStarted(now);
                case "DELIVERED" -> {
                    order.markDelivered(now);
                    eventPublisher.publish("task.status_changed.v1", "ORDER", order.getId(), new Events.OrderDelivered(order.getId()));
                }
                case "FAILED" -> {
                    order.markFailed("driver_reported_failure", now);
                    eventPublisher.publish("task.status_changed.v1", "ORDER", order.getId(), new Events.OrderFailed(order.getId(), order.getFailureReason()));
                }
                default -> {
                }
            }
            orderRepository.saveOrder(order);
            orderRepository.appendStatusHistory(statusHistory(order.getId(), order.getStatus().name(), status.toLowerCase(), now));
            return order;
        });
    }

    public List<OpsArtifactService.DriverCopilotRow> copilot(String driverId) {
        return opsArtifactService.driverFutureValues(driverId, 5);
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
