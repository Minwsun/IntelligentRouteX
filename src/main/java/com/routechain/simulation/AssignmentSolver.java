package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * Layer 6 — Assignment Solver.
 *
 * Phase 3 upgrade: weighted bipartite matching with:
 * - Candidate pool caps (top 5 plans per driver, max 20 drivers per zone group)
 * - Auction-based O(n²) matching within each zone group
 * - Greedy fallback for leftovers
 */
public class AssignmentSolver {

    /** Maximum plans to consider per driver. */
    private static final int MAX_PLANS_PER_DRIVER = 5;

    /** Maximum drivers per zone group for auction matching. */
    private static final int MAX_DRIVERS_PER_GROUP = 20;

    /** Minimum confidence threshold for plan acceptance. */
    private static final double MIN_CONFIDENCE = 0.15;

    /**
     * Select best non-conflicting plans from scored candidates.
     * Uses weighted bipartite matching via auction algorithm,
     * with greedy fallback for unmatched orders.
     */
    public List<DispatchPlan> solve(List<DispatchPlan> scoredPlans) {
        if (scoredPlans.isEmpty()) return List.of();

        // Step 1: Pool caps — keep top N plans per driver
        Map<String, List<DispatchPlan>> plansByDriver = new LinkedHashMap<>();
        for (DispatchPlan plan : scoredPlans) {
            plansByDriver.computeIfAbsent(plan.getDriver().getId(),
                    k -> new ArrayList<>()).add(plan);
        }

        List<DispatchPlan> cappedPlans = new ArrayList<>();
        for (Map.Entry<String, List<DispatchPlan>> entry : plansByDriver.entrySet()) {
            List<DispatchPlan> driverPlans = entry.getValue();
            driverPlans.sort(Comparator.comparingDouble(
                    DispatchPlan::getTotalScore).reversed());
            LinkedHashSet<DispatchPlan> keptPlans = new LinkedHashSet<>();
            boolean hardThreePolicy = driverPlans.stream()
                    .anyMatch(DispatchPlan::isHardThreeOrderPolicyActive);
            boolean hasVisibleWave = driverPlans.stream()
                    .anyMatch(DispatchPlan::isWaveLaunchEligible);

            if (hardThreePolicy && hasVisibleWave) {
                driverPlans.stream()
                        .filter(DispatchPlan::isWaveLaunchEligible)
                        .limit(2)
                        .forEach(keptPlans::add);
            }

            for (DispatchPlan plan : driverPlans) {
                if (keptPlans.size() >= MAX_PLANS_PER_DRIVER) {
                    break;
                }
                if (hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()) {
                    continue;
                }
                keptPlans.add(plan);
            }

            driverPlans.stream()
                    .filter(plan -> !(hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()))
                    .filter(plan -> plan.getBundleSize() == 1)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            driverPlans.stream()
                    .filter(plan -> !(hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()))
                    .filter(plan -> plan.getBundleSize() >= 4)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            driverPlans.stream()
                    .filter(plan -> !(hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()))
                    .filter(plan -> plan.getBundleSize() == 3)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            driverPlans.stream()
                    .filter(plan -> !(hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()))
                    .filter(plan -> plan.getBundleSize() >= 3)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            driverPlans.stream()
                    .filter(plan -> !(hardThreePolicy && hasVisibleWave && plan.isStressFallbackOnly()))
                    .filter(plan -> plan.getBundleSize() == 2)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            driverPlans.stream()
                    .filter(DispatchPlan::isWaitingForThirdOrder)
                    .findFirst()
                    .ifPresent(keptPlans::add);

            cappedPlans.addAll(keptPlans.stream()
                    .limit(MAX_PLANS_PER_DRIVER + 4L)
                    .toList());
        }

        List<DispatchPlan> actionablePlans = cappedPlans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .toList();
        List<DispatchPlan> primaryActionablePlans = actionablePlans.stream()
                .filter(this::isPrimaryActionablePlan)
                .toList();
        List<DispatchPlan> secondaryActionablePlans = actionablePlans.stream()
                .filter(plan -> !isPrimaryActionablePlan(plan))
                .toList();
        List<DispatchPlan> holdPlans = cappedPlans.stream()
                .filter(DispatchPlan::isWaitingForThirdOrder)
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .toList();

        // Step 2: Match actionable plans in two passes.
        // Pass 1 favors launchable clean waves and non-fallback real plans.
        // Pass 2 only considers secondary actionable plans for remaining capacity.
        Set<String> usedDrivers = new HashSet<>();
        Set<String> usedOrders = new HashSet<>();
        List<DispatchPlan> selected = new ArrayList<>();
        matchZoneGroups(primaryActionablePlans, usedDrivers, usedOrders, selected);
        matchZoneGroups(secondaryActionablePlans, usedDrivers, usedOrders, selected);

        Map<String, DispatchPlan> bestHoldByDriver = new LinkedHashMap<>();
        for (DispatchPlan holdPlan : holdPlans) {
            String driverId = holdPlan.getDriver().getId();
            if (usedDrivers.contains(driverId) || globalPlanExistsForDriver(actionablePlans, driverId)) {
                continue;
            }
            bestHoldByDriver.merge(driverId, holdPlan,
                    (current, candidate) -> candidate.getTotalScore() > current.getTotalScore() ? candidate : current);
        }
        selected.addAll(bestHoldByDriver.values());
        return selected;
    }

