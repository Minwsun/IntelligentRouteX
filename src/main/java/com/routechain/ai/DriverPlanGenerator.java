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
 * The key paradigm shift: instead of building bundles globally and then
 * assigning drivers, we start from the driver and generate driver-local plans:
 *
 *   1. Single-order plans (one per reachable order)
 *   2. Bundle plans (from pickup clusters, 2-4 orders)
 *   3. Hold/wait plan (stay put, anticipate incoming orders)
 *   4. Reposition plans (move to a better zone)
 *
 * For bundle plans, the approach is SEQUENCE-FIRST:
 *   candidate orders → generate valid pickup/dropoff sequences → compute route cost
 *
 * This replaces the old flow of:
 *   BundleGraph → searchBundles → match to nearest drivers
 */
public class DriverPlanGenerator {

    /** Maximum candidate plans to emit per driver. */
    private static final int MAX_PLANS_PER_DRIVER = 12;

    /** Maximum orders in a single bundle. */
    private static final int MAX_BUNDLE_SIZE = 4;

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
     * Plans are sorted by their preliminary score (sequence route time).
     * The caller (OmegaDispatchAgent) will re-score them with full AI models.
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

            // Try bundle sizes 2, 3, 4 from the cluster
            int maxSize = Math.min(MAX_BUNDLE_SIZE, cluster.orders().size());
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

        // Sort by preliminary score (sequence route time, lower = better for
        // delivery plans; higher totalScore = better overall)
        plans.sort(Comparator.comparingDouble(
                DispatchPlan::getTotalScore).reversed());

        // Keep top N
        if (plans.size() > MAX_PLANS_PER_DRIVER) {
            return new ArrayList<>(plans.subList(0, MAX_PLANS_PER_DRIVER));
        }
        return plans;
    }

    // ── Plan builders ───────────────────────────────────────────────────

    /**
     * Build a plan for a single order: driver → pickup → dropoff.
     */
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

        // Preliminary scoring: based on deadhead distance + fee
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
     *
     * SEQUENCE-FIRST approach:
     *   1. Select top orders from cluster by fee
     *   2. Generate valid pickup/dropoff sequences
     *   3. Score each sequence by route time + efficiency
     *
     * @param cluster cluster of nearby orders
     * @param bundleSize target number of orders in the bundle
     * @return list of valid bundle plans (may be empty)
     */
    private List<DispatchPlan> buildBundlePlans(Driver driver,
                                                  OrderCluster cluster,
                                                  int bundleSize,
                                                  double trafficIntensity,
                                                  WeatherProfile weather) {
        List<DispatchPlan> result = new ArrayList<>();
        List<Order> candidates = cluster.orders();

        // If cluster is exactly the target size, use all orders
        if (candidates.size() == bundleSize) {
            DispatchPlan plan = buildBundlePlanFromOrders(
                    driver, candidates, trafficIntensity, weather);
            if (plan != null) result.add(plan);
            return result;
        }

        // Otherwise: select top orders by fee, try one combination
        List<Order> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingDouble(Order::getQuotedFee).reversed());
        List<Order> selected = sorted.subList(0,
                Math.min(bundleSize, sorted.size()));

        DispatchPlan plan = buildBundlePlanFromOrders(
                driver, selected, trafficIntensity, weather);
        if (plan != null) result.add(plan);

        // Try another combination: nearest pickups to driver
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

        // SEQUENCE-FIRST: generate valid sequences, then pick best
        SequenceOptimizer seqOpt = createSequenceOptimizer(
                trafficIntensity, weather);
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(
                driver, bundle, MAX_SEQUENCES_PER_BUNDLE);

        if (sequences.isEmpty()) return null;

        // Take best sequence (already sorted by route time in SequenceOptimizer)
        List<Stop> bestSeq = sequences.get(0);
        DispatchPlan plan = new DispatchPlan(driver, bundle, bestSeq);

        // Preliminary scoring: bundle efficiency + fee
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

    /**
     * Build a hold/wait plan — driver stays in place, expecting orders.
     * Score is based on local demand and spike probability.
     */
    private DispatchPlan buildHoldPlan(Driver driver,
                                        DriverDecisionContext ctx) {
        Bundle holdBundle = new Bundle("HOLD", List.of(), 0, 0);
        List<Stop> emptySeq = List.of();

        DispatchPlan plan = new DispatchPlan(driver, holdBundle, emptySeq);

        // Hold is attractive when local demand/spike is high
        double holdScore = HOLD_PLAN_BASE_SCORE
                + ctx.localDemandIntensity() * 0.04
                + ctx.localSpikeProbability() * 0.06
                + ctx.localShortagePressure() * 0.03
                - ctx.localDriverDensity() / 20.0 * 0.02;

        plan.setTotalScore(Math.max(0.01, holdScore));
        plan.setConfidence(0.3); // low confidence — it's a fallback
        plan.setTraceId("HOLD");

        return plan;
    }

    /**
     * Build a reposition plan — driver moves to a more attractive zone.
     * Only worth it when current zone is poor and target zone is clearly better.
     */
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

        // Reposition worth = attraction gain - travel cost
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

    /**
     * Compute total route distance from driver position through all stops.
     */
    private double computeRouteDistance(GeoPoint start, List<Stop> sequence) {
        double dist = 0;
        GeoPoint prev = start;
        for (Stop stop : sequence) {
            dist += prev.distanceTo(stop.location()) / 1000.0;
            prev = stop.location();
        }
        return dist;
    }
}
