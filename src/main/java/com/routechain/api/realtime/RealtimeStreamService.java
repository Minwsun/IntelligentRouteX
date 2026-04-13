package com.routechain.api.realtime;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.data.port.OrderRepository;
import com.routechain.domain.Order;
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
 * Realtime hub that keeps stream clients aligned to one projection-backed authority path.
 * Runtime map details are still included, but they ride on the same authoritative snapshot.
 */
@Service
public class RealtimeStreamService {
    private final EventBus eventBus = EventBus.getInstance();
    private final OrderRepository orderRepository;
    private final RuntimeBridge runtimeBridge;
    private final Gson gson = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(Instant.class, (com.google.gson.JsonSerializer<Instant>) (src, typeOfSrc, context) ->
                    src == null ? null : context.serialize(src.toString()))
            .create();
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
        sendSingle(session, readyEnvelope("DRIVER", driverId));
        sendSingle(session, snapshotEnvelope("DRIVER", "driver", driverId, runtimeBridge.driverRealtimeSnapshot(driverId)));
    }

    public void registerUser(String customerId, WebSocketSession session) {
        userSessions.computeIfAbsent(customerId, ignored -> new CopyOnWriteArraySet<>()).add(session);
        sendSingle(session, readyEnvelope("USER", customerId));
        sendSingle(session, snapshotEnvelope("USER", "customer", customerId, runtimeBridge.userRealtimeSnapshot(customerId)));
    }

    public void registerOps(WebSocketSession session) {
        opsSessions.add(session);
        sendSingle(session, readyEnvelope("OPS", "ops"));
        sendSingle(session, snapshotEnvelope("OPS", "ops", "global", runtimeBridge.opsRealtimeSnapshot()));
    }

    public void unregister(WebSocketSession session) {
        driverSessions.values().forEach(sessions -> sessions.remove(session));
        userSessions.values().forEach(sessions -> sessions.remove(session));
        opsSessions.remove(session);
    }

    private void subscribeToEvents() {
        eventBus.subscribe(Events.DriverOfferCreated.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "driver_offer_created", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "driver_offer_created", event);
            publishOpsEvent("ORDER", event.orderId(), "driver_offer_created", event);
        });
        eventBus.subscribe(Events.OfferBatchCreated.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "offer_batch_created", event);
            publishOpsEvent("ORDER", event.orderId(), "offer_batch_created", event);
        });
        eventBus.subscribe(Events.OfferBatchClosed.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "offer_batch_closed", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "offer_batch_closed", event);
            publishOpsEvent("ORDER", event.orderId(), "offer_batch_closed", event);
        });
        eventBus.subscribe(Events.OrderReoffered.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_reoffered", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_reoffered", event);
            publishOpsEvent("ORDER", event.orderId(), "order_reoffered", event);
        });
        eventBus.subscribe(Events.DriverOfferAccepted.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "driver_offer_accepted", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "driver_offer_accepted", event);
            publishOpsEvent("ORDER", event.orderId(), "driver_offer_accepted", event);
        });
        eventBus.subscribe(Events.AssignmentLocked.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "assignment_locked", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "assignment_locked", event);
            publishOpsEvent("ORDER", event.orderId(), "assignment_locked", event);
        });
        eventBus.subscribe(Events.DriverOfferDeclined.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "driver_offer_declined", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "driver_offer_declined", event);
            publishOpsEvent("ORDER", event.orderId(), "driver_offer_declined", event);
        });
        eventBus.subscribe(Events.DriverOfferLost.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "driver_offer_lost", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "driver_offer_lost", event);
            publishOpsEvent("ORDER", event.orderId(), "driver_offer_lost", event);
        });
        eventBus.subscribe(Events.DriverOfferExpired.class, event -> {
            publishDriverEvent(event.driverId(), "ORDER", event.orderId(), "driver_offer_expired", event);
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "driver_offer_expired", event);
            publishOpsEvent("ORDER", event.orderId(), "driver_offer_expired", event);
        });
        eventBus.subscribe(Events.OrderCreated.class, event -> {
            publishUserEvent(event.order().getCustomerId(), "ORDER", event.order().getId(), "order_created", event.order());
            publishOpsEvent("ORDER", event.order().getId(), "order_created", event.order());
        });
        eventBus.subscribe(Events.OrderAssigned.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_assigned", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_assigned", event);
            publishOpsEvent("ORDER", event.orderId(), "order_assigned", event);
        });
        eventBus.subscribe(Events.OrderPickedUp.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_picked_up", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_picked_up", event);
            publishOpsEvent("ORDER", event.orderId(), "order_picked_up", event);
        });
        eventBus.subscribe(Events.OrderDelivered.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_delivered", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_delivered", event);
            publishOpsEvent("ORDER", event.orderId(), "order_delivered", event);
        });
        eventBus.subscribe(Events.OrderCancelled.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_cancelled", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_cancelled", event);
            publishOpsEvent("ORDER", event.orderId(), "order_cancelled", event);
        });
        eventBus.subscribe(Events.OrderFailed.class, event -> {
            publishUserEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_failed", event);
            publishAssignedDriverEventForOrder(event.orderId(), "ORDER", event.orderId(), "order_failed", event);
            publishOpsEvent("ORDER", event.orderId(), "order_failed", event);
        });
        eventBus.subscribe(Events.DriverLocationUpdated.class, event -> {
            publishDriverEvent(event.driverId(), "DRIVER", event.driverId(), "driver_location_updated", event);
            orderRepository.allOrders().stream()
                    .filter(order -> event.driverId().equals(order.getAssignedDriverId()))
                    .filter(order -> !isTerminal(order.getStatus().name()))
                    .forEach(order -> {
                        publishUserEvent(order.getCustomerId(), "ORDER", order.getId(), "driver_location_updated", event);
                        publishOpsEvent("ORDER", order.getId(), "driver_location_updated", event);
                    });
        });
    }

    private void publishAssignedDriverEventForOrder(String orderId, String entityType, String entityId, String topic, Object payload) {
        orderRepository.findOrder(orderId)
                .map(Order::getAssignedDriverId)
                .filter(driverId -> driverId != null && !driverId.isBlank())
                .ifPresent(driverId -> publishDriverEvent(driverId, entityType, entityId, topic, payload));
    }

    private void publishUserEventForOrder(String orderId, String entityType, String entityId, String topic, Object payload) {
        orderRepository.findOrder(orderId)
                .ifPresent(order -> publishUserEvent(order.getCustomerId(), entityType, entityId, topic, payload));
    }

    private void publishDriverEvent(String driverId, String entityType, String entityId, String topic, Object payload) {
        publishToDriver(driverId, eventEnvelope(
                "DRIVER",
                entityType,
                entityId,
                topic,
                payload,
                runtimeBridge.driverRealtimeSnapshot(driverId)));
    }

    private void publishUserEvent(String customerId, String entityType, String entityId, String topic, Object payload) {
        publishToUser(customerId, eventEnvelope(
                "USER",
                entityType,
                entityId,
                topic,
                payload,
                runtimeBridge.userRealtimeSnapshot(customerId)));
    }

    private void publishOpsEvent(String entityType, String entityId, String topic, Object payload) {
        publishToOps(eventEnvelope(
                "OPS",
                entityType,
                entityId,
                topic,
                payload,
                runtimeBridge.opsRealtimeSnapshot()));
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

    private String readyEnvelope(String audience, String subjectId) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "stream.ready");
        envelope.put("audience", audience);
        envelope.put("subjectId", subjectId);
        envelope.put("sentAt", Instant.now().toString());
        return gson.toJson(envelope);
    }

    private String snapshotEnvelope(String audience, String entityType, String entityId, Object snapshot) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "stream.snapshot");
        envelope.put("audience", audience);
        envelope.put("entityType", entityType);
        envelope.put("entityId", entityId);
        envelope.put("sentAt", Instant.now().toString());
        envelope.put("snapshot", snapshot);
        return gson.toJson(envelope);
    }

    private String eventEnvelope(String audience,
                                 String entityType,
                                 String entityId,
                                 String topic,
                                 Object payload,
                                 Object snapshot) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "stream.event");
        envelope.put("audience", audience);
        envelope.put("entityType", entityType);
        envelope.put("entityId", entityId);
        envelope.put("topic", topic);
        envelope.put("sentAt", Instant.now().toString());
        envelope.put("payload", payload);
        envelope.put("snapshot", snapshot);
        return gson.toJson(envelope);
    }

    private boolean isTerminal(String status) {
        return "DELIVERED".equalsIgnoreCase(status)
                || "CANCELLED".equalsIgnoreCase(status)
                || "FAILED".equalsIgnoreCase(status)
                || "EXPIRED".equalsIgnoreCase(status);
    }
}
