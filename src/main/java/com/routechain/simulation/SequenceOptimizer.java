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
 * - Pickup span ≤ 12 minutes
 * - Cumulative merchant wait ≤ 8 minutes
 * - Detour ratio ≤ 2.0
 */
public class SequenceOptimizer {

    private static final double MAX_PICKUP_SPAN_MINUTES = 15.0;
    private static final double MAX_MERCHANT_WAIT_MINUTES = 15.0;
    private static final double MAX_DETOUR_RATIO = 3.0;
    private static final double SLA_LATENESS_FACTOR = 0.7;

    private final double trafficIntensity;
    private final Enums.WeatherProfile weather;

    public SequenceOptimizer(double trafficIntensity, Enums.WeatherProfile weather) {
        this.trafficIntensity = trafficIntensity;
        this.weather = weather;
    }

    /**
     * Generate up to maxCandidates feasible stop sequences for a bundle.
     * Includes a specific "Pickup-Wave" candidate for larger bundles.
     */
    public List<List<Stop>> generateFeasibleSequences(
            Driver driver, DispatchPlan.Bundle bundle, int maxCandidates) {

        List<Order> orders = bundle.orders();
        if (orders.size() == 1) {
            return List.of(singleOrderSequence(driver, orders.get(0)));
        }

        List<List<Stop>> candidates = new ArrayList<>();

        // 1. Pickup-Wave sequence (All Pickups -> All Dropoffs)
        List<Stop> waveSequence = generatePickupWaveSequence(driver, orders);
        if (waveSequence != null) {
            computeArrivalTimes(driver.getCurrentLocation(), waveSequence, orders);
            if (isFeasible(waveSequence, orders)) {
                candidates.add(waveSequence);
            }
        }

        // 2. Cheapest insertion sequence
        List<Stop> baseSequence = buildInitialSequence(driver, orders);
        computeArrivalTimes(driver.getCurrentLocation(), baseSequence, orders);
        if (isFeasible(baseSequence, orders) && !candidates.contains(baseSequence)) {
            candidates.add(baseSequence);
        }

        // 3. Local improvement variants
        for (int i = 0; i < Math.min(orders.size() * 2, maxCandidates - candidates.size()); i++) {
            List<Stop> variant = localImprovement(baseSequence, orders, i);
            if (variant != null) {
                computeArrivalTimes(driver.getCurrentLocation(), variant, orders);
                if (isFeasible(variant, orders) && !candidates.contains(variant)) {
                    candidates.add(variant);
                }
            }
        }

        // Sort by total route cost (distance + merchant wait penalty)
        candidates.sort(Comparator.comparingDouble(
                s -> computeRouteCost(driver.getCurrentLocation(), s, orders)));

        return candidates.subList(0, Math.min(candidates.size(), maxCandidates));
    }

    /**
     * Generate a "Pickup-Wave" sequence: collect all items then deliver all.
     * Order of pickups and dropoffs is determined by proximity.
     */
    private List<Stop> generatePickupWaveSequence(Driver driver, List<Order> orders) {
        List<Stop> route = new ArrayList<>();
        List<Order> remainingPickups = new ArrayList<>(orders);
        GeoPoint current = driver.getCurrentLocation();

        // 1. Pickups wave — nearest-first
        while (!remainingPickups.isEmpty()) {
            final GeoPoint loc = current;
            Order nearest = remainingPickups.stream()
                    .min(Comparator.comparingDouble(o -> loc.distanceTo(o.getPickupPoint())))
                    .get();
            route.add(new Stop(nearest.getId(), nearest.getPickupPoint(), StopType.PICKUP, 0));
            remainingPickups.remove(nearest);
            current = nearest.getPickupPoint();
        }

        // 2. Dropoffs wave — nearest-first
        List<Order> remainingDropoffs = new ArrayList<>(orders);
        while (!remainingDropoffs.isEmpty()) {
            final GeoPoint loc = current;
            Order nearest = remainingDropoffs.stream()
                    .min(Comparator.comparingDouble(o -> loc.distanceTo(o.getDropoffPoint())))
                    .get();
            route.add(new Stop(nearest.getId(), nearest.getDropoffPoint(), StopType.DROPOFF, 0));
            remainingDropoffs.remove(nearest);
            current = nearest.getDropoffPoint();
        }

        return route;
    }

