package com.routechain.ai;

import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.DriverDecisionContext.OrderCluster;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.DispatchPlan.Bundle;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;
import com.routechain.simulation.SequenceOptimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Generates candidate dispatch plans for a single driver from their
 * {@link DriverDecisionContext}.
 *
 * Driver-centric plan generation:
 *   1. Single-order plans (one per reachable order)
 *   2. Bundle plans (from pickup clusters, 2-N orders with dynamic cap)
 *   3. Hold/wait plan (stay put, anticipate incoming orders)
 *   4. Reposition plans (move to a better zone)
 *
 * For bundle plans, the approach is SEQUENCE-FIRST:
 *   candidate orders → generate valid pickup/dropoff sequences → compute route cost
 */
public class DriverPlanGenerator {

    /** Maximum candidate plans to emit per driver. */
    private static final int MAX_PLANS_PER_DRIVER = 12;

    /** Maximum candidate sequences to evaluate per bundle. */
    private static final int MAX_SEQUENCES_PER_BUNDLE = 3;

    /** Hold plan base score — represents the expected value of waiting. */
    private static final double HOLD_PLAN_BASE_SCORE = 0.08;

    /** Reposition plan base score — set low; only chosen when no orders. */
    private static final double REPOSITION_BASE_SCORE = 0.05;

    public DriverPlanGenerator() {
    }

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Generate up to {@link #MAX_PLANS_PER_DRIVER} candidate plans for a
     * single driver.
     *
     * @param ctx              driver's local world snapshot
     * @param trafficIntensity current global traffic intensity
     * @param weather          current weather profile
     * @param simulatedHour    simulated hour (0-23)
     * @return candidate plans, sorted by preliminary quality
     */
    public List<DispatchPlan> generate(DriverDecisionContext ctx,
                                        double trafficIntensity,
                                        WeatherProfile weather,
                                        int simulatedHour) {

        List<DispatchPlan> plans = new ArrayList<>();
        Driver driver = ctx.driver();

        // ── Type 1: Single-order plans ──────────────────────────────────
        for (Order order : ctx.reachableOrders()) {
            DispatchPlan plan = buildSingleOrderPlan(driver, order,
                    trafficIntensity, weather);
            if (plan != null) {
                plans.add(plan);
            }
        }

        // ── Type 2: Bundle plans from pickup clusters ───────────────────
        for (OrderCluster cluster : ctx.pickupClusters()) {
            if (cluster.orders().size() < 2) continue;

            int dynamicMax = computeDynamicBatchCap(cluster, trafficIntensity, weather, driver);
            int maxSize = Math.min(dynamicMax, cluster.orders().size());
            for (int size = 2; size <= maxSize; size++) {
                List<DispatchPlan> bundlePlans = buildBundlePlans(
                        driver, cluster, size, trafficIntensity, weather);
                plans.addAll(bundlePlans);
            }
        }

        // ── Type 3: Hold/wait plan ──────────────────────────────────────
        plans.add(buildHoldPlan(driver, ctx));

        // ── Type 4: Reposition plans ────────────────────────────────────
        for (EndZoneCandidate zone : ctx.endZoneCandidates()) {
            DispatchPlan reposPlan = buildRepositionPlan(driver, zone);
            if (reposPlan != null) {
                plans.add(reposPlan);
            }
        }

        // Sort by preliminary score descending
        plans.sort(Comparator.comparingDouble(
                DispatchPlan::getTotalScore).reversed());

        if (plans.size() > MAX_PLANS_PER_DRIVER) {
            return new ArrayList<>(plans.subList(0, MAX_PLANS_PER_DRIVER));
        }
        return plans;
    }

    // ── Plan builders ───────────────────────────────────────────────────

    private DispatchPlan buildSingleOrderPlan(Driver driver, Order order,
                                               double trafficIntensity,
                                               WeatherProfile weather) {
        Bundle bundle = new Bundle(
                "S-" + order.getId(),
                List.of(order),
                order.getQuotedFee(),
                1
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(
                trafficIntensity, weather);
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(
                driver, bundle, 1);

        if (sequences.isEmpty()) return null;

        DispatchPlan plan = new DispatchPlan(driver, bundle, sequences.get(0));

        double deadheadKm = driver.getCurrentLocation()
                .distanceTo(order.getPickupPoint()) / 1000.0;
        double deliveryKm = order.getPickupPoint()
                .distanceTo(order.getDropoffPoint()) / 1000.0;
        double feeNorm = order.getQuotedFee() / 35000.0;
        double prelimScore = feeNorm * 0.5
                - (deadheadKm / 6.0) * 0.3
                + (deliveryKm > 0 ? 1.0 / (1.0 + deadheadKm / deliveryKm) : 0) * 0.2;

        plan.setTotalScore(Math.max(0.01, prelimScore));
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setTraceId("SINGLE-" + UUID.randomUUID().toString().substring(0, 6));

        return plan;
    }

    /**
     * Build bundle plans from a pickup cluster.
     * SEQUENCE-FIRST approach.
     */
    private List<DispatchPlan> buildBundlePlans(Driver driver,
                                                  OrderCluster cluster,
                                                  int bundleSize,
                                                  double trafficIntensity,
                                                  WeatherProfile weather) {
        List<DispatchPlan> result = new ArrayList<>();
        List<Order> candidates = cluster.orders();

        if (candidates.size() == bundleSize) {
            DispatchPlan plan = buildBundlePlanFromOrders(
                    driver, candidates, trafficIntensity, weather);
            if (plan != null) result.add(plan);
            return result;
        }

        // Strategy 1: top orders by fee
        List<Order> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Order::getQuotedFee).reversed());
        List<Order> selected = sorted.subList(0,
                Math.min(bundleSize, sorted.size()));

        DispatchPlan plan = buildBundlePlanFromOrders(
                driver, selected, trafficIntensity, weather);
        if (plan != null) result.add(plan);

        // Strategy 2: nearest pickups to driver
        if (candidates.size() > bundleSize) {
            List<Order> nearest = new ArrayList<>(candidates);
            GeoPoint driverPos = driver.getCurrentLocation();
            nearest.sort(Comparator.comparingDouble(o ->
                    driverPos.distanceTo(o.getPickupPoint())));
            List<Order> nearSelected = nearest.subList(0,
                    Math.min(bundleSize, nearest.size()));

            if (!nearSelected.equals(selected)) {
                DispatchPlan nearPlan = buildBundlePlanFromOrders(
                        driver, nearSelected, trafficIntensity, weather);
                if (nearPlan != null) result.add(nearPlan);
            }
        }

        return result;
    }