    private void matchZoneGroups(List<DispatchPlan> plans,
                                 Set<String> usedDrivers,
                                 Set<String> usedOrders,
                                 List<DispatchPlan> selected) {
        if (plans.isEmpty()) {
            return;
        }
        Map<String, List<DispatchPlan>> zoneGroups = new LinkedHashMap<>();
        for (DispatchPlan plan : plans) {
            String zoneKey = getZoneKey(plan);
            zoneGroups.computeIfAbsent(zoneKey, k -> new ArrayList<>()).add(plan);
        }
        for (List<DispatchPlan> groupPlans : zoneGroups.values()) {
            List<List<DispatchPlan>> subGroups = partitionGroup(groupPlans);
            for (List<DispatchPlan> subGroup : subGroups) {
                List<DispatchPlan> matched = auctionMatch(subGroup, usedDrivers, usedOrders);
                selected.addAll(matched);
                for (DispatchPlan plan : matched) {
                    usedDrivers.add(plan.getDriver().getId());
                    for (Order order : plan.getOrders()) {
                        usedOrders.add(order.getId());
                    }
                }
            }
        }
    }

    private boolean isPrimaryActionablePlan(DispatchPlan plan) {
        if (plan == null || plan.getOrders().isEmpty()) {
            return false;
        }
        if (plan.isWaveLaunchEligible()) {
            return true;
        }
        if (plan.getBundleSize() >= 3) {
            return true;
        }
        return !plan.isStressFallbackOnly();
    }

    private boolean globalPlanExistsForDriver(List<DispatchPlan> actionablePlans, String driverId) {
        return actionablePlans.stream()
                .anyMatch(plan -> plan.getDriver().getId().equals(driverId));
    }

    /**
     * Auction-based weighted matching for a zone group.
     *
     * Algorithm:
     * 1. Sort plans by score descending
     * 2. Each "round", highest unmatched plan claims its driver+orders
     * 3. If conflict, the losing plan's driver becomes available for next best plan
     * 4. Iterate until stable or all plans processed
     *
     * This approximates the optimal weighted matching better than pure greedy
     * by allowing re-assignment when a higher-value plan appears.
     */
    private List<DispatchPlan> auctionMatch(List<DispatchPlan> plans,
                                             Set<String> globalUsedDrivers,
                                             Set<String> globalUsedOrders) {

        // Sort by score descending
        plans.sort((a, b) -> {
            int waveCompare = Boolean.compare(b.isWaveLaunchEligible(), a.isWaveLaunchEligible());
            if (waveCompare != 0) {
                return waveCompare;
            }
            int fallbackCompare = Boolean.compare(a.isStressFallbackOnly(), b.isStressFallbackOnly());
            if (fallbackCompare != 0) {
                return fallbackCompare;
            }
            int bundleCompare = Integer.compare(b.getBundleSize(), a.getBundleSize());
            if (bundleCompare != 0) {
                return bundleCompare;
            }
            return Double.compare(b.getTotalScore(), a.getTotalScore());
        });

        // Track local assignments: driverId -> best plan
        Map<String, DispatchPlan> driverAssignment = new LinkedHashMap<>();
        Set<String> localUsedOrders = new HashSet<>();
        List<DispatchPlan> result = new ArrayList<>();

        for (DispatchPlan plan : plans) {
            String driverId = plan.getDriver().getId();

            // Skip globally used drivers/orders
            if (globalUsedDrivers.contains(driverId)) continue;

            // Confidence gate
            if (plan.getConfidence() < MIN_CONFIDENCE) continue;

            // Check order conflicts
            boolean orderConflict = false;
            for (Order order : plan.getOrders()) {
                if (globalUsedOrders.contains(order.getId())
                        || localUsedOrders.contains(order.getId())) {
                    orderConflict = true;
                    break;
                }
            }
            if (orderConflict) continue;

            // Check if this driver already has a plan — auction: replace if better
            DispatchPlan existing = driverAssignment.get(driverId);
            if (existing != null) {
                if (plan.getTotalScore() > existing.getTotalScore()) {
                    // Remove old assignment, free its orders
                    for (Order o : existing.getOrders()) {
                        localUsedOrders.remove(o.getId());
                    }
                    result.remove(existing);

                    // Assign new plan
                    driverAssignment.put(driverId, plan);
                    for (Order o : plan.getOrders()) {
                        localUsedOrders.add(o.getId());
                    }
                    result.add(plan);
                }
                // else keep existing (higher score already)
            } else {
                // New assignment
                driverAssignment.put(driverId, plan);
                for (Order o : plan.getOrders()) {
                    localUsedOrders.add(o.getId());
                }
                result.add(plan);
            }
        }

        return result;
    }

    /**
     * Get zone key for grouping — uses first order's pickup region.
     * Hold/reposition plans go to a special group.
     */
    private String getZoneKey(DispatchPlan plan) {
        if (plan.getOrders().isEmpty()) return "__IDLE__";
        return plan.getOrders().get(0).getPickupRegionId();
    }

    /**
     * Partition a zone group into sub-groups of at most MAX_DRIVERS_PER_GROUP.
     */
    private List<List<DispatchPlan>> partitionGroup(List<DispatchPlan> group) {
        if (group.size() <= MAX_DRIVERS_PER_GROUP * MAX_PLANS_PER_DRIVER) {
            return List.of(group);
        }

        List<List<DispatchPlan>> subGroups = new ArrayList<>();
        for (int i = 0; i < group.size(); i += MAX_DRIVERS_PER_GROUP * MAX_PLANS_PER_DRIVER) {
            int end = Math.min(i + MAX_DRIVERS_PER_GROUP * MAX_PLANS_PER_DRIVER, group.size());
            subGroups.add(group.subList(i, end));
        }
        return subGroups;
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
                double deliveryDist = order.getPickupPoint()
                        .distanceTo(order.getDropoffPoint()) / 1000.0;
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
