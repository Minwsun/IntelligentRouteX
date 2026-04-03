package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Execution-first assignment solver with bucketed matching passes.
 */
public class AssignmentSolver {

    private static final int MAX_PLANS_PER_DRIVER = 6;
    private static final int MAX_DRIVERS_PER_GROUP = 20;
    private static final double MIN_CONFIDENCE = 0.15;

    public List<DispatchPlan> solve(List<DispatchPlan> scoredPlans) {
        if (scoredPlans == null || scoredPlans.isEmpty()) {
            return List.of();
        }

        Map<String, List<DispatchPlan>> plansByDriver = new LinkedHashMap<>();
        for (DispatchPlan plan : scoredPlans) {
            plansByDriver.computeIfAbsent(plan.getDriver().getId(), ignored -> new ArrayList<>())
                    .add(plan);
        }

        List<DispatchPlan> cappedPlans = new ArrayList<>();
        for (List<DispatchPlan> driverPlans : plansByDriver.values()) {
            Map<SelectionBucket, DispatchPlan> bestByBucket = new EnumMap<>(SelectionBucket.class);
            driverPlans.stream()
                    .sorted(Comparator.comparingDouble(this::businessSelectionScore).reversed())
                    .forEach(plan -> bestByBucket.merge(
                            effectiveBucket(plan),
                            plan,
                            (existing, candidate) ->
                                    businessSelectionScore(candidate) > businessSelectionScore(existing)
                                            ? candidate : existing));
            cappedPlans.addAll(bestByBucket.values().stream()
                    .sorted(Comparator.comparingDouble(this::businessSelectionScore).reversed())
                    .limit(MAX_PLANS_PER_DRIVER)
                    .toList());
        }

        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<DispatchPlan> selected = new ArrayList<>();
        Map<String, Integer> borrowedQuotaByZone = new HashMap<>();
        Map<String, Integer> emergencyQuotaByZone = new HashMap<>();

        List<SelectionBucket> passOrder = List.of(
                SelectionBucket.WAVE_LOCAL,
                SelectionBucket.EXTENSION_LOCAL,
                SelectionBucket.HOLD_WAIT3,
                SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD,
                SelectionBucket.BORROWED_COVERAGE,
                SelectionBucket.EMERGENCY_COVERAGE
        );

        for (SelectionBucket bucket : passOrder) {
            List<DispatchPlan> bucketPlans = cappedPlans.stream()
                    .filter(plan -> effectiveBucket(plan) == bucket)
                    .filter(plan -> eligibleForBucket(plan, bucket))
                    .sorted(Comparator.comparingDouble(this::businessSelectionScore).reversed())
                    .toList();
            if (bucketPlans.isEmpty()) {
                continue;
            }
            List<DispatchPlan> matched = matchZoneGroups(bucketPlans, usedDrivers, usedOrders);
            for (DispatchPlan plan : matched) {
                if (bucket == SelectionBucket.BORROWED_COVERAGE) {
                    String zone = getZoneKey(plan);
                    if (borrowedQuotaByZone.getOrDefault(zone, 0) >= 1) {
                        continue;
                    }
                    borrowedQuotaByZone.merge(zone, 1, Integer::sum);
                } else if (bucket == SelectionBucket.EMERGENCY_COVERAGE) {
                    String zone = getZoneKey(plan);
                    if (emergencyQuotaByZone.getOrDefault(zone, 0) >= 1) {
                        continue;
                    }
                    emergencyQuotaByZone.merge(zone, 1, Integer::sum);
                }
                selected.add(plan);
            }
        }
        return selected;
    }

    private List<DispatchPlan> matchZoneGroups(List<DispatchPlan> plans,
                                               Set<String> usedDrivers,
                                               Set<String> usedOrders) {
        if (plans.isEmpty()) {
            return List.of();
        }
        Map<String, List<DispatchPlan>> zoneGroups = new LinkedHashMap<>();
        for (DispatchPlan plan : plans) {
            zoneGroups.computeIfAbsent(getZoneKey(plan), ignored -> new ArrayList<>()).add(plan);
        }
        List<DispatchPlan> result = new ArrayList<>();
        for (List<DispatchPlan> groupPlans : zoneGroups.values()) {
            for (List<DispatchPlan> subGroup : partitionGroup(groupPlans)) {
                List<DispatchPlan> matched = auctionMatch(subGroup, usedDrivers, usedOrders);
                result.addAll(matched);
                for (DispatchPlan plan : matched) {
                    usedDrivers.add(plan.getDriver().getId());
                    for (Order order : plan.getOrders()) {
                        usedOrders.add(order.getId());
                    }
                }
            }
        }
        return result;
    }

