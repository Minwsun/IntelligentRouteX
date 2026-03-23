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
import java.util.UUID;

/**
 * Collects data from the simulation engine and produces a RunReport.
 * Call generateReport() when the simulation stops or a snapshot is needed.
 */
public class RunReportExporter {

    private final String scenarioName;
    private final long seed;
    private final Instant startTime;

    public RunReportExporter(String scenarioName, long seed, Instant startTime) {
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
            Instant endTime) {

        String runId = "RUN-" + UUID.randomUUID().toString().substring(0, 8);
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
        double bundleSuccessRate = computeBundleSuccessRate(completedOrders, cancelledOrders);

        double avgAssignLatency = totalAssignments > 0
                ? (double) totalAssignmentLatencyMs / totalAssignments : 0;
        double avgConfidence = allKnownOrders.stream()
                .filter(o -> o.getPredictedAssignmentConfidence() > 0)
                .mapToDouble(Order::getPredictedAssignmentConfidence)
                .average().orElse(0.0);
        double etaMAE = computeEtaMAE(completedOrders);

        double avgFee = totalDelivered > 0
                ? totalEarnings / totalDelivered : 0;

        return new RunReport(
                runId, scenarioName, seed, startTime, endTime, totalTicks,
                totalOrders, totalDriverCount,
                completionRate, onTimeRate, cancellationRate, failedOrderRate,
                deadheadDistRatio, deadheadTimeRatio, avgUtilization,
                avgOrdersPerHour, avgNetEarning,
                bundleRate, bundleSuccessRate, reDispatchCount,
                avgAssignLatency, avgConfidence, etaMAE,
                surgeEvents, shortageEvents, avgFee
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

    private double computeBundleSuccessRate(List<Order> completedOrders, List<Order> cancelledOrders) {
        Map<String, List<Order>> bundleOrders = new HashMap<>();
        for (Order order : completedOrders) {
            addBundleOrder(bundleOrders, order);
        }
        for (Order order : cancelledOrders) {
            addBundleOrder(bundleOrders, order);
        }

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

    private void addBundleOrder(Map<String, List<Order>> bundleOrders, Order order) {
        String bundleId = order.getBundleId();
        if (bundleId == null || bundleId.isBlank()) return;
        bundleOrders.computeIfAbsent(bundleId, k -> new ArrayList<>()).add(order);
    }
}
