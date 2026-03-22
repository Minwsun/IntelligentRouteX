package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;

import java.util.*;

/**
 * Layer 4 — Sequence Optimizer.
 * Uses Constrained Cheapest Insertion + Local Improvement to determine
 * the optimal pickup/dropoff order for a bundle.
 *
 * Rules:
 * - Pickup must precede dropoff for each order
 * - Insertion cost minimized
 * - Late risk must not exceed threshold
 */
public class SequenceOptimizer {

    private final double trafficIntensity;
    private final Enums.WeatherProfile weather;

    public SequenceOptimizer(double trafficIntensity, Enums.WeatherProfile weather) {
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;
    }

    /**
     * Generate up to maxCandidates feasible stop sequences for a bundle.
     */
    public List<List<Stop>> generateFeasibleSequences(
            Driver driver, DispatchPlan.Bundle bundle, int maxCandidates) {

        List<Order> orders = bundle.orders();
        if (orders.size() == 1) {
            return List.of(singleOrderSequence(driver, orders.get(0)));
        }

        // Build initial sequence using cheapest insertion
        List<Stop> baseSequence = buildInitialSequence(driver, orders);

        List<List<Stop>> candidates = new ArrayList<>();
        candidates.add(baseSequence);

        // Generate variations via local improvement
        for (int i = 0; i < Math.min(orders.size() * 2, maxCandidates - 1); i++) {
            List<Stop> variant = localImprovement(baseSequence, orders, i);
            if (variant != null && isFeasible(variant, orders)) {
                candidates.add(variant);
            }
        }

        // Compute arrival times for all candidates
        for (List<Stop> seq : candidates) {
            computeArrivalTimes(driver.getCurrentLocation(), seq);
        }

        // Sort by total route time
        candidates.sort(Comparator.comparingDouble(this::totalRouteTime));

        return candidates.subList(0, Math.min(candidates.size(), maxCandidates));
    }

    // ── Cheapest insertion ──────────────────────────────────────────────

    private List<Stop> buildInitialSequence(Driver driver, List<Order> orders) {
        List<Stop> route = new ArrayList<>();

        // Start with first order (pickup then dropoff)
        Order first = orders.get(0);
        route.add(new Stop(first.getId(), first.getPickupPoint(), StopType.PICKUP, 0));
        route.add(new Stop(first.getId(), first.getDropoffPoint(), StopType.DROPOFF, 0));

        // Insert remaining orders one by one at cheapest position
        for (int i = 1; i < orders.size(); i++) {
            Order order = orders.get(i);
            insertCheapest(route, order, driver.getCurrentLocation());
        }

        return route;
    }

    private void insertCheapest(List<Stop> route, Order order, GeoPoint driverPos) {
        Stop pickup = new Stop(order.getId(), order.getPickupPoint(), StopType.PICKUP, 0);
        Stop dropoff = new Stop(order.getId(), order.getDropoffPoint(), StopType.DROPOFF, 0);

        double bestCost = Double.MAX_VALUE;
        int bestPickupPos = -1;
        int bestDropoffPos = -1;

        // Try all valid insertion positions
        for (int pPos = 0; pPos <= route.size(); pPos++) {
            for (int dPos = pPos + 1; dPos <= route.size() + 1; dPos++) {
                // Create trial route
                List<Stop> trial = new ArrayList<>(route);
                trial.add(pPos, pickup);
                trial.add(dPos, dropoff);

                if (!isOrderConstraintSatisfied(trial)) continue;

                double cost = computeRouteCost(driverPos, trial);
                if (cost < bestCost) {
                    bestCost = cost;
                    bestPickupPos = pPos;
                    bestDropoffPos = dPos;
                }
            }
        }

        if (bestPickupPos >= 0) {
            route.add(bestPickupPos, pickup);
            route.add(bestDropoffPos, dropoff);
        } else {
            // Fallback: append at end
            route.add(pickup);
            route.add(dropoff);
        }
    }

    // ── Local improvement ───────────────────────────────────────────────

    private List<Stop> localImprovement(List<Stop> base, List<Order> orders, int iteration) {
        List<Stop> improved = new ArrayList<>(base);

        if (improved.size() < 4) return null;

        // Swap adjacent dropoffs that don't violate pickup-before-dropoff
        int swapIdx = (iteration * 2 + 1) % (improved.size() - 1);
        if (swapIdx > 0 && swapIdx < improved.size() - 1) {
            Stop a = improved.get(swapIdx);
            Stop b = improved.get(swapIdx + 1);

            // Only swap if both are dropoffs or both are pickups
            if (a.type() == b.type()) {
                improved.set(swapIdx, b);
                improved.set(swapIdx + 1, a);

                if (!isOrderConstraintSatisfied(improved)) {
                    // Revert
                    improved.set(swapIdx, a);
                    improved.set(swapIdx + 1, b);
                    return null;
                }
            }
        }

        return improved;
    }

    // ── Feasibility ─────────────────────────────────────────────────────

    private boolean isFeasible(List<Stop> sequence, List<Order> orders) {
        return isOrderConstraintSatisfied(sequence);
    }

    /** Ensure pickup comes before dropoff for every order. */
    private boolean isOrderConstraintSatisfied(List<Stop> sequence) {
        Set<String> pickedUp = new HashSet<>();
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                pickedUp.add(stop.orderId());
            } else if (stop.type() == StopType.DROPOFF) {
                if (!pickedUp.contains(stop.orderId())) return false;
            }
        }
        return true;
    }

    // ── Cost computation ────────────────────────────────────────────────

    private double computeRouteCost(GeoPoint start, List<Stop> sequence) {
        double totalDist = 0;
        GeoPoint prev = start;
        for (Stop stop : sequence) {
            totalDist += prev.distanceTo(stop.location());
            prev = stop.location();
        }
        return totalDist / 1000.0; // km
    }

    private double totalRouteTime(List<Stop> sequence) {
        if (sequence.isEmpty()) return 0;
        return sequence.get(sequence.size() - 1).estimatedArrivalMinutes();
    }

    private void computeArrivalTimes(GeoPoint driverPos, List<Stop> sequence) {
        double speedKmh = estimateSpeed();
        double elapsed = 0;
        GeoPoint prev = driverPos;

        List<Stop> updated = new ArrayList<>();
        for (Stop stop : sequence) {
            double distKm = prev.distanceTo(stop.location()) / 1000.0;
            elapsed += (distKm / speedKmh) * 60.0; // minutes
            updated.add(new Stop(stop.orderId(), stop.location(), stop.type(), elapsed));
            prev = stop.location();
        }

        // Replace in-place (we have to clear and re-add since list is mutable)
        sequence.clear();
        sequence.addAll(updated);
    }

    private double estimateSpeed() {
        double speedKmh = 25.0 * (1.0 - trafficIntensity * 0.5);
        if (weather == Enums.WeatherProfile.HEAVY_RAIN) speedKmh *= 0.7;
        if (weather == Enums.WeatherProfile.STORM) speedKmh *= 0.4;
        return Math.max(8.0, speedKmh);
    }

    private List<Stop> singleOrderSequence(Driver driver, Order order) {
        double speedKmh = estimateSpeed();
        double pickupDist = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        double deliveryDist = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;

        double pickupMinutes = (pickupDist / speedKmh) * 60.0;
        double deliveryMinutes = pickupMinutes + (deliveryDist / speedKmh) * 60.0;

        return List.of(
                new Stop(order.getId(), order.getPickupPoint(), StopType.PICKUP, pickupMinutes),
                new Stop(order.getId(), order.getDropoffPoint(), StopType.DROPOFF, deliveryMinutes)
        );
    }
}
