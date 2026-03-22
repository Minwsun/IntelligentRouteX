package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * Layer 6 — Assignment Solver.
 * Greedy conflict-free driver-plan matching.
 * After scoring all plans, selects the best non-conflicting assignments:
 * no driver assigned twice, no order assigned twice.
 */
public class AssignmentSolver {

    /**
     * Select best non-conflicting plans from scored candidates.
     */
    public List<DispatchPlan> solve(List<DispatchPlan> scoredPlans) {
        // Sort by total score descending
        scoredPlans.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());

        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<DispatchPlan> selected = new ArrayList<>();

        for (DispatchPlan plan : scoredPlans) {
            String driverId = plan.getDriver().getId();
            if (usedDrivers.contains(driverId)) continue;

            // Check no order in this bundle is already assigned
            boolean conflict = false;
            for (Order order : plan.getOrders()) {
                if (usedOrders.contains(order.getId())) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) continue;

            // Confidence gate
            if (plan.getConfidence() < 0.15) continue;

            // Accept
            selected.add(plan);
            usedDrivers.add(driverId);
            for (Order order : plan.getOrders()) {
                usedOrders.add(order.getId());
            }
        }

        return selected;
    }

    /**
     * Fallback heuristic: simple nearest-driver assignment for unassigned orders.
     */
    public List<DispatchPlan> fallback(List<Order> unassignedOrders,
                                       List<Driver> availableDrivers) {
        List<DispatchPlan> plans = new ArrayList<>();
        Set<String> usedDrivers = new HashSet<>();

        for (Order order : unassignedOrders) {
            Driver best = null;
            double bestDist = Double.MAX_VALUE;

            for (Driver d : availableDrivers) {
                if (usedDrivers.contains(d.getId())) continue;
                if (d.getState() == DriverState.OFFLINE) continue;
                if (d.getCurrentOrderCount() >= 3) continue;

                double dist = d.getCurrentLocation().distanceTo(order.getPickupPoint());
                if (dist < bestDist) {
                    bestDist = dist;
                    best = d;
                }
            }

            if (best != null) {
                DispatchPlan.Bundle bundle = new DispatchPlan.Bundle(
                        "FBK-" + order.getId(), List.of(order), 0, 1);

                double speedKmh = Math.max(8.0, 25.0);
                double pickupMin = (bestDist / 1000.0 / speedKmh) * 60.0;
                double deliveryDist = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
                double deliveryMin = pickupMin + (deliveryDist / speedKmh) * 60.0;

                List<DispatchPlan.Stop> seq = List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(),
                                DispatchPlan.Stop.StopType.PICKUP, pickupMin),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(),
                                DispatchPlan.Stop.StopType.DROPOFF, deliveryMin));

                DispatchPlan plan = new DispatchPlan(best, bundle, seq);
                plan.setTotalScore(0.1);
                plan.setConfidence(0.3);
                plan.setTraceId("FALLBACK");

                plans.add(plan);
                usedDrivers.add(best.getId());
            }
        }

        return plans;
    }
}
