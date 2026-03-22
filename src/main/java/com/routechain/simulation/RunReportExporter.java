package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Collects data from the simulation engine and produces a RunReport.
 * Call generateReport() when the simulation stops or a snapshot is needed.
 */
public class RunReportExporter {

    private final String scenarioName;
    private final long seed;
    private final Instant startTime;

    public RunReportExporter(String scenarioName, long seed) {
        this.scenarioName = scenarioName;
        this.seed = seed;
        this.startTime = Instant.now();
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
            int shortageEvents) {

        String runId = "RUN-" + UUID.randomUUID().toString().substring(0, 8);
        Instant endTime = Instant.now();

        // ── Volume ──────────────────────────────────────────────────────
        int totalOrders = totalDelivered + cancelledOrders.size() + activeOrders.size();
        int totalDriverCount = drivers.size();

        // ── Operations KPIs ─────────────────────────────────────────────
        double completionRate = totalOrders > 0
                ? (double) totalDelivered / totalOrders * 100 : 0;
        double onTimeRate = totalDelivered > 0
                ? (double) (totalDelivered - totalLateDelivered) / totalDelivered * 100 : 100;
        double cancellationRate = totalOrders > 0
                ? (double) cancelledOrders.size() / totalOrders * 100 : 0;
        double failedOrderRate = 0; // TODO: track failed orders separately

        // ── Driver efficiency KPIs ──────────────────────────────────────
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

        double totalDrivenKm = totalDeadheadKm + (totalDelivered * 3.0); // rough
        double deadheadDistRatio = totalDrivenKm > 0
                ? totalDeadheadKm / totalDrivenKm * 100 : 0;

        double totalOnlineTicks = drivers.stream()
                .mapToLong(Driver::getOnlineTicks).sum();
        double totalIdleTicks = drivers.stream()
                .mapToLong(Driver::getIdleTicks).sum();
        double deadheadTimeRatio = totalOnlineTicks > 0
                ? totalIdleTicks / totalOnlineTicks * 100 : 0;

        // ── Bundle / Route KPIs ─────────────────────────────────────────
        double bundleRate = totalOrders > 0
                ? (double) totalBundled / totalOrders * 100 : 0;
        double bundleSuccessRate = 95.0; // placeholder — need bundle-level tracking

        // ── AI KPIs ─────────────────────────────────────────────────────
        double avgAssignLatency = totalAssignments > 0
                ? (double) totalAssignmentLatencyMs / totalAssignments : 0;

        double avgConfidence = 0.75; // placeholder — need per-assignment tracking

        // ETA MAE: compare predicted vs actual for completed orders
        double etaMAE = computeEtaMAE(completedOrders);

        // ── Avg fee ─────────────────────────────────────────────────────
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

    /**
     * Compute ETA Mean Absolute Error from completed orders.
     */
    private double computeEtaMAE(List<Order> completedOrders) {
        if (completedOrders.isEmpty()) return 0;

        double totalError = 0;
        int count = 0;
        for (Order order : completedOrders) {
            if (order.getDeliveredAt() != null && order.getCreatedAt() != null) {
                long actualMinutes = java.time.Duration.between(
                        order.getCreatedAt(), order.getDeliveredAt()).toMinutes();
                double error = Math.abs(actualMinutes - order.getPromisedEtaMinutes());
                totalError += error;
                count++;
            }
        }
        return count > 0 ? totalError / count : 0;
    }
}
