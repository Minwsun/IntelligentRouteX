package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events.*;

import java.util.*;

/**
 * Re-dispatch engine — monitors shock events and triggers localized replanning.
 *
 * Triggers:
 * - Traffic spike in a region
 * - Weather deterioration
 * - Driver going offline
 * - Pickup delay exceeding threshold
 *
 * Only replans orders currently in PICKUP_EN_ROUTE state where the
 * original assignment may no longer be optimal.
 */
public class ReDispatchEngine {

    private static final double TRAFFIC_SPIKE_THRESHOLD = 0.7;
    private static final double LATE_RISK_REPLAN_THRESHOLD = 0.6;
    private static final double PICKUP_DELAY_THRESHOLD_MIN = 5.0;

    private final EventBus eventBus = EventBus.getInstance();
    private volatile int reDispatchCount = 0;

    /**
     * Evaluate all in-progress orders and identify those needing re-dispatch.
     * Returns list of orders that should be replanned.
     */
    public List<Order> evaluateReDispatchCandidates(
            List<Order> activeOrders, List<Driver> drivers,
            double trafficIntensity, WeatherProfile weather) {

        List<Order> candidates = new ArrayList<>();

        for (Order order : activeOrders) {
            if (order.getStatus() != OrderStatus.PICKUP_EN_ROUTE) continue;
            if (order.getAssignedDriverId() == null) continue;

            Driver assignedDriver = drivers.stream()
                    .filter(d -> d.getId().equals(order.getAssignedDriverId()))
                    .findFirst().orElse(null);

            if (assignedDriver == null) {
                // Driver disappeared — must re-dispatch
                candidates.add(order);
                continue;
            }

            // Check if conditions have worsened since assignment
            boolean shouldReplan = false;
            String reason = "";

            // 1. Driver went offline
            if (assignedDriver.getState() == DriverState.OFFLINE) {
                shouldReplan = true;
                reason = "driver_offline";
            }

            // 2. Traffic spike making current route suboptimal
            if (!shouldReplan && trafficIntensity > TRAFFIC_SPIKE_THRESHOLD) {
                double currentDist = assignedDriver.getCurrentLocation()
                        .distanceTo(order.getPickupPoint());
                double etaMinutes = (currentDist / 1000.0) /
                        (25.0 * (1.0 - trafficIntensity * 0.6)) * 60.0;
                if (etaMinutes > order.getPromisedEtaMinutes() * 0.7) {
                    shouldReplan = true;
                    reason = "traffic_spike_eta_violation";
                }
            }

            // 3. Weather deterioration
            if (!shouldReplan && (weather == WeatherProfile.STORM)) {
                double remainingDist = assignedDriver.getCurrentLocation()
                        .distanceTo(order.getPickupPoint());
                if (remainingDist > 2000) { // still far from pickup
                    shouldReplan = true;
                    reason = "weather_deterioration";
                }
            }

            if (shouldReplan) {
                candidates.add(order);
                reDispatchCount++;
                eventBus.publish(new ReDispatchTriggered(order.getId(), reason));
            }
        }

        return candidates;
    }

    /**
     * Execute re-dispatch: unassign order from current driver and return to pending.
     */
    public void executeReDispatch(Order order, List<Driver> drivers) {
        String oldDriverId = order.getAssignedDriverId();

        // Reset order to pending
        order.setStatus(OrderStatus.PENDING_ASSIGNMENT);

        // Free up the old driver
        if (oldDriverId != null) {
            drivers.stream()
                    .filter(d -> d.getId().equals(oldDriverId))
                    .findFirst()
                    .ifPresent(driver -> {
                        driver.removeOrder(order.getId());
                        if (driver.getActiveOrderIds().isEmpty()) {
                            driver.setState(DriverState.ONLINE_IDLE);
                            driver.setTargetLocation(null);
                            driver.clearRouteWaypoints();
                        }
                    });
        }
    }

    public int getReDispatchCount() { return reDispatchCount; }

    public void reset() { reDispatchCount = 0; }
}