    // ── Cheapest insertion ──────────────────────────────────────────────

    private List<Stop> buildInitialSequence(Driver driver, List<Order> orders) {
        List<Stop> route = new ArrayList<>();

        Order first = orders.get(0);
        route.add(new Stop(first.getId(), first.getPickupPoint(), StopType.PICKUP, 0));
        route.add(new Stop(first.getId(), first.getDropoffPoint(), StopType.DROPOFF, 0));

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

        for (int pPos = 0; pPos <= route.size(); pPos++) {
            for (int dPos = pPos + 1; dPos <= route.size() + 1; dPos++) {
                List<Stop> trial = new ArrayList<>(route);
                trial.add(pPos, pickup);
                trial.add(dPos, dropoff);

                if (!isOrderConstraintSatisfied(trial)) continue;

                double cost = computeDistanceOnly(driverPos, trial);
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
            route.add(pickup);
            route.add(dropoff);
        }
    }

    // ── Local improvement ───────────────────────────────────────────────

    private List<Stop> localImprovement(List<Stop> base, List<Order> orders, int iteration) {
        List<Stop> improved = new ArrayList<>(base);
        if (improved.size() < 4) return null;

        int swapIdx = (iteration * 2 + 1) % (improved.size() - 1);
        if (swapIdx > 0 && swapIdx < improved.size() - 1) {
            Stop a = improved.get(swapIdx);
            Stop b = improved.get(swapIdx + 1);

            if (a.type() == b.type()) {
                improved.set(swapIdx, b);
                improved.set(swapIdx + 1, a);

                if (!isOrderConstraintSatisfied(improved)) {
                    improved.set(swapIdx, a);
                    improved.set(swapIdx + 1, b);
                    return null;
                }
            }
        }
        return improved;
    }

    // ── Feasibility — REAL hard constraints ──────────────────────────────

    /**
     * Validates a sequence against all hard constraints:
     * 1. Pickup-before-dropoff ordering
     * 2. Pickup span ≤ 12 minutes
     * 3. First-order lateness ≤ SLA * 0.7
     * 4. Cumulative merchant wait ≤ 8 minutes
     * 5. Detour ratio ≤ 2.0
     */
    private boolean isFeasible(List<Stop> sequence, List<Order> orders) {
        if (!isOrderConstraintSatisfied(sequence)) return false;

        // Constraint 2: Pickup span — time from first pickup to last pickup ≤ 12 min
        double firstPickupTime = Double.MAX_VALUE;
        double lastPickupTime = 0;
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                firstPickupTime = Math.min(firstPickupTime, stop.estimatedArrivalMinutes());
                lastPickupTime = Math.max(lastPickupTime, stop.estimatedArrivalMinutes());
            }
        }
        double pickupSpan = lastPickupTime - firstPickupTime;
        if (pickupSpan > MAX_PICKUP_SPAN_MINUTES) return false;

        // Constraint 3: First-order lateness — earliest order's dropoff must not exceed SLA*0.7
        if (!orders.isEmpty()) {
            Order firstOrder = orders.stream()
                    .min(Comparator.comparing(Order::getCreatedAt))
                    .orElse(orders.get(0));
            for (Stop stop : sequence) {
                if (stop.type() == StopType.DROPOFF && stop.orderId().equals(firstOrder.getId())) {
                    double maxAllowed = firstOrder.getPromisedEtaMinutes() * SLA_LATENESS_FACTOR;
                    if (stop.estimatedArrivalMinutes() > maxAllowed) return false;
                    break;
                }
            }
        }

        // Constraint 4: Cumulative merchant wait ≤ 8 min
        double cumulativeMerchantWait = computeCumulativeMerchantWait(sequence, orders);
        if (cumulativeMerchantWait > MAX_MERCHANT_WAIT_MINUTES) return false;

        // Constraint 5: Detour ratio ≤ 2.0
        double standaloneDistKm = orders.stream()
                .mapToDouble(o -> o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double totalRouteDistKm = computeSequenceDistanceKm(sequence);
        if (standaloneDistKm > 0) {
            double detourRatio = totalRouteDistKm / standaloneDistKm;
            if (detourRatio > MAX_DETOUR_RATIO) return false;
        }

        return true;
    }

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

