package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Order;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OrderBuffer {
    private final RouteChainDispatchV2Properties.Buffer properties;
    private final Map<String, Instant> firstSeenAt = new LinkedHashMap<>();

    public OrderBuffer(RouteChainDispatchV2Properties.Buffer properties) {
        this.properties = properties;
    }

    public synchronized BufferedOrderWindow buffer(List<Order> openOrders, Instant decisionTime) {
        Instant now = decisionTime == null ? Instant.now() : decisionTime;
        Map<String, Order> liveOrders = new LinkedHashMap<>();
        for (Order order : openOrders) {
            if (order == null) {
                continue;
            }
            liveOrders.put(order.getId(), order);
            firstSeenAt.putIfAbsent(order.getId(), earliest(order, now));
        }
        firstSeenAt.keySet().removeIf(orderId -> !liveOrders.containsKey(orderId));

        Duration holdWindow = properties == null || properties.getHoldWindow() == null
                ? Duration.ofSeconds(45)
                : properties.getHoldWindow();
        Duration softReleaseWindow = properties == null || properties.getSoftReleaseWindow() == null
                ? Duration.ofSeconds(30)
                : properties.getSoftReleaseWindow();

        List<Order> released = new ArrayList<>();
        List<Order> held = new ArrayList<>();
        for (Order order : liveOrders.values()) {
            Instant seenAt = firstSeenAt.getOrDefault(order.getId(), now);
            Duration age = Duration.between(seenAt, now);
            if (!age.minus(holdWindow).isNegative() || isUrgent(order, now)) {
                released.add(order);
            } else {
                held.add(order);
            }
        }

        if (released.isEmpty() && !held.isEmpty()) {
            List<Order> oldestFirst = held.stream()
                    .sorted(Comparator.comparing(order -> firstSeenAt.getOrDefault(order.getId(), now)))
                    .toList();
            int softReleaseCount = properties == null ? 1 : Math.max(1, properties.getSoftReleaseOrderCount());
            boolean softWindowElapsed = oldestFirst.stream().anyMatch(order -> {
                Instant seenAt = firstSeenAt.getOrDefault(order.getId(), now);
                return !Duration.between(seenAt, now).minus(softReleaseWindow).isNegative();
            });
            if (!softWindowElapsed) {
                released.addAll(oldestFirst);
                held.clear();
            } else {
                released.addAll(oldestFirst.subList(0, Math.min(softReleaseCount, oldestFirst.size())));
                held.removeAll(released);
            }
        }

        return new BufferedOrderWindow(windowId(now, holdWindow), now, List.copyOf(released), List.copyOf(held));
    }

    private Instant earliest(Order order, Instant fallback) {
        if (order == null) {
            return fallback;
        }
        if (order.getCreatedAt() != null) {
            return order.getCreatedAt();
        }
        return fallback;
    }

    private boolean isUrgent(Order order, Instant now) {
        if (order == null || order.getCreatedAt() == null) {
            return false;
        }
        long elapsedMinutes = Math.max(0L, Duration.between(order.getCreatedAt(), now).toMinutes());
        return order.getPromisedEtaMinutes() - elapsedMinutes <= 15;
    }

    private String windowId(Instant decisionTime, Duration holdWindow) {
        long seconds = Math.max(1L, holdWindow.getSeconds());
        long bucket = decisionTime.getEpochSecond() / seconds;
        return "buffer-" + bucket;
    }
}
