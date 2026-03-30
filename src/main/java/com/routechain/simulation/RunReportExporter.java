package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.Enums.OrderStatus;
import com.routechain.domain.Enums.DriverState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Collects data from the simulation engine and produces a RunReport.
 * Call generateReport() when the simulation stops or a snapshot is needed.
 */
public class RunReportExporter {

    private final String runId;
    private final String scenarioName;
    private final long seed;
    private final Instant startTime;

    public RunReportExporter(String runId, String scenarioName, long seed, Instant startTime) {
        this.runId = runId == null || runId.isBlank() ? "RUN-UNKNOWN" : runId;
        this.scenarioName = scenarioName;
        this.seed = seed;
        this.startTime = startTime;
    }

    /**
     * Generate a complete RunReport from current simulation state.
     */
    public RunReport generateReport(
            List<Driver> drivers,
            List<Order> completedOrders,
            List<Order> cancelledOrders,
            List<Order> activeOrders,
            int totalDelivered,
            int totalLateDelivered,
            double totalDeadheadKm,
            double totalEarnings,
            long totalAssignmentLatencyMs,
            int totalAssignments,
            int totalBundled,
            int reDispatchCount,
            long totalTicks,
            int surgeEvents,
            int shortageEvents,
            double totalDeliveryCorridorScore,
            double totalLastDropLandingScore,
            double totalExpectedPostCompletionEmptyKm,
            double totalExpectedNextOrderIdleMinutes,
            double totalZigZagPenalty,
            int routeMetricPlanCount,
            int lastDropGoodZoneCount,
            int visibleBundleThreePlusCount,
            int cleanRegimeOrderDecisionCount,
            int cleanRegimeSubThreeSelectedCount,
            int cleanRegimeWaveAssemblyHoldCount,
            int cleanRegimeThirdOrderLaunchCount,
            int stressDowngradeSelectionCount,
            int totalSelectedOrderPlanCount,
            int realAssignedPlanCount,
            int holdOnlySelectionCount,
            int prePickupAugmentationCount,
            double borrowedExecutedDeadheadKm,
            double fallbackExecutedDeadheadKm,
            double waveExecutedDeadheadKm,
            DispatchRecoveryDecomposition recovery,
            Instant endTime) {

        int totalOrders = totalDelivered + cancelledOrders.size() + activeOrders.size();
        int totalDriverCount = drivers.size();

        List<Order> allKnownOrders = new ArrayList<>(
                completedOrders.size() + cancelledOrders.size() + activeOrders.size());
        allKnownOrders.addAll(completedOrders);
        allKnownOrders.addAll(cancelledOrders);
        allKnownOrders.addAll(activeOrders);

        double completionRate = totalOrders > 0
                ? (double) totalDelivered / totalOrders * 100 : 0;
        double onTimeRate = totalDelivered > 0
                ? (double) (totalDelivered - totalLateDelivered) / totalDelivered * 100 : 100;
        double cancellationRate = totalOrders > 0
                ? (double) cancelledOrders.size() / totalOrders * 100 : 0;
        long failedOrders = allKnownOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.FAILED)
                .count();
        double failedOrderRate = totalOrders > 0
                ? (double) failedOrders / totalOrders * 100 : 0;

