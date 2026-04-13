package com.routechain.api.realtime;

import com.google.gson.Gson;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.data.port.OrderRepository;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Small realtime event hub for driver/user/ops clients.
 * Keeps streams grounded in the same event tape used by the control room.
 */
@Service
public class RealtimeStreamService {
    private final EventBus eventBus = EventBus.getInstance();
    private final OrderRepository orderRepository;
    private final RuntimeBridge runtimeBridge;
    private final Gson gson = new Gson();
    private final Map<String, Set<WebSocketSession>> driverSessions = new ConcurrentHashMap<>();
    private final Map<String, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final Set<WebSocketSession> opsSessions = new CopyOnWriteArraySet<>();

    public RealtimeStreamService(OrderRepository orderRepository,
                                 RuntimeBridge runtimeBridge) {
        this.orderRepository = orderRepository;
        this.runtimeBridge = runtimeBridge;
        subscribeToEvents();
    }

    public void registerDriver(String driverId, WebSocketSession session) {
        driverSessions.computeIfAbsent(driverId, ignored -> new CopyOnWriteArraySet<>()).add(session);
        sendSingle(session, envelope("driver_stream_ready", Map.of("driverId", driverId)));
        sendSingle(session, envelope("driver_bootstrap", payloadMap(
                "offers", runtimeBridge.driverOffers(driverId),
                "activeTask", runtimeBridge.activeTask(driverId).orElse(null),
                "mapSnapshot", runtimeBridge.driverMapSnapshot(driverId))));
    }

    public void registerUser(String customerId, WebSocketSession session) {
        userSessions.computeIfAbsent(customerId, ignored -> new CopyOnWriteArraySet<>()).add(session);
        sendSingle(session, envelope("user_stream_ready", Map.of("customerId", customerId)));
        sendSingle(session, envelope("user_bootstrap", payloadMap(
                "activeTrip", runtimeBridge.activeTripForCustomer(customerId).orElse(null),
                "mapSnapshot", runtimeBridge.userMapSnapshot(customerId, null))));
    }

    public void registerOps(WebSocketSession session) {
        opsSessions.add(session);
        sendSingle(session, envelope("ops_stream_ready", Map.of("connectedAt", Instant.now().toString())));
    }

    public void unregister(WebSocketSession session) {
        driverSessions.values().forEach(sessions -> sessions.remove(session));
        userSessions.values().forEach(sessions -> sessions.remove(session));
        opsSessions.remove(session);
    }