    /**
     * Core bundle plan builder — SEQUENCE-FIRST.
     */
    private DispatchPlan buildBundlePlanFromOrders(Driver driver,
                                                     List<Order> orders,
                                                     double trafficIntensity,
                                                     WeatherProfile weather) {
        double totalFee = orders.stream()
                .mapToDouble(Order::getQuotedFee).sum();

        Bundle bundle = new Bundle(
                "B-" + UUID.randomUUID().toString().substring(0, 8),
                List.copyOf(orders),
                totalFee,
                orders.size()
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(
                trafficIntensity, weather);
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(
                driver, bundle, MAX_SEQUENCES_PER_BUNDLE);

        if (sequences.isEmpty()) return null;

        List<Stop> bestSeq = sequences.get(0);
        DispatchPlan plan = new DispatchPlan(driver, bundle, bestSeq);

        double deadheadKm = driver.getCurrentLocation()
                .distanceTo(orders.get(0).getPickupPoint()) / 1000.0;
        double standaloneDist = orders.stream()
                .mapToDouble(o -> o.getPickupPoint()
                        .distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double bundleRouteDist = computeRouteDistance(
                driver.getCurrentLocation(), bestSeq);
        double efficiency = standaloneDist > 0
                ? standaloneDist / Math.max(0.1, bundleRouteDist)
                : 0.5;

        double feeNorm = totalFee / (35000.0 * orders.size());
        double prelimScore = feeNorm * 0.35
                + efficiency * 0.30
                + (orders.size() / 5.0) * 0.15
                - (deadheadKm / 6.0) * 0.20;

        plan.setTotalScore(Math.max(0.01, prelimScore));
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setBundleEfficiency(efficiency);
        plan.setTraceId("BUNDLE-" + UUID.randomUUID().toString().substring(0, 6));

        return plan;
    }

    private DispatchPlan buildHoldPlan(Driver driver,
                                        DriverDecisionContext ctx) {
        Bundle holdBundle = new Bundle("HOLD", List.of(), 0, 0);
        List<Stop> emptySeq = List.of();

        DispatchPlan plan = new DispatchPlan(driver, holdBundle, emptySeq);

        double holdScore = HOLD_PLAN_BASE_SCORE
                + ctx.localDemandIntensity() * 0.04
                + ctx.localSpikeProbability() * 0.06
                + ctx.localShortagePressure() * 0.03
                - ctx.localDriverDensity() / 20.0 * 0.02;

        plan.setTotalScore(Math.max(0.01, holdScore));
        plan.setConfidence(0.3);
        plan.setTraceId("HOLD");

        return plan;
    }

    private DispatchPlan buildRepositionPlan(Driver driver,
                                              EndZoneCandidate zone) {
        if (zone.distanceKm() < 0.3 || zone.distanceKm() > 2.5) return null;

        Bundle reposBundle = new Bundle(
                "REPOS-" + UUID.randomUUID().toString().substring(0, 6),
                List.of(), 0, 0);

        Stop target = new Stop("REPOS", zone.position(), StopType.PICKUP,
                zone.distanceKm() / 15.0 * 60.0);
        List<Stop> seq = List.of(target);

        DispatchPlan plan = new DispatchPlan(driver, reposBundle, seq);

        double score = REPOSITION_BASE_SCORE
                + zone.attractionScore() * 0.08
                - zone.distanceKm() / 5.0 * 0.04;

        plan.setTotalScore(Math.max(0.01, score));
        plan.setConfidence(0.2);
        plan.setEndZoneOpportunity(zone.attractionScore());
        plan.setTraceId("REPOS");

        return plan;
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private SequenceOptimizer createSequenceOptimizer(double trafficIntensity,
                                                       WeatherProfile weather) {
        return new SequenceOptimizer(trafficIntensity, weather);
    }

    private double computeRouteDistance(GeoPoint start, List<Stop> sequence) {
        double dist = 0;
        GeoPoint prev = start;
        for (Stop stop : sequence) {
            dist += prev.distanceTo(stop.location()) / 1000.0;
            prev = stop.location();
        }
        return dist;
    }

    /**
     * Compute dynamic batch cap based on real-time conditions.
     * Factors: traffic, weather, merchant readiness, driver load, cluster compactness.
     */
    private int computeDynamicBatchCap(OrderCluster cluster,
                                        double trafficSeverity,
                                        WeatherProfile weather,
                                        Driver driver) {
        int cap = 3; // default base

        // Traffic severity — high traffic reduces capacity
        if (trafficSeverity > 0.6) cap -= 1;

        // Weather severity
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) cap -= 1;

        // Perfect conditions boost
        if (trafficSeverity < 0.2 && weather == WeatherProfile.CLEAR) cap += 1;

        // Cluster compactness — same merchant orders are highly compact
        boolean sameMerchant = true;
        String firstMerchantId = cluster.orders().isEmpty()
                ? null : cluster.orders().get(0).getMerchantId();
        if (firstMerchantId != null && !firstMerchantId.isEmpty()) {
            for (Order o : cluster.orders()) {
                if (!firstMerchantId.equals(o.getMerchantId())) {
                    sameMerchant = false;
                    break;
                }
            }
        } else {
            sameMerchant = false;
        }
        if (sameMerchant) cap += 1;

        // Merchant readiness — high delay hazard reduces capacity
        double avgDelayHazard = cluster.orders().stream()
                .mapToDouble(Order::getPickupDelayHazard)
                .average().orElse(0);
        if (avgDelayHazard > 0.5) cap -= 1;

        // Driver load — already carrying orders reduces new capacity
        if (driver.getActiveOrderIds().size() >= 2) cap -= 1;

        // Bounded between 2 and 5
        return Math.max(2, Math.min(cap, 5));
    }
}