    private List<DispatchPlan> auctionMatch(List<DispatchPlan> plans,
                                            Set<String> globalUsedDrivers,
                                            Set<String> globalUsedOrders) {
        plans.sort(Comparator.comparingDouble(this::businessSelectionScore).reversed());

        Map<String, DispatchPlan> driverAssignment = new LinkedHashMap<>();
        Set<String> localUsedOrders = new HashSet<>();
        List<DispatchPlan> result = new ArrayList<>();

        for (DispatchPlan plan : plans) {
            if (globalUsedDrivers.contains(plan.getDriver().getId())) {
                continue;
            }
            if (plan.getConfidence() < MIN_CONFIDENCE) {
                continue;
            }
            if (plan.getSelectionBucket() != SelectionBucket.HOLD_WAIT3 && plan.getOrders().isEmpty()) {
                continue;
            }
            if (hasOrderConflict(plan, globalUsedOrders, localUsedOrders)) {
                continue;
            }

            String driverId = plan.getDriver().getId();
            DispatchPlan existing = driverAssignment.get(driverId);
            if (existing != null) {
                if (businessSelectionScore(plan) <= businessSelectionScore(existing)) {
                    continue;
                }
                removeOrders(existing, localUsedOrders);
                result.remove(existing);
            }
            driverAssignment.put(driverId, plan);
            addOrders(plan, localUsedOrders);
            result.add(plan);
        }
        return result;
    }

    private boolean hasOrderConflict(DispatchPlan plan,
                                     Set<String> globalUsedOrders,
                                     Set<String> localUsedOrders) {
        for (Order order : plan.getOrders()) {
            if (globalUsedOrders.contains(order.getId()) || localUsedOrders.contains(order.getId())) {
                return true;
            }
        }
        return false;
    }

    private void addOrders(DispatchPlan plan, Set<String> localUsedOrders) {
        for (Order order : plan.getOrders()) {
            localUsedOrders.add(order.getId());
        }
    }

    private void removeOrders(DispatchPlan plan, Set<String> localUsedOrders) {
        for (Order order : plan.getOrders()) {
            localUsedOrders.remove(order.getId());
        }
    }

    private SelectionBucket effectiveBucket(DispatchPlan plan) {
        SelectionBucket declared = plan.getSelectionBucket();
        if (declared == SelectionBucket.WAVE_LOCAL
                || declared == SelectionBucket.EXTENSION_LOCAL
                || declared == SelectionBucket.HOLD_WAIT3
                || declared == SelectionBucket.BORROWED_COVERAGE
                || declared == SelectionBucket.EMERGENCY_COVERAGE) {
            return declared;
        }
        if (plan.isWaitingForThirdOrder()) {
            return SelectionBucket.HOLD_WAIT3;
        }
        if (plan.isWaveLaunchEligible()) {
            return plan.getBundleSize() >= 3
                    ? SelectionBucket.WAVE_LOCAL
                    : SelectionBucket.EXTENSION_LOCAL;
        }
        if (plan.getBundleSize() >= 3) {
            return SelectionBucket.WAVE_LOCAL;
        }
        if (declared == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                && plan.getBorrowedDependencyScore() >= 0.25) {
            return SelectionBucket.BORROWED_COVERAGE;
        }
        return declared == null ? SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD : declared;
    }

    private boolean eligibleForBucket(DispatchPlan plan, SelectionBucket bucket) {
        return switch (bucket) {
            case WAVE_LOCAL -> plan.isExecutionGatePassed()
                    && plan.getBundleSize() >= 3
                    && plan.getBorrowedDependencyScore() < 0.25;
            case EXTENSION_LOCAL -> plan.isExecutionGatePassed()
                    && plan.isWaveLaunchEligible()
                    && plan.getBundleSize() < 3
                    && plan.getBorrowedDependencyScore() < 0.25;
            case HOLD_WAIT3 -> plan.isWaitingForThirdOrder()
                    && plan.isHardThreeOrderPolicyActive()
                    && plan.getHoldRemainingCycles() > 0
                    && plan.getWaveReadinessScore() >= 0.60;
            case FALLBACK_LOCAL_LOW_DEADHEAD -> plan.isExecutionGatePassed()
                    && plan.getBundleSize() <= 2
                    && plan.getBorrowedDependencyScore() < 0.25
                    && (plan.getOnTimeProbability() <= 0.0 || plan.getOnTimeProbability() >= 0.72);
            case BORROWED_COVERAGE -> plan.getBorrowedDependencyScore() >= 0.25
                    && plan.isExecutionGatePassed();
            case EMERGENCY_COVERAGE -> true;
        };
    }