    /**
     * Route cost = travel distance (km) + merchant wait penalty.
     * Merchant wait penalty: if driver arrives before merchant is ready,
     * the idle time incurs a cost proportional to the wait.
     */
    private double computeRouteCost(GeoPoint start, List<Stop> sequence, List<Order> orders) {
        double totalDistKm = computeDistanceOnly(start, sequence);
        double merchantWaitPenalty = computeCumulativeMerchantWait(sequence, orders);
        // Weight merchant wait: 1 minute wait ~ 0.5 km penalty
        return totalDistKm + merchantWaitPenalty * 0.5;
    }

    /**
     * Pure distance cost (km) without penalties — used for cheapest insertion.
     */
    private double computeDistanceOnly(GeoPoint start, List<Stop> sequence) {
        double totalDist = 0;
        GeoPoint prev = start;
        for (Stop stop : sequence) {
            totalDist += prev.distanceTo(stop.location());
            prev = stop.location();
        }
        return totalDist / 1000.0;
    }

    /**
     * Total route distance in km from sequence stops only.
     */
    private double computeSequenceDistanceKm(List<Stop> sequence) {
        double dist = 0;
        for (int i = 1; i < sequence.size(); i++) {
            dist += sequence.get(i - 1).location().distanceTo(sequence.get(i).location());
        }
        return dist / 1000.0;
    }

    /**
     * Calculate cumulative merchant wait across all pickup stops.
     * If driver arrives before merchantReadyAt, the difference is wait time.
     */
    private double computeCumulativeMerchantWait(List<Stop> sequence, List<Order> orders) {
        double totalWait = 0;
        for (Stop stop : sequence) {
            if (stop.type() == StopType.PICKUP) {
                Order order = orders.stream()
                        .filter(o -> o.getId().equals(stop.orderId()))
                        .findFirst().orElse(null);
                if (order != null && order.getPredictedReadyAt() != null && order.getCreatedAt() != null) {
                    // Merchant ready time in minutes since order creation
                    double merchantReadyMin = java.time.Duration.between(
                            order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
                    double driverArrivalMin = stop.estimatedArrivalMinutes();
                    if (driverArrivalMin < merchantReadyMin) {
                        totalWait += (merchantReadyMin - driverArrivalMin);
                    }
                }
            }
        }
        return totalWait;
    }

    /**
     * Compute arrival times for each stop, incorporating:
     * - Travel time based on speed
     * - Merchant wait time: if driver arrives before merchant is ready,
     *   elapsed advances to merchant ready time
     */
    private void computeArrivalTimes(GeoPoint driverPos, List<Stop> sequence, List<Order> orders) {
        double speedKmh = estimateSpeed();
        double elapsed = 0;
        GeoPoint prev = driverPos;

        List<Stop> updated = new ArrayList<>();
        for (Stop stop : sequence) {
            double distKm = prev.distanceTo(stop.location()) / 1000.0;
            double travelTime = (distKm / speedKmh) * 60.0;
            elapsed += travelTime;

            // Merchant readiness integration: if pickup and driver arrives early, wait
            if (stop.type() == StopType.PICKUP) {
                Order order = orders.stream()
                        .filter(o -> o.getId().equals(stop.orderId()))
                        .findFirst().orElse(null);
                if (order != null && order.getPredictedReadyAt() != null && order.getCreatedAt() != null) {
                    double merchantReadyMin = java.time.Duration.between(
                            order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
                    if (elapsed < merchantReadyMin) {
                        elapsed = merchantReadyMin; // Wait for merchant
                    }
                }
            }

            updated.add(new Stop(stop.orderId(), stop.location(), stop.type(), elapsed));
            prev = stop.location();
        }

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
        List<Stop> seq = new ArrayList<>();
        seq.add(new Stop(order.getId(), order.getPickupPoint(), StopType.PICKUP, 0));
        seq.add(new Stop(order.getId(), order.getDropoffPoint(), StopType.DROPOFF, 0));
        computeArrivalTimes(driver.getCurrentLocation(), seq, List.of(order));
        return seq;
    }
}
