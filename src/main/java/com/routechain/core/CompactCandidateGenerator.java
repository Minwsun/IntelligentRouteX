package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;
import com.routechain.simulation.SequenceOptimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class CompactCandidateGenerator {
    private final CompactPolicyConfig policyConfig;

    public CompactCandidateGenerator() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactCandidateGenerator(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig == null ? CompactPolicyConfig.defaults() : policyConfig;
    }

    public List<DispatchPlan> generate(List<Order> openOrders,
                                       List<Driver> availableDrivers,
                                       CompactDispatchContext context) {
        List<DispatchPlan> allPlans = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            allPlans.addAll(generateForDriver(driver, openOrders, context));
        }
        return allPlans;
    }

    public List<DispatchPlan> generateForDriver(Driver driver,
                                                List<Order> openOrders,
                                                CompactDispatchContext context) {
        int cap = candidateCap(context);
        List<Order> nearbyOrders = nearestOrders(driver, openOrders, 6);
        SequenceOptimizer optimizer = new SequenceOptimizer(
                context.trafficIntensity(),
                context.weatherProfile());
        Set<String> seen = new LinkedHashSet<>();
        List<DispatchPlan> plans = new ArrayList<>();

        for (Order order : nearbyOrders.stream().limit(2).toList()) {
            DispatchPlan plan = buildPlan(driver, List.of(order), optimizer, context, "CMP-S");
            if (plan != null && seen.add(planKey(plan))) {
                plan.setCompactPlanType(CompactPlanType.SINGLE_LOCAL);
                plan.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
                plans.add(plan);
            }
        }

        for (List<Order> pair : compactPairs(nearbyOrders)) {
            DispatchPlan plan = buildPlan(driver, pair, optimizer, context, "CMP-B2");
            if (plan != null && seen.add(planKey(plan))) {
                plan.setCompactPlanType(CompactPlanType.BATCH_2_COMPACT);
                plan.setSelectionBucket(SelectionBucket.EXTENSION_LOCAL);
                plans.add(plan);
            }
            if (plans.size() >= cap) {
                break;
            }
        }

        List<Order> waveOrders = cleanWaveThree(nearbyOrders);
        if (waveOrders.size() == 3) {
            DispatchPlan wave = buildPlan(driver, waveOrders, optimizer, context, "CMP-W3");
            if (wave != null && seen.add(planKey(wave))) {
                wave.setCompactPlanType(CompactPlanType.WAVE_3_CLEAN);
                wave.setSelectionBucket(SelectionBucket.WAVE_LOCAL);
                plans.add(wave);
            }
        }

        if (plans.isEmpty() && !nearbyOrders.isEmpty()) {
            DispatchPlan fallback = buildPlan(driver, List.of(nearbyOrders.get(0)), optimizer, context, "CMP-F");
            if (fallback != null) {
                fallback.setCompactPlanType(CompactPlanType.FALLBACK_LOCAL);
                fallback.setSelectionBucket(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
                plans.add(fallback);
            }
        }

        plans.sort(Comparator.comparingDouble(DispatchPlan::getPredictedDeadheadKm));
        return plans.size() > cap ? new ArrayList<>(plans.subList(0, cap)) : plans;
    }

    public int candidateCap(CompactDispatchContext context) {
        boolean stress = context.pendingOrderCount() >= context.availableDriverCount() * 2
                || context.trafficIntensity() >= 0.70
                || context.weatherProfile() == WeatherProfile.HEAVY_RAIN
                || context.weatherProfile() == WeatherProfile.STORM;
        return stress ? policyConfig.stressCandidateCap() : policyConfig.defaultCandidateCap();
    }

    private List<Order> nearestOrders(Driver driver, List<Order> openOrders, int limit) {
        return openOrders.stream()
                .sorted(Comparator
                        .comparingDouble((Order order) -> driver.getCurrentLocation().distanceTo(order.getPickupPoint()))
                        .thenComparing(Order::getCreatedAt))
                .limit(limit)
                .toList();
    }

    private List<List<Order>> compactPairs(List<Order> orders) {
        List<List<Order>> pairs = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                Order a = orders.get(i);
                Order b = orders.get(j);
                double pickupSpreadKm = a.getPickupPoint().distanceTo(b.getPickupPoint()) / 1000.0;
                if (pickupSpreadKm <= 1.6) {
                    pairs.add(List.of(a, b));
                }
            }
        }
        pairs.sort(Comparator.comparingDouble(pair ->
                pair.get(0).getPickupPoint().distanceTo(pair.get(1).getPickupPoint())));
        return pairs.size() > 2 ? new ArrayList<>(pairs.subList(0, 2)) : pairs;
    }

    private List<Order> cleanWaveThree(List<Order> orders) {
        if (orders.size() < 3) {
            return List.of();
        }
        List<Order> best = List.of();
        double bestSpread = Double.MAX_VALUE;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                for (int k = j + 1; k < orders.size(); k++) {
                    List<Order> triple = List.of(orders.get(i), orders.get(j), orders.get(k));
                    double spread = pickupSpreadKm(triple);
                    if (spread <= 2.0 && spread < bestSpread) {
                        bestSpread = spread;
                        best = triple;
                    }
                }
            }
        }
        return best;
    }

    private double pickupSpreadKm(List<Order> orders) {
        double max = 0.0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                max = Math.max(max, orders.get(i).getPickupPoint().distanceTo(orders.get(j).getPickupPoint()) / 1000.0);
            }
        }
        return max;
    }

    private DispatchPlan buildPlan(Driver driver,
                                   List<Order> orders,
                                   SequenceOptimizer optimizer,
                                   CompactDispatchContext context,
                                   String prefix) {
        DispatchPlan.Bundle bundle = new DispatchPlan.Bundle(
                prefix + "-" + driver.getId() + "-" + orders.stream().map(Order::getId).reduce((a, b) -> a + "_" + b).orElse("none"),
                List.copyOf(orders),
                orders.stream().mapToDouble(Order::getQuotedFee).sum(),
                orders.size());
        List<List<DispatchPlan.Stop>> sequences = optimizer.generateFeasibleSequences(driver, bundle, 3);
        if (sequences.isEmpty()) {
            return null;
        }
        DispatchPlan.Stop firstStop = sequences.get(0).get(0);
        DispatchPlan plan = new DispatchPlan(driver, bundle, sequences.get(0));
        double deadheadKm = driver.getCurrentLocation().distanceTo(firstStop.location()) / 1000.0;
        double routeMinutes = sequences.get(0).isEmpty() ? 0.0 : sequences.get(0).get(sequences.get(0).size() - 1).estimatedArrivalMinutes();
        var routeMetrics = optimizer.evaluateRouteObjective(driver, sequences.get(0), orders);
        populatePlan(plan, orders, deadheadKm, routeMinutes, routeMetrics, context);
        return plan;
    }

    private void populatePlan(DispatchPlan plan,
                              List<Order> orders,
                              double deadheadKm,
                              double routeMinutes,
                              SequenceOptimizer.RouteObjectiveMetrics routeMetrics,
                              CompactDispatchContext context) {
        double standaloneMinutes = orders.stream()
                .mapToDouble(order -> order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0 / 18.0 * 60.0)
                .sum();
        double onTimeProbability = PlanFeatureVector.clamp01(1.0 - Math.max(0.0, routeMinutes - averageEta(orders)) / Math.max(20.0, averageEta(orders)));
        double bundleEfficiency = orders.isEmpty()
                ? 0.0
                : PlanFeatureVector.clamp01(standaloneMinutes / Math.max(standaloneMinutes, routeMinutes + deadheadKm * 3.2));
        double cancelRisk = PlanFeatureVector.clamp01(orders.stream()
                .mapToDouble(Order::getCancellationRisk)
                .average()
                .orElse(0.08));
        double driverProfit = orders.stream().mapToDouble(Order::getQuotedFee).sum()
                - deadheadKm * 2500.0
                - Math.max(0.0, routeMinutes - standaloneMinutes) * 180.0;
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setPredictedTotalMinutes(routeMinutes);
        plan.setOnTimeProbability(onTimeProbability);
        plan.setLateRisk(1.0 - onTimeProbability);
        plan.setCancellationRisk(cancelRisk);
        plan.setDriverProfit(driverProfit);
        plan.setCustomerFee(orders.stream().mapToDouble(Order::getQuotedFee).sum());
        plan.setBundleEfficiency(bundleEfficiency);
        plan.setEndZoneOpportunity(routeMetrics.lastDropLandingScore());
        plan.setNextOrderAcquisitionScore(PlanFeatureVector.clamp01(1.0 - routeMetrics.expectedNextOrderIdleMinutes() / 8.0));
        plan.setCongestionPenalty(PlanFeatureVector.clamp01(context.trafficIntensity()));
        plan.setDeliveryCorridorScore(routeMetrics.deliveryCorridorScore());
        plan.setLastDropLandingScore(routeMetrics.lastDropLandingScore());
        plan.setExpectedPostCompletionEmptyKm(routeMetrics.expectedPostCompletionEmptyKm());
        plan.setRemainingDropProximityScore(routeMetrics.remainingDropProximityScore());
        plan.setDeliveryZigZagPenalty(routeMetrics.deliveryZigZagPenalty());
        plan.setExpectedNextOrderIdleMinutes(routeMetrics.expectedNextOrderIdleMinutes());
        plan.setPostDropDemandProbability(PlanFeatureVector.clamp01(1.0 - routeMetrics.expectedNextOrderIdleMinutes() / 10.0));
        plan.setTraceId("COMPACT-" + plan.getBundle().bundleId());
        plan.setRunId("compact-runtime");
        plan.setExecutionGatePassed(true);
    }

    private double averageEta(List<Order> orders) {
        return orders.stream().mapToInt(Order::getPromisedEtaMinutes).average().orElse(45.0);
    }

    private String planKey(DispatchPlan plan) {
        return plan.getDriver().getId() + ":" + plan.getOrders().stream()
                .map(Order::getId)
                .sorted()
                .reduce((left, right) -> left + "|" + right)
                .orElse("__empty__");
    }
}