    private void subscribeToEvents() {
        eventBus.subscribe(Events.DriverOfferCreated.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_offer_created", event));
            publishDriverBootstrap(event.driverId());
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("driver_offer_created", event));
        });
        eventBus.subscribe(Events.OfferBatchCreated.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_offer_batch_created", event);
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("order_offer_batch_created", event));
        });
        eventBus.subscribe(Events.OfferBatchClosed.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_offer_batch_closed", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
            publishToOps(envelope("order_offer_batch_closed", event));
        });
        eventBus.subscribe(Events.OrderReoffered.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_reoffered", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
            publishToOps(envelope("order_reoffered", event));
        });
        eventBus.subscribe(Events.DriverOfferAccepted.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_offer_accepted", event));
            publishDriverBootstrap(event.driverId());
            publishOrderEventToCustomer(event.orderId(), "order_offer_accepted", event);
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("driver_offer_accepted", event));
        });
        eventBus.subscribe(Events.AssignmentLocked.class, event -> {
            publishToDriver(event.driverId(), envelope("assignment_locked", event));
            publishDriverBootstrap(event.driverId());
            publishOrderEventToCustomer(event.orderId(), "assignment_locked", event);
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("assignment_locked", event));
        });
        eventBus.subscribe(Events.DriverOfferDeclined.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_offer_declined", event));
            publishDriverBootstrap(event.driverId());
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("driver_offer_declined", event));
        });
        eventBus.subscribe(Events.DriverOfferLost.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_offer_lost", event));
            publishDriverBootstrap(event.driverId());
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("driver_offer_lost", event));
        });
        eventBus.subscribe(Events.DriverOfferExpired.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_offer_expired", event));
            publishDriverBootstrap(event.driverId());
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("driver_offer_expired", event));
        });
        eventBus.subscribe(Events.OrderCreated.class, event ->
                publishToUser(event.order().getCustomerId(), envelope("order_created", event.order())));
        eventBus.subscribe(Events.OrderAssigned.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_assigned", event);
            publishCustomerSnapshot(event.orderId());
            publishToOps(envelope("order_assigned", event));
        });
        eventBus.subscribe(Events.OrderPickedUp.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_picked_up", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
        });
        eventBus.subscribe(Events.OrderDelivered.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_delivered", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
            publishToOps(envelope("order_delivered", event));
        });
        eventBus.subscribe(Events.OrderCancelled.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_cancelled", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
            publishToOps(envelope("order_cancelled", event));
        });
        eventBus.subscribe(Events.OrderFailed.class, event -> {
            publishOrderEventToCustomer(event.orderId(), "order_failed", event);
            publishCustomerSnapshot(event.orderId());
            publishDriverSnapshot(event.orderId());
            publishToOps(envelope("order_failed", event));
        });
        eventBus.subscribe(Events.DriverLocationUpdated.class, event -> {
            publishToDriver(event.driverId(), envelope("driver_location_updated", event));
            publishToDriver(event.driverId(), envelope("driver_map_snapshot", runtimeBridge.driverMapSnapshot(event.driverId())));
            orderRepository.allOrders().stream()
                    .filter(order -> event.driverId().equals(order.getAssignedDriverId()))
                    .filter(order -> !isTerminal(order.getStatus().name()))
                    .forEach(order -> {
                        publishOrderEventToCustomer(order.getId(), "trip_driver_location", event);
                        publishToUser(order.getCustomerId(), envelope("user_map_snapshot",
                                runtimeBridge.userMapSnapshot(order.getCustomerId(), order.getId())));
                    });
        });
    }

    private void publishOrderEventToCustomer(String orderId, String type, Object payload) {
        orderRepository.findOrder(orderId).ifPresent(order -> publishToUser(order.getCustomerId(), envelope(type, payload)));
    }

    private void publishCustomerSnapshot(String orderId) {
        orderRepository.findOrder(orderId).ifPresent(order -> publishToUser(
                order.getCustomerId(),
                envelope("user_map_snapshot", runtimeBridge.userMapSnapshot(order.getCustomerId(), order.getId()))));
    }

    private void publishDriverSnapshot(String orderId) {
        orderRepository.findOrder(orderId)
                .filter(order -> order.getAssignedDriverId() != null && !order.getAssignedDriverId().isBlank())
                .ifPresent(order -> {
                    publishDriverBootstrap(order.getAssignedDriverId());
                });
    }

    private void publishDriverBootstrap(String driverId) {
        publishToDriver(driverId, envelope("driver_offers_snapshot", runtimeBridge.driverOffers(driverId)));
        publishToDriver(driverId, envelope("driver_active_task", runtimeBridge.activeTask(driverId).orElse(null)));
        publishToDriver(driverId, envelope("driver_map_snapshot", runtimeBridge.driverMapSnapshot(driverId)));
    }

    private void publishToDriver(String driverId, String json) {
        publish(driverSessions.get(driverId), json);
    }

    private void publishToUser(String customerId, String json) {
        publish(userSessions.get(customerId), json);
    }

    private void publishToOps(String json) {
        publish(opsSessions, json);
    }

    private void publish(Set<WebSocketSession> sessions, String json) {
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        for (WebSocketSession session : sessions) {
            sendSingle(session, json);
        }
    }

    private void sendSingle(WebSocketSession session, String json) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            try {
                session.sendMessage(new TextMessage(json));
            } catch (IOException ignored) {
            }
        }
    }

    private String envelope(String type, Object payload) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", type);
        envelope.put("sentAt", Instant.now().toString());
        envelope.put("payload", payload);
        return gson.toJson(envelope);
    }

    private Map<String, Object> payloadMap(Object... pairs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        if (pairs == null) {
            return payload;
        }
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            Object key = pairs[index];
            if (key instanceof String stringKey) {
                payload.put(stringKey, pairs[index + 1]);
            }
        }
        return payload;
    }

    private boolean isTerminal(String status) {
        return "DELIVERED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(status);
    }
}
