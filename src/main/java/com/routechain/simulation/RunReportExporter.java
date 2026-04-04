package com.routechain.simulation;

import com.routechain.domain.Enums.DeliveryServiceTier;
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
            List<Long> dispatchDecisionLatencySamples,
            List<Long> modelInferenceLatencySamples,
            List<Long> neuralPriorLatencySamples,
            List<DispatchStageTimings> dispatchStageTimingSamples,
            List<Long> replayRetrainLatencySamples,
            List<Long> assignmentAgingLatencySamples,
            double tickThroughputPerSec,
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
            int postDropOpportunityCount,
            int postDropOrderHitCount,
            double totalPredictedPostDropOpportunity,
            double totalTrafficForecastAbsError,
            double totalWeatherForecastHitRate,
            int forecastDecisionCount,
            double totalBorrowSuccessCalibrationGap,
            int borrowSuccessCalibrationCount,
            int noDriverFoundOrderCount,
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
        double postDropOrderHitRate = postDropOpportunityCount > 0
                ? postDropOrderHitCount * 100.0 / postDropOpportunityCount
                : 0.0;
        double avgPredictedPostDropOpportunity = routeMetricPlanCount > 0
                ? totalPredictedPostDropOpportunity / routeMetricPlanCount
                : 0.0;
        int borrowedExecutedCount = Math.max(0, safeRecovery.executedBorrowedCount());
        int fallbackExecutedCount = Math.max(0, safeRecovery.executedFallbackCount());
        int waveExecutedCount = Math.max(0, safeRecovery.executedWaveCount() + safeRecovery.executedExtensionCount());
        double borrowedDeadheadPerExecutedOrderKm = borrowedExecutedCount > 0
                ? borrowedExecutedDeadheadKm / borrowedExecutedCount : 0.0;
        double fallbackDeadheadPerExecutedOrderKm = fallbackExecutedCount > 0
                ? fallbackExecutedDeadheadKm / fallbackExecutedCount : 0.0;
        double waveDeadheadPerExecutedOrderKm = waveExecutedCount > 0
                ? waveExecutedDeadheadKm / waveExecutedCount : 0.0;
        Map<String, ServiceTierMetrics> serviceTierBreakdown = buildServiceTierBreakdown(allKnownOrders);
        String dominantServiceTier = serviceTierBreakdown.entrySet().stream()
                .max(Map.Entry.comparingByValue((left, right) -> Integer.compare(left.orderCount(), right.orderCount())))
                .map(Map.Entry::getKey)
                .orElse(DeliveryServiceTier.classifyScenario(scenarioName).wireValue());
        double merchantPrepMaeMinutes = computeMerchantPrepMae(allKnownOrders);
        ForecastCalibrationSummary forecastCalibrationSummary = new ForecastCalibrationSummary(
                etaMAE,
                merchantPrepMaeMinutes,
                Math.abs(postDropOrderHitRate / 100.0 - avgPredictedPostDropOpportunity),
                avgPredictedPostDropOpportunity
        );
        DispatchStageBreakdown stageLatency = DispatchStageBreakdown.fromSamples(
                dispatchStageTimingSamples,
                replayRetrainLatencySamples);
        LatencyBreakdown latency = LatencyBreakdown.fromSamples(
                dispatchDecisionLatencySamples,
                modelInferenceLatencySamples,
                neuralPriorLatencySamples,
                assignmentAgingLatencySamples,
                tickThroughputPerSec
        );
        MeasurementSanityCheck measurementSanity = MeasurementSanityCheck.evaluate(latency, avgAssignLatency);
        double avgTrafficForecastError = forecastDecisionCount > 0
                ? totalTrafficForecastAbsError / forecastDecisionCount
                : 0.0;
        double avgWeatherForecastHitRate = forecastDecisionCount > 0
                ? totalWeatherForecastHitRate / forecastDecisionCount
                : 1.0;
        double avgBorrowSuccessCalibration = borrowSuccessCalibrationCount > 0
                ? totalBorrowSuccessCalibrationGap / borrowSuccessCalibrationCount
                : 0.0;
        IntelligenceScorecard intelligence = buildIntelligenceScorecard(
                completionRate,
                onTimeRate,
                etaMAE,
                deadheadPerCompletedOrderKm,
                deadheadPerAssignedOrderKm,
                postDropOrderHitRate,
                expectedPostCompletionEmptyKm,
                deliveryCorridorQuality,
                thirdOrderLaunchRate,
                safeRecovery,
                merchantPrepMaeMinutes,
                forecastCalibrationSummary.continuationCalibrationGap(),
                avgTrafficForecastError,
                avgWeatherForecastHitRate,
                avgBorrowSuccessCalibration,
                noDriverFoundOrderCount,
                totalOrders,
                fallbackDeadheadPerExecutedOrderKm,
                borrowedDeadheadPerExecutedOrderKm
        );
        ScenarioAcceptanceResult acceptance = buildScenarioAcceptance(
                scenarioName,
                dominantServiceTier,
                latency,
                intelligence,
                measurementSanity,
                weatherSeverityOf(scenarioName),
                noDriverFoundOrderCount
        );

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
                postDropOrderHitRate,
                borrowedDeadheadPerExecutedOrderKm,
                fallbackDeadheadPerExecutedOrderKm,
                waveDeadheadPerExecutedOrderKm,
                stageLatency,
                latency,
                intelligence,
                acceptance,
                dominantServiceTier,
                serviceTierBreakdown,
                forecastCalibrationSummary,
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

    private Map<String, ServiceTierMetrics> buildServiceTierBreakdown(List<Order> orders) {
        Map<String, List<Order>> byTier = new java.util.LinkedHashMap<>();
        for (Order order : orders) {
            String tier = DeliveryServiceTier.classify(order).wireValue();
            byTier.computeIfAbsent(tier, ignored -> new ArrayList<>()).add(order);
        }
        Map<String, ServiceTierMetrics> breakdown = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, List<Order>> entry : byTier.entrySet()) {
            List<Order> tierOrders = entry.getValue();
            int completed = (int) tierOrders.stream()
                    .filter(order -> order.getStatus() == OrderStatus.DELIVERED)
                    .count();
            double completionRate = tierOrders.isEmpty()
                    ? 0.0
                    : completed * 100.0 / tierOrders.size();
            double avgPromisedEta = tierOrders.stream()
                    .mapToInt(Order::getPromisedEtaMinutes)
                    .average()
                    .orElse(0.0);
            double avgQuotedFee = tierOrders.stream()
                    .mapToDouble(Order::getQuotedFee)
                    .average()
                    .orElse(0.0);
            breakdown.put(entry.getKey(), new ServiceTierMetrics(
                    entry.getKey(),
                    tierOrders.size(),
                    completed,
                    completionRate,
                    avgPromisedEta,
                    avgQuotedFee
            ));
        }
        if (breakdown.isEmpty()) {
            String fallbackTier = DeliveryServiceTier.classifyScenario(scenarioName).wireValue();
            breakdown.put(fallbackTier, new ServiceTierMetrics(fallbackTier, 0, 0, 0.0, 0.0, 0.0));
        }
        return breakdown;
    }

    private double computeMerchantPrepMae(List<Order> orders) {
        double totalError = 0.0;
        int count = 0;
        for (Order order : orders) {
            if (order.getPredictedReadyAt() == null || order.getActualReadyAt() == null) {
                continue;
            }
            double predicted = java.time.Duration.between(order.getCreatedAt(), order.getPredictedReadyAt())
                    .toSeconds() / 60.0;
            double actual = java.time.Duration.between(order.getCreatedAt(), order.getActualReadyAt())
                    .toSeconds() / 60.0;
            totalError += Math.abs(actual - predicted);
            count++;
        }
        return count > 0 ? totalError / count : 0.0;
    }

    private IntelligenceScorecard buildIntelligenceScorecard(double completionRate,
                                                             double onTimeRate,
                                                             double etaMAE,
                                                             double deadheadPerCompletedOrderKm,
                                                             double deadheadPerAssignedOrderKm,
                                                             double postDropOrderHitRate,
                                                             double expectedPostCompletionEmptyKm,
                                                             double deliveryCorridorQuality,
                                                             double thirdOrderLaunchRate,
                                                             DispatchRecoveryDecomposition recovery,
                                                             double merchantPrepMaeMinutes,
                                                             double continuationCalibrationGap,
                                                             double trafficForecastError,
                                                             double weatherForecastHitRate,
                                                             double borrowSuccessCalibration,
                                                             int noDriverFoundOrderCount,
                                                             int totalOrders,
                                                             double fallbackDeadheadPerExecutedOrderKm,
                                                             double borrowedDeadheadPerExecutedOrderKm) {
        double waveQuality = clamp01(
                thirdOrderLaunchRate / 100.0 * 0.45
                        + (recovery == null ? 0.0 : recovery.holdConversionRate() / 100.0) * 0.30
                        + (recovery == null ? 0.0 : recovery.waveExecutionRate() / 100.0) * 0.25);
        double noDriverFoundRate = totalOrders > 0
                ? Math.min(100.0, noDriverFoundOrderCount * 100.0 / totalOrders)
                : 0.0;
        double coverageRecoveryRate = clamp01(realRate(recovery));
        double emptyZoneRecoveryRate = clamp01(postDropOrderHitRate / 100.0);
        double reserveStability = clamp01(
                1.0 - (recovery == null ? 0.0 : recovery.borrowedSelectionRate() / 100.0) * 0.65
                        - noDriverFoundRate / 100.0 * 0.35);
        double weatherAvoidanceQuality = clamp01(
                1.0 - Math.min(1.0, borrowedDeadheadPerExecutedOrderKm / 3.5) * 0.35
                        - Math.min(1.0, fallbackDeadheadPerExecutedOrderKm / 3.5) * 0.15
                        + Math.min(1.0, onTimeRate / 100.0) * 0.20
                        + Math.min(1.0, weatherForecastHitRate) * 0.30);
        double congestionAvoidanceQuality = clamp01(
                deliveryCorridorQuality * 0.45
                        + Math.min(1.0, onTimeRate / 100.0) * 0.20
                        + Math.max(0.0, 1.0 - deadheadPerAssignedOrderKm / 3.0) * 0.35);
        double businessScore = clamp01(
                Math.max(0.0, 1.0 - deadheadPerCompletedOrderKm / 2.5) * 0.34
                        + completionRate / 100.0 * 0.28
                        + postDropOrderHitRate / 100.0 * 0.22
                        + Math.max(0.0, 1.0 - expectedPostCompletionEmptyKm / 2.6) * 0.16);
        double routingScore = clamp01(
                onTimeRate / 100.0 * 0.28
                        + Math.max(0.0, 1.0 - etaMAE / 15.0) * 0.12
                        + weatherAvoidanceQuality * 0.18
                        + congestionAvoidanceQuality * 0.18
                        + waveQuality * 0.24);
        double networkScore = clamp01(
                Math.max(0.0, 1.0 - noDriverFoundRate / 100.0) * 0.30
                        + reserveStability * 0.18
                        + coverageRecoveryRate * 0.28
                        + emptyZoneRecoveryRate * 0.24);
        double forecastScore = clamp01(
                Math.max(0.0, 1.0 - merchantPrepMaeMinutes / 12.0) * 0.30
                        + Math.max(0.0, 1.0 - Math.abs(continuationCalibrationGap)) * 0.30
                        + Math.max(0.0, 1.0 - trafficForecastError) * 0.18
                        + clamp01(weatherForecastHitRate) * 0.12
                        + Math.max(0.0, 1.0 - borrowSuccessCalibration) * 0.10);
        String primaryVerdict = verdictForScore(businessScore);
        String secondaryVerdict = verdictForScore((businessScore + routingScore + networkScore + forecastScore) / 4.0);
        return new IntelligenceScorecard(
                businessScore,
                routingScore,
                networkScore,
                forecastScore,
                weatherAvoidanceQuality,
                congestionAvoidanceQuality,
                waveQuality,
                noDriverFoundRate,
                reserveStability,
                coverageRecoveryRate,
                emptyZoneRecoveryRate,
                trafficForecastError,
                clamp01(weatherForecastHitRate),
                borrowSuccessCalibration,
                primaryVerdict,
                secondaryVerdict
        );
    }

    private ScenarioAcceptanceResult buildScenarioAcceptance(String scenarioName,
                                                             String serviceTier,
                                                             LatencyBreakdown latency,
                                                             IntelligenceScorecard intelligence,
                                                             MeasurementSanityCheck measurementSanity,
                                                             com.routechain.domain.Enums.WeatherProfile scenarioWeather,
                                                             int noDriverFoundOrderCount) {
        boolean measurementPass = measurementSanity.valid();
        boolean performancePass = latency.dispatchP95Ms() <= 120.0 && latency.dispatchP99Ms() <= 180.0;
        boolean intelligencePass = intelligence.businessScore() >= 0.55
                && intelligence.routingScore() >= 0.45
                && intelligence.networkScore() >= 0.45;
        boolean safetyPass = scenarioWeather == com.routechain.domain.Enums.WeatherProfile.HEAVY_RAIN
                || scenarioWeather == com.routechain.domain.Enums.WeatherProfile.STORM
                ? intelligence.weatherAvoidanceQuality() >= 0.45 && noDriverFoundOrderCount <= 3
                : true;
        boolean overallPass = measurementPass && performancePass && intelligencePass && safetyPass;
        String notes = String.join(" | ", measurementSanity.warnings());
        return new ScenarioAcceptanceResult(
                scenarioName,
                serviceTier,
                "local-production-small-50",
                measurementPass,
                performancePass,
                intelligencePass,
                safetyPass,
                overallPass,
                intelligence.primaryVerdict(),
                intelligence.secondaryVerdict(),
                notes
        );
    }

    private double realRate(DispatchRecoveryDecomposition recovery) {
        if (recovery == null) {
            return 0.0;
        }
        return clamp01(recovery.localCoverageExecutionRate() / 100.0);
    }

    private String verdictForScore(double score) {
        if (score >= 0.72) {
            return "STRONG";
        }
        if (score >= 0.55) {
            return "PASSING";
        }
        if (score >= 0.40) {
            return "WATCH";
        }
        return "WEAK";
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private com.routechain.domain.Enums.WeatherProfile weatherSeverityOf(String scenarioName) {
        if (scenarioName == null) {
            return com.routechain.domain.Enums.WeatherProfile.CLEAR;
        }
        String normalized = scenarioName.toLowerCase();
        if (normalized.contains("storm")) {
            return com.routechain.domain.Enums.WeatherProfile.STORM;
        }
        if (normalized.contains("heavy_rain")) {
            return com.routechain.domain.Enums.WeatherProfile.HEAVY_RAIN;
        }
        if (normalized.contains("rain")) {
            return com.routechain.domain.Enums.WeatherProfile.LIGHT_RAIN;
        }
        return com.routechain.domain.Enums.WeatherProfile.CLEAR;
    }

    private void addBundleOrder(Map<String, List<Order>> bundleOrders, Order order) {
        String bundleId = order.getBundleId();
        if (bundleId == null || bundleId.isBlank()) return;
        bundleOrders.computeIfAbsent(bundleId, k -> new ArrayList<>()).add(order);
    }
}