        double avgUtilization = drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE)
                .mapToDouble(Driver::getComputedUtilization)
                .average().orElse(0);

        double avgOrdersPerHour = drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE)
                .mapToDouble(Driver::getOrdersPerHour)
                .average().orElse(0);

        double avgNetEarning = drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE)
                .mapToDouble(Driver::getAvgEarningPerHour)
                .average().orElse(0);

        double totalDrivenKm = totalDeadheadKm + (totalDelivered * 3.0);
        double deadheadDistRatio = totalDrivenKm > 0
                ? totalDeadheadKm / totalDrivenKm * 100 : 0;

        double totalOnlineTicks = drivers.stream()
                .mapToLong(Driver::getOnlineTicks).sum();
        double totalIdleTicks = drivers.stream()
                .mapToLong(Driver::getIdleTicks).sum();
        double deadheadTimeRatio = totalOnlineTicks > 0
                ? totalIdleTicks / totalOnlineTicks * 100 : 0;

        double bundleRate = totalOrders > 0
                ? (double) totalBundled / totalOrders * 100 : 0;
        Map<String, List<Order>> bundleOrders = groupOrdersByBundle(allKnownOrders);
        double bundleSuccessRate = computeBundleSuccessRate(bundleOrders);
        double avgObservedBundleSize = computeAverageObservedBundleSize(bundleOrders);
        int maxObservedBundleSize = computeMaxObservedBundleSize(bundleOrders);
        double bundleThreePlusRate = computeBundleThreePlusRate(bundleOrders);

        double avgAssignLatency = totalAssignments > 0
                ? (double) totalAssignmentLatencyMs / totalAssignments : 0;
        double avgConfidence = allKnownOrders.stream()
                .filter(o -> o.getPredictedAssignmentConfidence() > 0)
                .mapToDouble(Order::getPredictedAssignmentConfidence)
                .average().orElse(0.0);
        double etaMAE = computeEtaMAE(completedOrders);

        double avgFee = totalDelivered > 0
                ? totalEarnings / totalDelivered : 0;
        double visibleBundleThreePlusRate = routeMetricPlanCount > 0
                ? visibleBundleThreePlusCount * 100.0 / routeMetricPlanCount : 0.0;
        double lastDropGoodZoneRate = routeMetricPlanCount > 0
                ? lastDropGoodZoneCount * 100.0 / routeMetricPlanCount : 0.0;
        double expectedPostCompletionEmptyKm = routeMetricPlanCount > 0
                ? totalExpectedPostCompletionEmptyKm / routeMetricPlanCount : 0.0;
        double nextOrderIdleMinutes = routeMetricPlanCount > 0
                ? totalExpectedNextOrderIdleMinutes / routeMetricPlanCount : 0.0;
        double deliveryCorridorQuality = routeMetricPlanCount > 0
                ? totalDeliveryCorridorScore / routeMetricPlanCount : 0.0;
        double zigZagPenaltyAvg = routeMetricPlanCount > 0
                ? totalZigZagPenalty / routeMetricPlanCount : 0.0;
        int cleanRegimePolicyEvents = cleanRegimeOrderDecisionCount + cleanRegimeWaveAssemblyHoldCount;
        double realAssignmentRate = totalSelectedOrderPlanCount > 0
                ? realAssignedPlanCount * 100.0 / totalSelectedOrderPlanCount
                : 0.0;
        double selectedSubThreeRateInCleanRegime = cleanRegimeOrderDecisionCount > 0
                ? cleanRegimeSubThreeSelectedCount * 100.0 / cleanRegimeOrderDecisionCount
                : 0.0;
        double waveAssemblyWaitRate = cleanRegimePolicyEvents > 0
                ? cleanRegimeWaveAssemblyHoldCount * 100.0 / cleanRegimePolicyEvents
                : 0.0;
        double thirdOrderLaunchRate = cleanRegimePolicyEvents > 0
                ? cleanRegimeThirdOrderLaunchCount * 100.0 / cleanRegimePolicyEvents
                : 0.0;
        double stressDowngradeRate = totalSelectedOrderPlanCount > 0
                ? stressDowngradeSelectionCount * 100.0 / totalSelectedOrderPlanCount
                : 0.0;
        double prePickupAugmentRate = realAssignedPlanCount > 0
                ? prePickupAugmentationCount * 100.0 / realAssignedPlanCount
                : 0.0;
        double holdOnlySelectionRate = totalSelectedOrderPlanCount > 0
                ? holdOnlySelectionCount * 100.0 / totalSelectedOrderPlanCount
                : 0.0;
        DispatchRecoveryDecomposition safeRecovery = recovery == null
                ? DispatchRecoveryDecomposition.empty()
                : recovery;
        double avgAssignedDeadheadKm = realAssignedPlanCount > 0
                ? totalDeadheadKm / realAssignedPlanCount : 0.0;
        double deadheadPerCompletedOrderKm = totalDelivered > 0
                ? totalDeadheadKm / totalDelivered : 0.0;
        double deadheadPerAssignedOrderKm = totalAssignments > 0
                ? totalDeadheadKm / totalAssignments : 0.0;
        int borrowedExecutedCount = Math.max(0, safeRecovery.executedBorrowedCount());
        int fallbackExecutedCount = Math.max(0, safeRecovery.executedFallbackCount());
        int waveExecutedCount = Math.max(0, safeRecovery.executedWaveCount() + safeRecovery.executedExtensionCount());
        double borrowedDeadheadPerExecutedOrderKm = borrowedExecutedCount > 0
                ? borrowedExecutedDeadheadKm / borrowedExecutedCount : 0.0;
        double fallbackDeadheadPerExecutedOrderKm = fallbackExecutedCount > 0
                ? fallbackExecutedDeadheadKm / fallbackExecutedCount : 0.0;
        double waveDeadheadPerExecutedOrderKm = waveExecutedCount > 0
                ? waveExecutedDeadheadKm / waveExecutedCount : 0.0;

        return new RunReport(
                runId, scenarioName, seed, startTime, endTime, totalTicks,
                totalOrders, totalDriverCount,
                completionRate, onTimeRate, cancellationRate, failedOrderRate,
                deadheadDistRatio, deadheadTimeRatio, avgUtilization,
                avgOrdersPerHour, avgNetEarning,
                bundleRate, bundleSuccessRate, avgObservedBundleSize,
                maxObservedBundleSize, bundleThreePlusRate, reDispatchCount,
                avgAssignLatency, avgConfidence, etaMAE,
                surgeEvents, shortageEvents, avgFee,
                visibleBundleThreePlusRate, lastDropGoodZoneRate,
                expectedPostCompletionEmptyKm, nextOrderIdleMinutes,
                deliveryCorridorQuality, zigZagPenaltyAvg,
                realAssignmentRate,
                selectedSubThreeRateInCleanRegime, waveAssemblyWaitRate,
                thirdOrderLaunchRate, stressDowngradeRate,
                prePickupAugmentRate, holdOnlySelectionRate,
                avgAssignedDeadheadKm,
                deadheadPerCompletedOrderKm,
                deadheadPerAssignedOrderKm,
                borrowedDeadheadPerExecutedOrderKm,
                fallbackDeadheadPerExecutedOrderKm,
                waveDeadheadPerExecutedOrderKm,
                safeRecovery
        );
    }

    private double computeEtaMAE(List<Order> completedOrders) {
        if (completedOrders.isEmpty()) return 0;

        double totalError = 0;
        int count = 0;
        for (Order order : completedOrders) {
            if (order.getDeliveredAt() != null && order.getCreatedAt() != null) {
                double actualMinutes = java.time.Duration.between(
                        order.getCreatedAt(), order.getDeliveredAt()).toSeconds() / 60.0;
                double predictedMinutes = order.getPredictedTravelTime() > 0
                        ? order.getPredictedTravelTime()
                        : order.getPromisedEtaMinutes();
                totalError += Math.abs(actualMinutes - predictedMinutes);
                count++;
            }
        }
        return count > 0 ? totalError / count : 0;
    }

    private double computeBundleSuccessRate(Map<String, List<Order>> bundleOrders) {
        int multiOrderBundles = 0;
        int successfulBundles = 0;
        for (List<Order> orders : bundleOrders.values()) {
            if (orders.size() <= 1) continue;
            multiOrderBundles++;
            boolean success = orders.stream().allMatch(
                    o -> o.getStatus() == OrderStatus.DELIVERED && !o.isLate());
            if (success) {
                successfulBundles++;
            }
        }
        return multiOrderBundles > 0
                ? successfulBundles * 100.0 / multiOrderBundles
                : 0.0;
    }

    private double computeAverageObservedBundleSize(Map<String, List<Order>> bundleOrders) {
        return bundleOrders.values().stream()
                .filter(orders -> orders.size() > 1)
                .mapToInt(List::size)
                .average()
                .orElse(0.0);
    }

    private int computeMaxObservedBundleSize(Map<String, List<Order>> bundleOrders) {
        return bundleOrders.values().stream()
                .mapToInt(List::size)
                .max()
                .orElse(1);
    }

    private double computeBundleThreePlusRate(Map<String, List<Order>> bundleOrders) {
        long multiOrderBundles = bundleOrders.values().stream()
                .filter(orders -> orders.size() > 1)
                .count();
        if (multiOrderBundles == 0) {
            return 0.0;
        }

        long threePlusBundles = bundleOrders.values().stream()
                .filter(orders -> orders.size() >= 3)
                .count();
        return threePlusBundles * 100.0 / multiOrderBundles;
    }

    private Map<String, List<Order>> groupOrdersByBundle(List<Order> orders) {
        Map<String, List<Order>> bundleOrders = new HashMap<>();
        for (Order order : orders) {
            addBundleOrder(bundleOrders, order);
        }
        return bundleOrders;
    }

    private void addBundleOrder(Map<String, List<Order>> bundleOrders, Order order) {
        String bundleId = order.getBundleId();
        if (bundleId == null || bundleId.isBlank()) return;
        bundleOrders.computeIfAbsent(bundleId, k -> new ArrayList<>()).add(order);
    }
}