    private double businessSelectionScore(DispatchPlan plan) {
        SelectionBucket bucket = effectiveBucket(plan);
        double score = plan.getTotalScore();
        if (bucket == SelectionBucket.WAVE_LOCAL || bucket == SelectionBucket.EXTENSION_LOCAL) {
            score += 0.06;
        }
        if (bucket == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD) {
            score += plan.getPredictedDeadheadKm() <= 1.9
                    && plan.getOnTimeProbability() >= 0.82
                    && plan.getBorrowedDependencyScore() < 0.25
                    ? 0.04 : -0.01;
        } else if (bucket == SelectionBucket.BORROWED_COVERAGE) {
            score -= 0.07;
        } else if (bucket == SelectionBucket.EMERGENCY_COVERAGE) {
            score -= 0.10;
        } else if (bucket == SelectionBucket.HOLD_WAIT3) {
            score -= 0.04;
        }
        score += Math.max(0.0, plan.getExecutionScore()) * 0.12;
        score += Math.max(0.0, plan.getContinuationScore()) * 0.04;
        score += Math.max(0.0, plan.getCoverageScore()) * 0.02;
        score += Math.max(0.0, plan.getGraphAffinityScore()) * 0.05;
        score -= plan.getPredictedDeadheadKm() * 0.10;
        score -= plan.getExpectedPostCompletionEmptyKm() * 0.08;
        score -= plan.getBorrowedDependencyScore() * 0.12;
        score -= plan.getEmptyRiskAfter() * 0.07;
        if (plan.isStressFallbackOnly() && plan.getBundleSize() <= 2) {
            score -= 0.05;
        }
        score += plan.getCoverageQuality() * 0.03;
        score += plan.getReplacementDepth() * 0.02;
        score += plan.getPostDropDemandProbability() * 0.03;
        if (bucket == SelectionBucket.HOLD_WAIT3) {
            score += plan.getWaveReadinessScore() * 0.03;
            if (plan.getHoldRemainingCycles() <= 1) {
                score -= 0.02;
            }
        }
        return score;
    }

    private String getZoneKey(DispatchPlan plan) {
        if (plan.getHoldAnchorZoneId() != null && !plan.getHoldAnchorZoneId().isBlank()) {
            return plan.getHoldAnchorZoneId();
        }
        if (!plan.getOrders().isEmpty()) {
            return plan.getOrders().get(0).getPickupRegionId();
        }
        return plan.getDriver() == null ? "__IDLE__" : plan.getDriver().getRegionId();
    }

    private List<List<DispatchPlan>> partitionGroup(List<DispatchPlan> group) {
        int maxGroupSize = MAX_DRIVERS_PER_GROUP * MAX_PLANS_PER_DRIVER;
        if (group.size() <= maxGroupSize) {
            return List.of(group);
        }
        List<List<DispatchPlan>> subGroups = new ArrayList<>();
        for (int i = 0; i < group.size(); i += maxGroupSize) {
            int end = Math.min(i + maxGroupSize, group.size());
            subGroups.add(group.subList(i, end));
        }
        return subGroups;
    }

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
                double deliveryDist = order.getPickupPoint()
                        .distanceTo(order.getDropoffPoint()) / 1000.0;
                double deliveryMin = pickupMin + (deliveryDist / speedKmh) * 60.0;

                List<DispatchPlan.Stop> seq = List.of(
                        new DispatchPlan.Stop(order.getId(), order.getPickupPoint(),
                                DispatchPlan.Stop.StopType.PICKUP, pickupMin),
                        new DispatchPlan.Stop(order.getId(), order.getDropoffPoint(),
                                DispatchPlan.Stop.StopType.DROPOFF, deliveryMin));

                DispatchPlan plan = new DispatchPlan(best, bundle, seq);
                plan.setSelectionBucket(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
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
