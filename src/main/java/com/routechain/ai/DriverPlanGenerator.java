package com.routechain.ai;

import com.routechain.ai.DriverDecisionContext.EndZoneCandidate;
import com.routechain.ai.DriverDecisionContext.OrderCluster;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.DispatchPlan;
import com.routechain.simulation.SelectionBucket;
import com.routechain.simulation.DispatchPlan.Bundle;
import com.routechain.simulation.DispatchPlan.Stop;
import com.routechain.simulation.DispatchPlan.Stop.StopType;
import com.routechain.simulation.SequenceOptimizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Generates driver-centric candidate plans from a local world snapshot.
 *
 * Mainline profile favors visible, realistic 3-4 order pickup waves.
 * Showcase profile allows 5-8 order waves only in exceptionally clean clusters.
 */
public class DriverPlanGenerator {

    private static final double HOLD_PLAN_BASE_SCORE = 0.03;
    private static final double REPOSITION_BASE_SCORE = 0.02;
    private static final int MAINLINE_MAX_TOTAL_BUNDLE_SIZE = 5;
    private boolean holdPlansEnabled = true;
    private boolean repositionPlansEnabled = true;
    private boolean smallBatchOnly = false;
    private OmegaDispatchAgent.ExecutionProfile executionProfile =
            OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC;

    public DriverPlanGenerator() {
    }

    static boolean prefersThreeOrderLaunch(OmegaDispatchAgent.ExecutionProfile executionProfile,
                                           DriverDecisionContext ctx) {
        if (executionProfile != OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC) {
            return false;
        }
        if (ctx == null) {
            return false;
        }
        if (ctx.stressRegime() == StressRegime.SEVERE_STRESS) {
            return false;
        }
        if (ctx.harshWeatherStress()) {
            return false;
        }
        boolean clusterSignal = ctx.compactClusterCount() >= 1
                || ctx.nearReadySameMerchantCount() >= 2
                || ctx.nearReadyOrders() >= 3
                || ctx.reachableOrders().size() >= 4;
        return ctx.reachableOrders().size() >= 3
                && clusterSignal
                && ctx.thirdOrderFeasibilityScore() >= 0.35
                && ctx.threeOrderSlackBuffer() >= 0.8
                && ctx.localCorridorExposure() <= 0.82;
    }

    private static boolean shouldWaitForLikelyThirdOrder(DriverDecisionContext ctx) {
        if (ctx == null) {
            return false;
        }
        if (ctx.stressRegime() == StressRegime.SEVERE_STRESS || ctx.harshWeatherStress()) {
            return false;
        }
        if (ctx.localReachableBacklog() < 2 || ctx.localReachableBacklog() > 3) {
            return false;
        }
        boolean borderlineBacklog = ctx.localReachableBacklog() == 2;
        boolean clusterSignal = ctx.nearReadySameMerchantCount() >= 2 || ctx.compactClusterCount() >= 1;
        boolean readinessSignal = ctx.nearReadyOrders() >= 2
                && (!borderlineBacklog
                || ctx.nearReadyOrders() >= 3
                || (ctx.thirdOrderFeasibilityScore() >= 0.82
                && ctx.waveAssemblyPressure() >= 0.40));
        double requiredThirdOrderFeasibility = borderlineBacklog ? 0.70 : 0.62;
        double requiredWaveAssemblyPressure = borderlineBacklog ? 0.40 : 0.38;
        double requiredSlaSlackMinutes = borderlineBacklog ? 2.4 : 2.0;
        double requiredSlackBuffer = borderlineBacklog ? 0.25 : 0.0;
        return clusterSignal
                && readinessSignal
                && ctx.thirdOrderFeasibilityScore() >= requiredThirdOrderFeasibility
                && ctx.waveAssemblyPressure() >= requiredWaveAssemblyPressure
                && ctx.effectiveSlaSlackMinutes() > requiredSlaSlackMinutes
                && ctx.threeOrderSlackBuffer() >= requiredSlackBuffer
                && ctx.localCorridorExposure() <= 0.55;
    }

    static boolean requiresHardThreeOrderLaunch(OmegaDispatchAgent.ExecutionProfile executionProfile,
                                                DriverDecisionContext ctx) {
        return prefersThreeOrderLaunch(executionProfile, ctx)
                && shouldWaitForLikelyThirdOrder(ctx);
    }

    public List<DispatchPlan> generate(DriverDecisionContext ctx,
                                       double trafficIntensity,
                                       WeatherProfile weather,
                                       int simulatedHour) {

        List<DispatchPlan> safeSingles = new ArrayList<>();
        List<DispatchPlan> sameMerchantWaves = new ArrayList<>();
        List<DispatchPlan> compactClusterWaves = new ArrayList<>();
        List<DispatchPlan> corridorAlignedWaves = new ArrayList<>();
        List<DispatchPlan> stretchMultiOrder = new ArrayList<>();
        List<DispatchPlan> idlePlans = new ArrayList<>();
        Driver driver = ctx.driver();
        StressRegime regime = ctx.stressRegime();
        boolean targetThreeOrderLaunch = prefersThreeOrderLaunch(executionProfile, ctx);
        boolean hardThreeOrderPolicy = requiresHardThreeOrderLaunch(executionProfile, ctx);

        List<OrderCluster> prioritizedClusters = new ArrayList<>(ctx.pickupClusters());
        prioritizedClusters.sort(Comparator.comparingDouble(
                (OrderCluster cluster) -> computeClusterPriority(cluster, weather, trafficIntensity))
                .reversed());
        int clusterLimit = clusterProcessingLimit(ctx, weather, trafficIntensity);
        int candidateBuildBudget = driverCandidateBuildBudget(ctx, weather, trafficIntensity);

        for (int clusterIndex = 0;
             clusterIndex < prioritizedClusters.size() && clusterIndex < clusterLimit;
             clusterIndex++) {
            OrderCluster cluster = prioritizedClusters.get(clusterIndex);
            if (cluster.orders().size() < 2) {
                continue;
            }

            int dynamicMax = Math.min(cluster.orders().size(),
                    computeDynamicBatchCap(cluster, trafficIntensity, weather, driver, regime));
            for (int size = dynamicMax; size >= 2; size--) {
                for (DispatchPlan plan : buildBundlePlans(driver, cluster, size, trafficIntensity, weather, regime, ctx)) {
                    classifyOrderPlan(plan, sameMerchantWaves, compactClusterWaves, corridorAlignedWaves, stretchMultiOrder);
                }
            }
            if (countActionablePlans(
                    safeSingles,
                    sameMerchantWaves,
                    compactClusterWaves,
                    corridorAlignedWaves,
                    stretchMultiOrder) >= candidateBuildBudget) {
                break;
            }
        }

        if (targetThreeOrderLaunch && ctx.reachableOrders().size() >= 3) {
            for (List<Order> syntheticWave : buildHardThreeWaveSeeds(ctx)) {
                DispatchPlan plan = buildBundlePlanFromOrders(
                        driver, syntheticWave, trafficIntensity, weather, regime, ctx);
                if (plan != null) {
                    classifyOrderPlan(plan, sameMerchantWaves, compactClusterWaves, corridorAlignedWaves, stretchMultiOrder);
                }
            }
        }

        List<Order> prioritizedReachableOrders = new ArrayList<>(ctx.reachableOrders());
        prioritizedReachableOrders.sort(Comparator
                .comparingDouble((Order order) -> singleOrderPriority(order, driver, ctx))
                .reversed()
                .thenComparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Order::getId));
        int singleLimit = Math.min(
                prioritizedReachableOrders.size(),
                singleOrderCandidateLimit(ctx, weather, trafficIntensity));
        for (int index = 0; index < singleLimit; index++) {
            Order order = prioritizedReachableOrders.get(index);
            DispatchPlan plan = buildSingleOrderPlan(driver, order, trafficIntensity, weather, regime, ctx);
            if (plan != null) {
                safeSingles.add(plan);
            }
        }

        boolean hasActionableOrderPlans = !safeSingles.isEmpty()
                || !sameMerchantWaves.isEmpty()
                || !compactClusterWaves.isEmpty()
                || !corridorAlignedWaves.isEmpty()
                || !stretchMultiOrder.isEmpty();
        boolean hasThreeOrderWave = hasVisibleThreeOrderWave(
                sameMerchantWaves, compactClusterWaves, corridorAlignedWaves, stretchMultiOrder);
        boolean hasEffectiveThreePlusPath = hasThreeOrderWave
                || hasWaveExtensionOpportunity(driver, safeSingles)
                || hasWaveExtensionOpportunity(driver, sameMerchantWaves)
                || hasWaveExtensionOpportunity(driver, compactClusterWaves)
                || hasWaveExtensionOpportunity(driver, corridorAlignedWaves)
                || hasWaveExtensionOpportunity(driver, stretchMultiOrder);
        boolean likelyThirdOrder = shouldWaitForLikelyThirdOrder(ctx);

        boolean allowStrategicHold = !hasActionableOrderPlans && likelyThirdOrder;
        if (!allowStrategicHold && !hasEffectiveThreePlusPath && likelyThirdOrder) {
            allowStrategicHold = true;
        }
        if (isPrePickupAugmentable(driver) && hasEffectiveThreePlusPath) {
            allowStrategicHold = false;
        }

        if (holdPlansEnabled && allowStrategicHold) {
            idlePlans.add(buildHoldPlan(driver, ctx, likelyThirdOrder && !hasEffectiveThreePlusPath));
        }

        boolean allowReposition = !hasActionableOrderPlans
                && ctx.localReachableBacklog() <= 1
                && ctx.localDemandForecast5m() < 0.8
                && ctx.localDemandForecast10m() < 0.9
                && ctx.localWeatherExposure() < 0.70
                && ctx.localCorridorExposure() < 0.75;
        if (regime.isAtLeast(StressRegime.STRESS) || ctx.localReachableBacklog() >= 3) {
            allowReposition = false;
        }
        if (hardThreeOrderPolicy && !hasThreeOrderWave) {
            allowReposition = false;
        }

        if (repositionPlansEnabled && allowReposition) {
            for (EndZoneCandidate zone : ctx.endZoneCandidates()) {
                DispatchPlan reposPlan = buildRepositionPlan(driver, zone);
                if (reposPlan != null) {
                    idlePlans.add(reposPlan);
                }
            }
        }

        List<DispatchPlan> plans = collectWithQuota(
                safeSingles, sameMerchantWaves, compactClusterWaves, corridorAlignedWaves,
                stretchMultiOrder, idlePlans, regime);
        plans.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        int maxPlans = executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8
                ? 22 : 14;
        if (plans.size() > maxPlans) {
            return new ArrayList<>(plans.subList(0, maxPlans));
        }
        return plans;
    }

    private DispatchPlan buildSingleOrderPlan(Driver driver,
                                              Order order,
                                              double trafficIntensity,
                                              WeatherProfile weather,
                                              StressRegime regime,
                                              DriverDecisionContext ctx) {
        Bundle bundle = new Bundle(
                "S-" + order.getId(),
                List.of(order),
                order.getQuotedFee(),
                1
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(trafficIntensity, weather, regime, ctx);
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(driver, bundle, 1);
        if (sequences.isEmpty()) {
            return null;
        }

        DispatchPlan plan = new DispatchPlan(driver, bundle, sequences.get(0));

        double deadheadKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        double maxDeadheadKm = computeMaxPlanDeadheadKm(trafficIntensity, weather, false);
        if (deadheadKm > maxDeadheadKm) {
            return null;
        }
        if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                && order.getPickupDelayHazard() > 0.45
                && deadheadKm > maxDeadheadKm - 0.6) {
            return null;
        }
        if (regime == StressRegime.SEVERE_STRESS
                && (deadheadKm > 2.5 || order.getPickupDelayHazard() > 0.45)) {
            return null;
        }

        double deliveryKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        double feeNorm = order.getQuotedFee() / 35000.0;
        double slackMinutes = ctx.effectiveSlaSlackMinutes();
        double slackBonus = slackMinutes > 0 ? Math.min(0.10, slackMinutes / 40.0) : Math.max(-0.12, slackMinutes / 20.0);
        double regimePenalty = regime == StressRegime.NORMAL ? 0.0
                : regime == StressRegime.STRESS ? deadheadKm / 8.0 * 0.06
                : deadheadKm / 6.0 * 0.12;
        boolean opportunisticExtension = isPrePickupExtensionEligible(driver, List.of(order), ctx, weather, regime);
        int effectiveBundleSize = computeEffectiveBundleSize(driver, List.of(order));
        double onRouteAddOnScore = opportunisticExtension
                ? computeOnRouteAddOnScore(driver, List.of(order), ctx)
                : 0.0;
        SequenceOptimizer.RouteObjectiveMetrics routeMetrics =
                seqOpt.evaluateRouteObjective(driver, plan.getSequence(), List.of(order));
        populateRouteAndLandingMetrics(plan, routeMetrics);
        double prelimScore = feeNorm * 0.42
                - (deadheadKm / 6.0) * 0.34
                + (deliveryKm > 0 ? 1.0 / (1.0 + deadheadKm / deliveryKm) : 0) * 0.18
                - order.getPickupDelayHazard() * 0.06
                + slackBonus
                + (opportunisticExtension ? Math.min(0.12, onRouteAddOnScore * 0.12) : 0.0)
                + (opportunisticExtension && effectiveBundleSize >= 4 ? 0.06 : 0.0)
                + routeMetrics.lastDropLandingScore() * 0.08
                + routeMetrics.deliveryCorridorScore() * 0.04
                + ctx.localPostDropOpportunity() * 0.06
                - Math.min(0.12, routeMetrics.expectedPostCompletionEmptyKm() / 8.0)
                - ctx.localEmptyZoneRisk() * 0.05
                - regimePenalty;

        plan.setTotalScore(Math.max(0.01, prelimScore));
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setTraceId("SINGLE-" + UUID.randomUUID().toString().substring(0, 6));
        applyPolicyMetadata(plan, ctx, regime, false, false);
        return plan;
    }

    private List<DispatchPlan> buildBundlePlans(Driver driver,
                                                OrderCluster cluster,
                                                int bundleSize,
                                                double trafficIntensity,
                                                WeatherProfile weather,
                                                StressRegime regime,
                                                DriverDecisionContext ctx) {
        List<DispatchPlan> result = new ArrayList<>();
        List<List<Order>> candidateSubsets = buildCandidateOrderSubsets(cluster.orders(), bundleSize, driver, ctx);
        Set<String> seenKeys = new LinkedHashSet<>();

        for (List<Order> subset : candidateSubsets) {
            String key = subset.stream().map(Order::getId).sorted().reduce((a, b) -> a + "|" + b).orElse("");
            if (!seenKeys.add(key)) {
                continue;
            }

            DispatchPlan plan = buildBundlePlanFromOrders(driver, subset, trafficIntensity, weather, regime, ctx);
            if (plan != null) {
                result.add(plan);
            }
        }

        return result;
    }

    private List<List<Order>> buildCandidateOrderSubsets(List<Order> candidates,
                                                         int bundleSize,
                                                         Driver driver,
                                                         DriverDecisionContext ctx) {
        List<List<Order>> subsets = new ArrayList<>();
        if (candidates.size() <= bundleSize) {
            subsets.add(List.copyOf(candidates));
            return subsets;
        }

        List<Order> sameMerchantFirst = new ArrayList<>(candidates);
        sameMerchantFirst.sort(Comparator
                .comparing((Order o) -> o.getMerchantId() == null || o.getMerchantId().isBlank())
                .thenComparing((Order o) -> o.getPickupClusterId() == null || o.getPickupClusterId().isBlank())
                .thenComparingDouble(Order::getPickupDelayHazard)
                .thenComparingDouble(Order::getQuotedFee).reversed());
        subsets.add(List.copyOf(sameMerchantFirst.subList(0, Math.min(bundleSize, sameMerchantFirst.size()))));

        List<Order> sameClusterFirst = new ArrayList<>(candidates);
        sameClusterFirst.sort(Comparator
                .comparing((Order o) -> o.getPickupClusterId() == null || o.getPickupClusterId().isBlank())
                .thenComparing((Order o) -> o.getMerchantId() == null || o.getMerchantId().isBlank())
                .thenComparingDouble(Order::getPickupDelayHazard)
                .thenComparingDouble(Order::getQuotedFee).reversed());
        subsets.add(List.copyOf(sameClusterFirst.subList(0, Math.min(bundleSize, sameClusterFirst.size()))));

        List<Order> readySoonFirst = new ArrayList<>(candidates);
        readySoonFirst.sort(Comparator
                .comparingDouble(Order::getPickupDelayHazard)
                .thenComparingDouble(Order::getQuotedFee).reversed());
        subsets.add(List.copyOf(readySoonFirst.subList(0, Math.min(bundleSize, readySoonFirst.size()))));

        List<Order> corridorFirst = new ArrayList<>(candidates);
        corridorFirst.sort(Comparator
                .comparingDouble((Order order) -> candidateSubsetOrderPriority(order, driver, ctx))
                .reversed());
        subsets.add(List.copyOf(corridorFirst.subList(0, Math.min(bundleSize, corridorFirst.size()))));

        if (isPrePickupAugmentable(driver)) {
            List<Order> onRouteFirst = new ArrayList<>(candidates);
            onRouteFirst.sort(Comparator
                    .comparingDouble((Order order) -> onRouteExtensionPriority(order, driver, ctx))
                    .reversed());
            subsets.add(List.copyOf(onRouteFirst.subList(0, Math.min(bundleSize, onRouteFirst.size()))));
        }

        List<Order> comboPool = new ArrayList<>(candidates);
        comboPool.sort(Comparator
                .comparingDouble((Order order) -> candidateSubsetOrderPriority(order, driver, ctx))
                .reversed());
        int poolSize = Math.min(comboPool.size(), comboPoolSize());
        List<List<Order>> rankedCombos = new ArrayList<>();
        buildRankedSubsetCombos(
                comboPool.subList(0, poolSize),
                0,
                bundleSize,
                new ArrayList<>(),
                rankedCombos);
        rankedCombos.sort(Comparator
                .comparingDouble((List<Order> subset) -> scoreCandidateSubset(subset, driver, ctx))
                .reversed());
        rankedCombos.stream()
                .limit(rankedComboLimit(bundleSize))
                .map(List::copyOf)
                .forEach(subsets::add);

        return subsets;
    }

    private int clusterProcessingLimit(DriverDecisionContext ctx,
                                       WeatherProfile weather,
                                       double trafficIntensity) {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return Integer.MAX_VALUE;
        }
        int limit = switch (weather) {
            case CLEAR -> 3;
            case LIGHT_RAIN -> 3;
            case HEAVY_RAIN -> 4;
            case STORM -> 5;
        };
        if (ctx != null && (ctx.localReachableBacklog() >= 5 || ctx.reachableOrders().size() >= 6)) {
            limit += 1;
        }
        if (trafficIntensity >= 0.55) {
            limit += 1;
        }
        if (ctx != null && requiresHardThreeOrderLaunch(executionProfile, ctx)) {
            limit += 1;
        }
        return Math.max(2, limit);
    }

    private int driverCandidateBuildBudget(DriverDecisionContext ctx,
                                           WeatherProfile weather,
                                           double trafficIntensity) {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return Integer.MAX_VALUE;
        }
        int budget = switch (weather) {
            case CLEAR -> 7;
            case LIGHT_RAIN -> 8;
            case HEAVY_RAIN -> 9;
            case STORM -> 10;
        };
        if (ctx != null && ctx.localReachableBacklog() >= 5) {
            budget += 1;
        }
        if (trafficIntensity >= 0.55) {
            budget += 1;
        }
        return Math.max(6, budget);
    }

    private int singleOrderCandidateLimit(DriverDecisionContext ctx,
                                          WeatherProfile weather,
                                          double trafficIntensity) {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return Integer.MAX_VALUE;
        }
        int limit = switch (weather) {
            case CLEAR -> 5;
            case LIGHT_RAIN -> 6;
            case HEAVY_RAIN -> 7;
            case STORM -> 8;
        };
        if (ctx != null && ctx.localReachableBacklog() >= 5) {
            limit += 1;
        }
        if (trafficIntensity >= 0.55) {
            limit += 1;
        }
        return Math.max(4, limit);
    }

    private int comboPoolSize() {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return 8;
        }
        return smallBatchOnly ? 4 : 5;
    }

    private int rankedComboLimit(int bundleSize) {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return 10;
        }
        if (smallBatchOnly) {
            return 2;
        }
        return bundleSize >= 3 ? 4 : 3;
    }

    private int syntheticWavePoolSize() {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return 7;
        }
        return 5;
    }

    private int syntheticWaveComboLimit(int bundleSize) {
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            return 8;
        }
        return bundleSize >= 4 ? 2 : 4;
    }

    private int countActionablePlans(List<DispatchPlan> safeSingles,
                                     List<DispatchPlan> sameMerchantWaves,
                                     List<DispatchPlan> compactClusterWaves,
                                     List<DispatchPlan> corridorAlignedWaves,
                                     List<DispatchPlan> stretchMultiOrder) {
        return safeSingles.size()
                + sameMerchantWaves.size()
                + compactClusterWaves.size()
                + corridorAlignedWaves.size()
                + stretchMultiOrder.size();
    }

    private double singleOrderPriority(Order order,
                                       Driver driver,
                                       DriverDecisionContext ctx) {
        double urgency = clamp01(order.getPredictedLateRisk()) * 0.18
                + clamp01(order.getPickupDelayHazard()) * 0.16
                + clamp01(order.getCancellationRisk()) * 0.06;
        return candidateSubsetOrderPriority(order, driver, ctx) + urgency;
    }

    private double candidateSubsetOrderPriority(Order order,
                                                Driver driver,
                                                DriverDecisionContext ctx) {
        double distanceKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        double merchantBonus = order.getMerchantId() != null && !order.getMerchantId().isBlank() ? 0.22 : 0.0;
        double clusterBonus = order.getPickupClusterId() != null && !order.getPickupClusterId().isBlank() ? 0.18 : 0.0;
        double readiness = Math.max(0.0, 1.0 - order.getPickupDelayHazard()) * 0.20;
        double corridor = scoreDropCorridorAffinity(order, ctx) * 0.22;
        double landing = scoreLandingAffinity(order, ctx) * 0.12;
        double feeSignal = Math.min(0.18, order.getQuotedFee() / 120000.0);
        return merchantBonus
                + clusterBonus
                + readiness
                + corridor
                + landing
                + feeSignal
                - distanceKm * 0.10;
    }

    private double onRouteExtensionPriority(Order order,
                                            Driver driver,
                                            DriverDecisionContext ctx) {
        GeoPoint anchor = resolvePrePickupRouteAnchor(driver);
        if (anchor == null) {
            anchor = driver.getCurrentLocation();
        }
        double anchorOffsetKm = estimateDetourKm(driver.getCurrentLocation(), anchor, order.getPickupPoint());
        double onRouteSignal = 1.0 / (1.0 + anchorOffsetKm / 0.8);
        return candidateSubsetOrderPriority(order, driver, ctx)
                + onRouteSignal * 0.35;
    }

    private void buildRankedSubsetCombos(List<Order> pool,
                                         int index,
                                         int targetSize,
                                         List<Order> current,
                                         List<List<Order>> output) {
        if (current.size() == targetSize) {
            output.add(List.copyOf(current));
            return;
        }
        if (index >= pool.size()) {
            return;
        }
        int remainingNeeded = targetSize - current.size();
        if (pool.size() - index < remainingNeeded) {
            return;
        }

        current.add(pool.get(index));
        buildRankedSubsetCombos(pool, index + 1, targetSize, current, output);
        current.remove(current.size() - 1);
        buildRankedSubsetCombos(pool, index + 1, targetSize, current, output);
    }

    private double scoreCandidateSubset(List<Order> subset,
                                        Driver driver,
                                        DriverDecisionContext ctx) {
        if (subset.isEmpty()) {
            return 0.0;
        }
        double orderScore = subset.stream()
                .mapToDouble(order -> candidateSubsetOrderPriority(order, driver, ctx))
                .average()
                .orElse(0.0);
        double compactness = 1.0 / (1.0 + Math.max(0.0, clusterSpreadKm(subset) - 0.25));
        double sameMerchantBonus = isSameMerchant(subset) ? 0.18 : 0.0;
        double sameClusterBonus = isSamePickupCluster(subset) ? 0.14 : 0.0;
        return orderScore
                + compactness * 0.30
                + sameMerchantBonus
                + sameClusterBonus;
    }

    private double scoreDropCorridorAffinity(Order order, DriverDecisionContext ctx) {
        if (ctx == null || ctx.dropCorridorCandidates().isEmpty()) {
            return 0.35;
        }
        double best = 0.0;
        for (DriverDecisionContext.DropCorridorCandidate candidate : ctx.dropCorridorCandidates()) {
            double distKm = order.getDropoffPoint().distanceTo(candidate.anchorPoint()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.2);
            best = Math.max(best,
                    candidate.corridorScore() * 0.65
                            + proximity * 0.20
                            + Math.min(1.0, candidate.demandSignal() / 2.5) * 0.15);
        }
        return Math.max(0.0, Math.min(1.0, best));
    }

    private double scoreLandingAffinity(Order order, DriverDecisionContext ctx) {
        if (ctx == null || ctx.endZoneCandidates().isEmpty()) {
            return 0.35;
        }
        double best = 0.0;
        for (DriverDecisionContext.EndZoneCandidate candidate : ctx.endZoneCandidates()) {
            double distKm = order.getDropoffPoint().distanceTo(candidate.position()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.0);
            best = Math.max(best,
                    candidate.attractionScore() * 0.60
                            + proximity * 0.25
                            + (1.0 - candidate.corridorExposure()) * 0.10
                            + (1.0 - candidate.weatherExposure()) * 0.05);
        }
        return Math.max(0.0, Math.min(1.0, best));
    }

    private boolean isPrePickupExtensionEligible(Driver driver,
                                                 List<Order> orders,
                                                 DriverDecisionContext ctx,
                                                 WeatherProfile weather,
                                                 StressRegime regime) {
        if (!isPrePickupAugmentable(driver) || orders == null || orders.isEmpty()) {
            return false;
        }
        if (regime == StressRegime.SEVERE_STRESS
                || weather == WeatherProfile.HEAVY_RAIN
                || weather == WeatherProfile.STORM) {
            return false;
        }
        return computeEffectiveBundleSize(driver, orders) <= MAINLINE_MAX_TOTAL_BUNDLE_SIZE
                && computeOnRouteAddOnScore(driver, orders, ctx) >= 0.45;
    }

    private int computeEffectiveBundleSize(Driver driver, List<Order> orders) {
        Set<String> uniqueOrderIds = new LinkedHashSet<>();
        if (driver != null) {
            uniqueOrderIds.addAll(driver.getActiveOrderIds());
        }
        if (orders != null) {
            for (Order order : orders) {
                if (order != null) {
                    uniqueOrderIds.add(order.getId());
                }
            }
        }
        return uniqueOrderIds.size();
    }

    private double computeOnRouteAddOnScore(Driver driver,
                                            List<Order> orders,
                                            DriverDecisionContext ctx) {
        if (driver == null || orders == null || orders.isEmpty()) {
            return 0.0;
        }
        GeoPoint routeAnchor = resolvePrePickupRouteAnchor(driver);
        final GeoPoint anchor = routeAnchor != null ? routeAnchor : driver.getCurrentLocation();
        double avgPickupOffsetKm = orders.stream()
                .mapToDouble(order -> estimateDetourKm(driver.getCurrentLocation(), anchor, order.getPickupPoint()))
                .average()
                .orElse(1.5);
        double routeFit = 1.0 / (1.0 + avgPickupOffsetKm / 0.7);
        double corridor = orders.stream()
                .mapToDouble(order -> scoreDropCorridorAffinity(order, ctx))
                .average()
                .orElse(0.0);
        double landing = orders.stream()
                .mapToDouble(order -> scoreLandingAffinity(order, ctx))
                .average()
                .orElse(0.0);
        return Math.max(0.0, Math.min(1.0,
                routeFit * 0.55 + corridor * 0.25 + landing * 0.20));
    }

    private GeoPoint resolvePrePickupRouteAnchor(Driver driver) {
        if (driver == null) {
            return null;
        }
        if (driver.getPendingTargetLocation() != null) {
            return driver.getPendingTargetLocation();
        }
        if (driver.getTargetLocation() != null) {
            return driver.getTargetLocation();
        }
        List<Stop> assignedSequence = driver.getAssignedSequence();
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(driver.getCurrentSequenceIndex(), assignedSequence.size() - 1));
        return assignedSequence.get(index).location();
    }

    private double estimateDetourKm(GeoPoint origin, GeoPoint anchor, GeoPoint pickup) {
        if (origin == null || anchor == null || pickup == null) {
            return Double.MAX_VALUE;
        }
        double directMeters = origin.distanceTo(anchor);
        if (directMeters <= 0.0) {
            return origin.distanceTo(pickup) / 1000.0;
        }
        double viaMeters = origin.distanceTo(pickup) + pickup.distanceTo(anchor);
        return Math.max(0.0, (viaMeters - directMeters) / 1000.0);
    }

    private boolean isTrafficOnlyStressWindow(DriverDecisionContext ctx,
                                              WeatherProfile weather,
                                              StressRegime regime) {
        return ctx != null
                && regime == StressRegime.STRESS
                && !ctx.harshWeatherStress()
                && (weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)
                && ctx.thirdOrderFeasibilityScore() >= 0.40
                && ctx.threeOrderSlackBuffer() >= 1.2;
    }

    private List<List<Order>> buildHardThreeWaveSeeds(DriverDecisionContext ctx) {
        List<List<Order>> seeds = new ArrayList<>();
        List<Order> reachable = new ArrayList<>(ctx.reachableOrders());
        if (reachable.size() < 3) {
            return seeds;
        }

        reachable.sort(Comparator
                .comparingDouble((Order order) -> ctx.driver().getCurrentLocation().distanceTo(order.getPickupPoint()))
                .thenComparingDouble(Order::getPickupDelayHazard));
        seeds.add(List.copyOf(reachable.subList(0, Math.min(3, reachable.size()))));

        List<Order> corridorFirst = new ArrayList<>(ctx.reachableOrders());
        corridorFirst.sort(Comparator
                .comparing((Order order) -> order.getMerchantId() == null || order.getMerchantId().isBlank())
                .thenComparing((Order order) -> order.getPickupClusterId() == null || order.getPickupClusterId().isBlank())
                .thenComparingDouble(Order::getPickupDelayHazard)
                .thenComparingDouble(order -> ctx.driver().getCurrentLocation().distanceTo(order.getPickupPoint())));
        seeds.add(List.copyOf(corridorFirst.subList(0, Math.min(3, corridorFirst.size()))));

        List<Order> readinessFirst = new ArrayList<>(ctx.reachableOrders());
        readinessFirst.sort(Comparator
                .comparingDouble(Order::getPickupDelayHazard)
                .thenComparingDouble(order -> ctx.driver().getCurrentLocation().distanceTo(order.getPickupPoint())));
        seeds.add(List.copyOf(readinessFirst.subList(0, Math.min(3, readinessFirst.size()))));

        List<Order> wavePool = new ArrayList<>(ctx.reachableOrders());
        wavePool.sort(Comparator
                .comparingDouble((Order order) -> syntheticWaveOrderPriority(order, ctx))
                .reversed());
        int poolSize = Math.min(syntheticWavePoolSize(), wavePool.size());
        List<List<Order>> rankedCombos = new ArrayList<>();
        for (int i = 0; i < poolSize - 2; i++) {
            for (int j = i + 1; j < poolSize - 1; j++) {
                for (int k = j + 1; k < poolSize; k++) {
                    rankedCombos.add(List.of(wavePool.get(i), wavePool.get(j), wavePool.get(k)));
                }
            }
        }
        rankedCombos.sort(Comparator
                .comparingDouble((List<Order> seed) -> scoreSyntheticWaveSeed(seed, ctx))
                .reversed());
        seeds.addAll(rankedCombos.stream()
                .limit(syntheticWaveComboLimit(3))
                .toList());

        boolean cleanLaunchWindow = !ctx.harshWeatherStress()
                && ctx.thirdOrderFeasibilityScore() >= 0.45
                && ctx.threeOrderSlackBuffer() >= 1.5;
        if (cleanLaunchWindow && reachable.size() >= 4) {
            List<List<Order>> rankedFourCombos = new ArrayList<>();
            int fourPoolSize = Math.min(syntheticWavePoolSize(), wavePool.size());
            for (int i = 0; i < fourPoolSize - 3; i++) {
                for (int j = i + 1; j < fourPoolSize - 2; j++) {
                    for (int k = j + 1; k < fourPoolSize - 1; k++) {
                        for (int l = k + 1; l < fourPoolSize; l++) {
                            rankedFourCombos.add(List.of(
                                    wavePool.get(i),
                                    wavePool.get(j),
                                    wavePool.get(k),
                                    wavePool.get(l)));
                        }
                    }
                }
            }
            rankedFourCombos.sort(Comparator
                    .comparingDouble((List<Order> seed) -> scoreSyntheticWaveSeed(seed, ctx))
                    .reversed());
            rankedFourCombos.stream()
                    .limit(syntheticWaveComboLimit(4))
                    .forEach(seeds::add);
        }

        List<List<Order>> uniqueSeeds = new ArrayList<>();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (List<Order> seed : seeds) {
            if (seed.size() < 3) {
                continue;
            }
            String key = orderKey(seed);
            if (seenKeys.add(key)) {
                uniqueSeeds.add(List.copyOf(seed));
            }
        }
        return uniqueSeeds;
    }

    private DispatchPlan buildBundlePlanFromOrders(Driver driver,
                                                   List<Order> orders,
                                                   double trafficIntensity,
                                                   WeatherProfile weather,
                                                   StressRegime regime,
                                                   DriverDecisionContext ctx) {
        boolean trafficOnlyStressWindow = isTrafficOnlyStressWindow(ctx, weather, regime);
        int effectiveBundleSize = computeEffectiveBundleSize(driver, orders);
        boolean opportunisticExtension = isPrePickupExtensionEligible(driver, orders, ctx, weather, regime);
        double onRouteAddOnScore = opportunisticExtension
                ? computeOnRouteAddOnScore(driver, orders, ctx)
                : 0.0;
        double totalFee = orders.stream().mapToDouble(Order::getQuotedFee).sum();

        Bundle bundle = new Bundle(
                "B-" + UUID.randomUUID().toString().substring(0, 8),
                List.copyOf(orders),
                totalFee,
                orders.size()
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(trafficIntensity, weather, regime, ctx);
        int maxSequences = executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8
                ? 12 : effectiveBundleSize >= 4 ? 5 : orders.size() >= 3 ? 4 : 3;
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(
                driver, bundle, maxSequences);
        if (sequences.isEmpty()) {
            return null;
        }

        List<Stop> bestSeq = selectBestSequenceByRouteIntelligence(driver, sequences, orders, seqOpt);
        DispatchPlan plan = new DispatchPlan(driver, bundle, bestSeq);

        GeoPoint firstPickup = bestSeq.isEmpty() ? orders.get(0).getPickupPoint() : bestSeq.get(0).location();
        double deadheadKm = driver.getCurrentLocation().distanceTo(firstPickup) / 1000.0;
        boolean sameMerchant = isSameMerchant(orders);
        boolean samePickupCluster = isSamePickupCluster(orders);
        double maxDeadheadKm = computeMaxPlanDeadheadKm(
                trafficIntensity, weather, sameMerchant || samePickupCluster);
        if (deadheadKm > maxDeadheadKm) {
            return null;
        }

        double standaloneDist = orders.stream()
                .mapToDouble(o -> o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double bundleRouteDist = computeRouteDistance(driver.getCurrentLocation(), bestSeq);
        if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                && !(sameMerchant || samePickupCluster)
                && bundleRouteDist > standaloneDist * 1.35) {
            return null;
        }
        if (regime == StressRegime.SEVERE_STRESS && orders.size() > 2) {
            return null;
        }
        if (regime == StressRegime.STRESS
                && orders.size() > 3
                && !(sameMerchant || samePickupCluster)) {
            return null;
        }

        double compactness = 1.0 / (1.0 + Math.max(0.0, clusterSpreadKm(orders) - 0.25));
        double readinessBonus = Math.max(0.0, 1.0 - averageDelayHazard(orders));
        double readinessDeltaMinutes = computeReadinessDeltaMinutes(orders);
        boolean targetThreeOrderLaunch = prefersThreeOrderLaunch(executionProfile, ctx);
        boolean hardThreeOrderPolicy = requiresHardThreeOrderLaunch(executionProfile, ctx);
        int scoredBundleSize = opportunisticExtension
                ? Math.max(orders.size(), effectiveBundleSize)
                : orders.size();
        double sizeBonus;
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            sizeBonus = scoredBundleSize / 8.0;
        } else if (scoredBundleSize >= 5) {
            sizeBonus = 1.08;
        } else if (scoredBundleSize >= 4) {
            sizeBonus = 1.00;
        } else if (scoredBundleSize == 3) {
            sizeBonus = 0.82;
        } else {
            sizeBonus = 0.34;
        }
        double efficiency = standaloneDist > 0
                ? standaloneDist / Math.max(0.1, bundleRouteDist)
                : 0.5;
        boolean compactReadyAligned = compactness >= 0.72
                && readinessBonus >= 0.72
                && readinessDeltaMinutes <= 4.0;
        SequenceOptimizer.RouteObjectiveMetrics routeMetrics =
                seqOpt.evaluateRouteObjective(driver, bestSeq, orders);
        populateRouteAndLandingMetrics(plan, routeMetrics);
        double highValueWaveBonus = executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                && !ctx.harshWeatherStress()
                && (orders.size() >= 3 || (opportunisticExtension && effectiveBundleSize >= 3))
                && compactReadyAligned
                && routeMetrics.deliveryCorridorScore() >= 0.38
                && routeMetrics.lastDropLandingScore() >= 0.24
                && routeMetrics.expectedPostCompletionEmptyKm() <= 2.0
                && deadheadKm <= 2.9
                ? (hardThreeOrderPolicy ? 0.14 : targetThreeOrderLaunch ? 0.11 : 0.09) : 0.0;
        double trafficStressRecoveryBonus = trafficOnlyStressWindow
                && orders.size() == 3
                && compactness >= 0.60
                && readinessBonus >= 0.60
                && routeMetrics.deliveryCorridorScore() >= 0.34
                ? 0.08 : 0.0;
        double landingThreshold = orders.size() >= 3 && targetThreeOrderLaunch ? (hardThreeOrderPolicy ? 0.34 : 0.30)
                : (trafficOnlyStressWindow && orders.size() >= 3 ? 0.28
                : (regime == StressRegime.NORMAL ? 0.48 : 0.42));
        double corridorThreshold = orders.size() >= 3 && targetThreeOrderLaunch ? (hardThreeOrderPolicy ? 0.44 : 0.40)
                : (trafficOnlyStressWindow && orders.size() >= 3 ? 0.36
                : (orders.size() >= 3 ? 0.52 : 0.45));
        double zigZagThreshold = orders.size() >= 3 && targetThreeOrderLaunch ? 0.58
                : (trafficOnlyStressWindow && orders.size() >= 3 ? 0.64 : 0.48);
        boolean landingAcceptable = routeMetrics.lastDropLandingScore() >= landingThreshold;
        boolean corridorAcceptable = routeMetrics.deliveryCorridorScore() >= corridorThreshold
                && routeMetrics.deliveryZigZagPenalty() <= zigZagThreshold;
        boolean relaxedTrafficStressWave = trafficOnlyStressWindow
                && orders.size() == 3
                && compactness >= 0.60
                && readinessBonus >= 0.60
                && readinessDeltaMinutes <= 5.5
                && routeMetrics.deliveryCorridorScore() >= 0.34
                && routeMetrics.deliveryZigZagPenalty() <= 0.66
                && routeMetrics.lastDropLandingScore() >= 0.18;
        boolean extensionCorridorSafe = !opportunisticExtension
                || (onRouteAddOnScore >= 0.46
                && routeMetrics.deliveryCorridorScore() >= 0.32
                && routeMetrics.deliveryZigZagPenalty() <= 0.68);

        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                && orders.size() >= 4) {
            boolean weatherAllowed = weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN;
            if (!weatherAllowed
                    || !((sameMerchant || samePickupCluster)
                    || (trafficOnlyStressWindow
                    && compactness >= 0.80
                    && routeMetrics.deliveryCorridorScore() >= 0.62))
                    || !compactReadyAligned
                    || deadheadKm > (trafficOnlyStressWindow ? 2.4 : 2.0)
                    || averageDelayHazard(orders) > (trafficOnlyStressWindow ? 0.22 : 0.18)
                    || !landingAcceptable
                    || !corridorAcceptable) {
                return null;
            }
        }
        if (regime.isAtLeast(StressRegime.STRESS)
                && (!compactReadyAligned || !corridorAcceptable)
                && orders.size() >= 3
                && !(targetThreeOrderLaunch
                && orders.size() == 3
                && ctx.thirdOrderFeasibilityScore() >= 0.50
                && ctx.threeOrderSlackBuffer() >= 1.8
                && routeMetrics.deliveryCorridorScore() >= 0.38)
                && !relaxedTrafficStressWave) {
            return null;
        }
        if (orders.size() >= 3
                && !landingAcceptable
                && executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                && !(targetThreeOrderLaunch
                && orders.size() == 3
                && ctx.thirdOrderFeasibilityScore() >= 0.50
                && ctx.threeOrderSlackBuffer() >= 1.8
                && routeMetrics.lastDropLandingScore() >= 0.24
                && routeMetrics.deliveryCorridorScore() >= 0.38)
                && !relaxedTrafficStressWave) {
            return null;
        }
        if (!extensionCorridorSafe) {
            return null;
        }

        double prelimScore = (totalFee / (35000.0 * Math.max(1, orders.size()))) * 0.25
                + efficiency * 0.28
                + compactness * 0.15
                + readinessBonus * 0.10
                + sizeBonus * 0.17
                + highValueWaveBonus
                + trafficStressRecoveryBonus
                + routeMetrics.deliveryCorridorScore() * 0.10
                + routeMetrics.lastDropLandingScore() * 0.10
                + routeMetrics.remainingDropProximityScore() * 0.08
                + (sameMerchant ? 0.08 : 0.0)
                + (samePickupCluster ? 0.06 : 0.0)
                + (opportunisticExtension ? onRouteAddOnScore * 0.14 : 0.0)
                + ctx.localPostDropOpportunity() * 0.05
                - routeMetrics.deliveryZigZagPenalty() * 0.10
                - Math.min(0.15, routeMetrics.expectedPostCompletionEmptyKm() / 8.0)
                - ctx.localEmptyZoneRisk() * 0.04
                - (deadheadKm / 6.0) * (opportunisticExtension ? 0.12 : 0.18);

        plan.setTotalScore(Math.max(0.02, prelimScore));
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setBundleEfficiency(efficiency);
        plan.setTraceId("BUNDLE-" + UUID.randomUUID().toString().substring(0, 6));
        boolean cleanWaveEligible = (orders.size() >= 3 || (opportunisticExtension && effectiveBundleSize >= 3))
                && (compactReadyAligned || relaxedTrafficStressWave)
                && routeMetrics.deliveryCorridorScore() >= (targetThreeOrderLaunch || opportunisticExtension ? 0.34 : corridorThreshold)
                && routeMetrics.lastDropLandingScore() >= (targetThreeOrderLaunch || opportunisticExtension ? 0.20 : landingThreshold)
                && routeMetrics.deliveryZigZagPenalty() <= (targetThreeOrderLaunch || opportunisticExtension ? 0.64 : 0.40);
        applyPolicyMetadata(plan, ctx, regime, cleanWaveEligible, false);
        return plan;
    }

    private DispatchPlan buildHoldPlan(Driver driver,
                                       DriverDecisionContext ctx,
                                       boolean waitingForThirdOrder) {
        Bundle holdBundle = new Bundle("HOLD", List.of(), 0, 0);
        DispatchPlan plan = new DispatchPlan(driver, holdBundle, List.of());

        double holdScore = HOLD_PLAN_BASE_SCORE
                + ctx.localDemandIntensity() * 0.04
                + ctx.localDemandForecast5m() * 0.05
                + ctx.localDemandForecast10m() * 0.06
                + ctx.localDemandForecast15m() * 0.04
                + ctx.localDemandForecast30m() * 0.03
                + ctx.localPostDropOpportunity() * 0.05
                + ctx.localSpikeProbability() * 0.06
                + ctx.localShortagePressure() * 0.03
                + Math.min(3, ctx.nearReadyOrders()) * 0.025
                - ctx.localDriverDensity() / 20.0 * 0.02
                - ctx.localEmptyZoneRisk() * 0.03
                - ctx.localWeatherExposure() * 0.05
                - ctx.localCorridorExposure() * 0.04;
        if (waitingForThirdOrder) {
            holdScore += 0.06
                    + Math.min(0.08, ctx.waveAssemblyPressure() * 0.08)
                    + Math.min(0.05, ctx.thirdOrderFeasibilityScore() * 0.05);
        }

        double waveReadiness = Math.max(0.0, Math.min(1.0,
                ctx.thirdOrderFeasibilityScore() * 0.45
                        + ctx.waveAssemblyPressure() * 0.35
                        + Math.min(1.0, ctx.nearReadyOrders() / 3.0) * 0.20
                        - ctx.localCorridorExposure() * 0.10));

        plan.setTotalScore(Math.max(0.01, Math.min(0.12, holdScore)));
        plan.setConfidence(0.3);
        plan.setWaveReadinessScore(waveReadiness);
        plan.setMarginalDeadheadPerAddedOrder(0.0);
        plan.setPickupSpreadKm(0.0);
        plan.setTraceId(waitingForThirdOrder
                ? "HOLD-THIRD-" + UUID.randomUUID().toString().substring(0, 6)
                : "HOLD");
        applyPolicyMetadata(plan, ctx, ctx.stressRegime(), false, waitingForThirdOrder);
        return plan;
    }

    private DispatchPlan buildRepositionPlan(Driver driver, EndZoneCandidate zone) {
        if (zone.distanceKm() < 0.3 || zone.distanceKm() > 2.5) {
            return null;
        }
        if (zone.weatherExposure() > 0.75 || zone.corridorExposure() > 0.80) {
            return null;
        }

        Bundle reposBundle = new Bundle(
                "REPOS-" + UUID.randomUUID().toString().substring(0, 6),
                List.of(), 0, 0);

        Stop target = new Stop("REPOS", zone.position(), StopType.PICKUP,
                zone.distanceKm() / 15.0 * 60.0);
        DispatchPlan plan = new DispatchPlan(driver, reposBundle, List.of(target));

        double score = REPOSITION_BASE_SCORE
                + zone.attractionScore() * 0.08
                + zone.postDropOpportunity() * 0.08
                - zone.distanceKm() / 5.0 * 0.04
                - zone.emptyZoneRisk() * 0.04
                - zone.weatherExposure() * 0.04
                - zone.corridorExposure() * 0.05;

        plan.setTotalScore(Math.max(0.01, score));
        plan.setConfidence(0.2);
        plan.setEndZoneOpportunity(zone.attractionScore());
        plan.setTraceId("REPOS");
        return plan;
    }

    private SequenceOptimizer createSequenceOptimizer(double trafficIntensity,
                                                      WeatherProfile weather,
                                                      StressRegime regime,
                                                      DriverDecisionContext ctx) {
        return new SequenceOptimizer(
                trafficIntensity,
                weather,
                executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8,
                regime,
                ctx);
    }

    private List<Stop> selectBestSequenceByRouteIntelligence(Driver driver,
                                                             List<List<Stop>> sequences,
                                                             List<Order> orders,
                                                             SequenceOptimizer seqOpt) {
        if (sequences == null || sequences.isEmpty()) {
            return List.of();
        }
        if (sequences.size() == 1) {
            return sequences.get(0);
        }

        Map<String, Order> ordersById = new HashMap<>(orders.size());
        for (Order order : orders) {
            ordersById.put(order.getId(), order);
        }

        double bestScore = Double.NEGATIVE_INFINITY;
        List<Stop> best = sequences.get(0);
        int maxCandidates = Math.min(
                executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8 ? 6 : 4,
                sequences.size());
        for (int i = 0; i < maxCandidates; i++) {
            List<Stop> candidate = sequences.get(i);
            SequenceOptimizer.RouteObjectiveMetrics metrics =
                    seqOpt.evaluateRouteObjective(driver, candidate, orders);
            double routeKm = computeRouteDistance(driver.getCurrentLocation(), candidate);
            double firstDropUrgency = firstDropUrgency(candidate, ordersById);
            double frontloadScore = urgencyFrontloadScore(candidate, ordersById);
            double score = metrics.deliveryCorridorScore() * 0.26
                    + metrics.lastDropLandingScore() * 0.22
                    + metrics.remainingDropProximityScore() * 0.16
                    + (1.0 - metrics.deliveryZigZagPenalty()) * 0.16
                    + firstDropUrgency * 0.10
                    + frontloadScore * 0.10
                    - Math.min(0.22, metrics.expectedPostCompletionEmptyKm() / 10.0)
                    - Math.min(0.10, metrics.expectedNextOrderIdleMinutes() / 10.0)
                    - Math.min(0.18, routeKm / 24.0);
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
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

    private double firstDropUrgency(List<Stop> sequence, Map<String, Order> ordersById) {
        if (sequence == null || ordersById == null || ordersById.isEmpty()) {
            return 0.0;
        }
        for (Stop stop : sequence) {
            if (stop.type() != StopType.DROPOFF) {
                continue;
            }
            Order order = ordersById.get(stop.orderId());
            if (order != null) {
                return computeOrderUrgency(order);
            }
        }
        return 0.0;
    }

    private double urgencyFrontloadScore(List<Stop> sequence, Map<String, Order> ordersById) {
        if (sequence == null || ordersById == null || ordersById.size() <= 1) {
            return 0.0;
        }
        List<Order> dropOrder = new ArrayList<>();
        for (Stop stop : sequence) {
            if (stop.type() != StopType.DROPOFF) {
                continue;
            }
            Order order = ordersById.get(stop.orderId());
            if (order != null) {
                dropOrder.add(order);
            }
        }
        if (dropOrder.size() <= 1) {
            return 0.0;
        }

        double score = 0.0;
        int compared = 0;
        for (int i = 0; i < dropOrder.size() - 1; i++) {
            double left = computeOrderUrgency(dropOrder.get(i));
            for (int j = i + 1; j < dropOrder.size(); j++) {
                double right = computeOrderUrgency(dropOrder.get(j));
                score += left >= right ? 1.0 : Math.max(0.0, 1.0 - (right - left));
                compared++;
            }
        }
        return compared == 0 ? 0.0 : score / compared;
    }

    private double computeOrderUrgency(Order order) {
        if (order == null) {
            return 0.0;
        }
        double etaTightness = clamp01(1.0 - order.getPromisedEtaMinutes() / 90.0);
        double delayHazard = clamp01(order.getPickupDelayHazard());
        double cancelRisk = clamp01(order.getCancellationRisk());
        double priority = clamp01(order.getPriority() / 3.0);
        return etaTightness * 0.44
                + delayHazard * 0.28
                + cancelRisk * 0.18
                + priority * 0.10;
    }

    private int computeDynamicBatchCap(OrderCluster cluster,
                                       double trafficSeverity,
                                       WeatherProfile weather,
                                       Driver driver,
                                       StressRegime regime) {
        boolean sameMerchant = isSameMerchant(cluster.orders());
        boolean samePickupCluster = isSamePickupCluster(cluster.orders());
        double avgDelayHazard = averageDelayHazard(cluster.orders());
        boolean compactReadyCluster = cluster.spreadMeters() <= 320.0
                && avgDelayHazard <= 0.22;

        int cap;
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            cap = 4;
            if (sameMerchant || samePickupCluster) {
                cap += 2;
            }
            if (cluster.spreadMeters() < 260.0) {
                cap += 1;
            }
            if (avgDelayHazard < 0.22) {
                cap += 1;
            }
            if (trafficSeverity > 0.55) {
                cap -= 1;
            }
            if (weather == WeatherProfile.HEAVY_RAIN) {
                cap = Math.min(cap, sameMerchant ? 3 : 2);
            } else if (weather == WeatherProfile.STORM) {
                cap = Math.min(cap, 2);
            }
            if (!(sameMerchant || samePickupCluster)) {
                cap = Math.min(cap, 5);
            }
            cap = Math.min(cap, 8);
        } else {
            cap = 3;
            if (sameMerchant || samePickupCluster) {
                cap += 1;
            }
            if (compactReadyCluster
                    && weather != WeatherProfile.HEAVY_RAIN
                    && weather != WeatherProfile.STORM
                    && trafficSeverity <= 0.58) {
                cap += 1;
            }
            if (trafficSeverity > 0.6) {
                cap -= 1;
            }
            if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
                cap -= 1;
            }
            if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                    && cluster.spreadMeters() > 650.0) {
                cap -= 1;
            }
            if (!sameMerchant && !samePickupCluster && weather == WeatherProfile.HEAVY_RAIN) {
                cap = Math.min(cap, 2);
            }
            if (!sameMerchant && !samePickupCluster && weather == WeatherProfile.STORM) {
                cap = 1;
            }
            if (avgDelayHazard > 0.5) {
                cap -= 1;
            }
            cap = Math.min(cap, 4);
        }

        if (driver.isPrePickupAugmentable()) {
            int remainingCapacity = MAINLINE_MAX_TOTAL_BUNDLE_SIZE - driver.getCurrentOrderCount();
            cap = Math.min(cap, remainingCapacity);
        } else if (driver.getActiveOrderIds().size() >= 2) {
            cap -= 1;
        }
        if (smallBatchOnly) {
            cap = Math.min(cap, 2);
        }
        if (regime == StressRegime.STRESS) {
            boolean cleanTrafficStress = weather != WeatherProfile.HEAVY_RAIN
                    && weather != WeatherProfile.STORM;
            if (cleanTrafficStress && (sameMerchant || samePickupCluster || compactReadyCluster)) {
                cap = Math.min(cap, sameMerchant || samePickupCluster ? 4 : 3);
            } else {
                cap = Math.min(cap, sameMerchant || samePickupCluster || compactReadyCluster ? 3 : 2);
            }
        } else if (regime == StressRegime.SEVERE_STRESS) {
            cap = Math.min(cap, sameMerchant || samePickupCluster ? 2 : 1);
        }

        return Math.max(0, cap);
    }

    private double computeMaxPlanDeadheadKm(double trafficIntensity,
                                            WeatherProfile weather,
                                            boolean clusterPreferred) {
        double maxDeadhead = switch (weather) {
            case CLEAR -> 5.8 - trafficIntensity * 1.1;
            case LIGHT_RAIN -> 4.8 - trafficIntensity * 1.0;
            case HEAVY_RAIN -> 3.0 - trafficIntensity * 0.8;
            case STORM -> 2.2 - trafficIntensity * 0.5;
        };
        if (clusterPreferred) {
            maxDeadhead += 0.4;
        }
        if (executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8) {
            maxDeadhead += 0.4;
        }
        return Math.max(1.8, maxDeadhead);
    }

    private double computeClusterPriority(OrderCluster cluster,
                                          WeatherProfile weather,
                                          double trafficIntensity) {
        boolean sameMerchant = isSameMerchant(cluster.orders());
        boolean samePickupCluster = isSamePickupCluster(cluster.orders());
        double spreadScore = 1.0 / (1.0 + cluster.spreadMeters() / 350.0);
        double readinessScore = Math.max(0.0, 1.0 - averageDelayHazard(cluster.orders()));
        double weatherPenalty = (weather == WeatherProfile.HEAVY_RAIN ? 0.10 : 0.0)
                + (weather == WeatherProfile.STORM ? 0.18 : 0.0);

        return cluster.totalFee() / Math.max(1.0, cluster.orders().size()) / 40000.0
                + spreadScore * 0.35
                + readinessScore * 0.25
                + (sameMerchant ? 0.20 : 0.0)
                + (samePickupCluster ? 0.15 : 0.0)
                - trafficIntensity * 0.10
                - weatherPenalty;
    }

    private boolean isSameMerchant(List<Order> orders) {
        if (orders.isEmpty()) {
            return false;
        }
        String merchantId = orders.get(0).getMerchantId();
        if (merchantId == null || merchantId.isBlank()) {
            return false;
        }
        for (Order order : orders) {
            if (!merchantId.equals(order.getMerchantId())) {
                return false;
            }
        }
        return true;
    }

    private boolean isSamePickupCluster(List<Order> orders) {
        if (orders.isEmpty()) {
            return false;
        }
        String pickupClusterId = orders.get(0).getPickupClusterId();
        if (pickupClusterId == null || pickupClusterId.isBlank()) {
            return false;
        }
        for (Order order : orders) {
            if (!pickupClusterId.equals(order.getPickupClusterId())) {
                return false;
            }
        }
        return true;
    }

    private double averageDelayHazard(List<Order> orders) {
        return orders.stream()
                .mapToDouble(Order::getPickupDelayHazard)
                .average().orElse(0.0);
    }

    private double clusterSpreadKm(List<Order> orders) {
        if (orders.size() <= 1) {
            return 0.0;
        }
        double maxSpreadMeters = 0.0;
        for (int i = 0; i < orders.size(); i++) {
            for (int j = i + 1; j < orders.size(); j++) {
                maxSpreadMeters = Math.max(maxSpreadMeters,
                        orders.get(i).getPickupPoint().distanceTo(orders.get(j).getPickupPoint()));
            }
        }
        return maxSpreadMeters / 1000.0;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double computeReadinessDeltaMinutes(List<Order> orders) {
        double minReady = Double.POSITIVE_INFINITY;
        double maxReady = Double.NEGATIVE_INFINITY;
        for (Order order : orders) {
            if (order.getPredictedReadyAt() == null || order.getCreatedAt() == null) {
                continue;
            }
            double readyMinutes = java.time.Duration.between(
                    order.getCreatedAt(), order.getPredictedReadyAt()).toSeconds() / 60.0;
            minReady = Math.min(minReady, readyMinutes);
            maxReady = Math.max(maxReady, readyMinutes);
        }
        if (minReady == Double.POSITIVE_INFINITY || maxReady == Double.NEGATIVE_INFINITY) {
            return 0.0;
        }
        return maxReady - minReady;
    }

    private void classifyOrderPlan(DispatchPlan plan,
                                   List<DispatchPlan> sameMerchantWaves,
                                   List<DispatchPlan> compactClusterWaves,
                                   List<DispatchPlan> corridorAlignedWaves,
                                   List<DispatchPlan> stretchMultiOrder) {
        if (plan.getOrders().isEmpty()) {
            return;
        }
        if (plan.getBundleSize() <= 1) {
            return;
        }

        if (isSameMerchant(plan.getOrders())) {
            sameMerchantWaves.add(plan);
            return;
        }

        boolean samePickupCluster = isSamePickupCluster(plan.getOrders());
        boolean compact = clusterSpreadKm(plan.getOrders()) <= 0.45;
        if (samePickupCluster || compact) {
            compactClusterWaves.add(plan);
            return;
        }
        if (plan.getBundleSize() >= 3
                && plan.getDeliveryCorridorScore() >= 0.60
                && plan.getDeliveryZigZagPenalty() <= 0.35
                && plan.getLastDropLandingScore() >= 0.50) {
            corridorAlignedWaves.add(plan);
            return;
        }
        stretchMultiOrder.add(plan);
    }

    private double syntheticWaveOrderPriority(Order order, DriverDecisionContext ctx) {
        double distKm = ctx.driver().getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        double proximityScore = 1.0 / (1.0 + distKm / 0.9);
        double readinessScore = Math.max(0.0, 1.0 - order.getPickupDelayHazard());
        double merchantBonus = order.getMerchantId() == null || order.getMerchantId().isBlank() ? 0.0 : 0.12;
        double clusterBonus = order.getPickupClusterId() == null || order.getPickupClusterId().isBlank() ? 0.0 : 0.10;
        double feeScore = Math.min(1.0, order.getQuotedFee() / 45000.0) * 0.04;
        double corridorBonus = scoreDropCorridorAffinity(order, ctx) * 0.10;
        return proximityScore * 0.42
                + readinessScore * 0.32
                + merchantBonus
                + clusterBonus
                + feeScore
                + corridorBonus;
    }

    private double scoreSyntheticWaveSeed(List<Order> seed, DriverDecisionContext ctx) {
        double compactness = 1.0 / (1.0 + Math.max(0.0, clusterSpreadKm(seed) - 0.25));
        double readiness = Math.max(0.0, 1.0 - averageDelayHazard(seed));
        double avgPickupKm = seed.stream()
                .mapToDouble(order -> ctx.driver().getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0)
                .average()
                .orElse(3.0);
        double proximityScore = 1.0 / (1.0 + avgPickupKm / 0.9);
        double corridorScore = seed.stream()
                .mapToDouble(order -> scoreDropCorridorAffinity(order, ctx))
                .average()
                .orElse(0.45);
        return compactness * 0.30
                + readiness * 0.24
                + proximityScore * 0.18
                + (isSameMerchant(seed) ? 0.16 : 0.0)
                + (isSamePickupCluster(seed) ? 0.12 : 0.0)
                + corridorScore * 0.10
                + Math.min(1.0, ctx.thirdOrderFeasibilityScore()) * 0.10
                + Math.min(1.0, ctx.waveAssemblyPressure()) * 0.08;
    }

    private String orderKey(List<Order> orders) {
        return orders.stream()
                .map(Order::getId)
                .sorted()
                .reduce((a, b) -> a + "|" + b)
                .orElse("");
    }

    private List<DispatchPlan> collectWithQuota(List<DispatchPlan> safeSingles,
                                                List<DispatchPlan> sameMerchantWaves,
                                                List<DispatchPlan> compactClusterWaves,
                                                List<DispatchPlan> corridorAlignedWaves,
                                                List<DispatchPlan> stretchMultiOrder,
                                                List<DispatchPlan> idlePlans,
                                                StressRegime regime) {
        safeSingles.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        sameMerchantWaves.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        compactClusterWaves.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        corridorAlignedWaves.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        stretchMultiOrder.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        idlePlans.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());

        int maxPlans = executionProfile == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8 ? 22 : 14;
        boolean hardThreeOrderPolicy = executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                && (idlePlans.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive)
                || sameMerchantWaves.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive)
                || compactClusterWaves.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive)
                || corridorAlignedWaves.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive)
                || stretchMultiOrder.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive)
                || safeSingles.stream().anyMatch(DispatchPlan::isHardThreeOrderPolicyActive));
        int singleQuota = switch (regime) {
            case NORMAL -> hardThreeOrderPolicy ? 2
                    : executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC ? 3 : 5;
            case STRESS -> executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                    ? (hardThreeOrderPolicy ? 2 : Math.max(4, maxPlans / 3))
                    : Math.max(7, maxPlans / 2);
            case SEVERE_STRESS -> Math.max(9, maxPlans - 3);
        };
        int merchantQuota = switch (regime) {
            case NORMAL -> hardThreeOrderPolicy ? 6
                    : executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC ? 5 : 4;
            case STRESS -> executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                    ? (hardThreeOrderPolicy ? 5 : 4) : 3;
            case SEVERE_STRESS -> 2;
        };
        int compactQuota = switch (regime) {
            case NORMAL -> hardThreeOrderPolicy ? 5
                    : executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC ? 4 : 3;
            case STRESS -> executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                    ? (hardThreeOrderPolicy ? 4 : 3) : 2;
            case SEVERE_STRESS -> 1;
        };
        int corridorQuota = switch (regime) {
            case NORMAL -> hardThreeOrderPolicy ? 4
                    : executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC ? 3 : 2;
            case STRESS -> executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                    ? (hardThreeOrderPolicy ? 3 : 2) : 1;
            case SEVERE_STRESS -> 0;
        };
        int stretchQuota = regime == StressRegime.NORMAL ? 2 : 0;

        List<DispatchPlan> selected = new ArrayList<>();
        if ((regime == StressRegime.NORMAL || regime == StressRegime.STRESS)
                && executionProfile == OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC) {
            addTop(selected, sameMerchantWaves, merchantQuota);
            addTop(selected, compactClusterWaves, compactQuota);
            addTop(selected, corridorAlignedWaves, corridorQuota);
            if (regime == StressRegime.NORMAL) {
                addTop(selected, stretchMultiOrder, stretchQuota);
            }
            addTop(selected, safeSingles, singleQuota);
        } else {
            addTop(selected, safeSingles, singleQuota);
            addTop(selected, sameMerchantWaves, merchantQuota);
            addTop(selected, compactClusterWaves, compactQuota);
            addTop(selected, corridorAlignedWaves, corridorQuota);
            addTop(selected, stretchMultiOrder, stretchQuota);
        }

        for (DispatchPlan plan : idlePlans) {
            if (selected.size() >= maxPlans) {
                break;
            }
            selected.add(plan);
        }

        addTop(selected, sameMerchantWaves, maxPlans - selected.size());
        addTop(selected, compactClusterWaves, maxPlans - selected.size());
        addTop(selected, corridorAlignedWaves, maxPlans - selected.size());
        if (regime == StressRegime.NORMAL) {
            addTop(selected, stretchMultiOrder, maxPlans - selected.size());
        }
        addTop(selected, safeSingles, maxPlans - selected.size());
        ensureFallbackCoverage(
                selected,
                maxPlans,
                safeSingles,
                sameMerchantWaves,
                compactClusterWaves,
                corridorAlignedWaves);
        promoteHighUtilityMultiOrderWaves(
                selected,
                maxPlans,
                regime,
                hardThreeOrderPolicy,
                sameMerchantWaves,
                compactClusterWaves,
                corridorAlignedWaves,
                stretchMultiOrder);

        return selected;
    }

    private void ensureFallbackCoverage(List<DispatchPlan> selected,
                                        int maxPlans,
                                        List<DispatchPlan> safeSingles,
                                        List<DispatchPlan> sameMerchantWaves,
                                        List<DispatchPlan> compactClusterWaves,
                                        List<DispatchPlan> corridorAlignedWaves) {
        if (executionProfile != OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC) {
            return;
        }
        safeSingles.stream()
                .findFirst()
                .filter(plan -> !selected.contains(plan))
                .ifPresent(plan -> {
                    if (selected.size() < maxPlans) {
                        selected.add(plan);
                    }
                });
        compactClusterWaves.stream()
                .filter(plan -> plan.getBundleSize() == 2)
                .findFirst()
                .or(() -> sameMerchantWaves.stream().filter(plan -> plan.getBundleSize() == 2).findFirst())
                .or(() -> corridorAlignedWaves.stream().filter(plan -> plan.getBundleSize() == 2).findFirst())
                .filter(plan -> !selected.contains(plan))
                .ifPresent(plan -> {
                    if (selected.size() < maxPlans) {
                        selected.add(plan);
                    }
                });
    }

    private void promoteHighUtilityMultiOrderWaves(List<DispatchPlan> selected,
                                                   int maxPlans,
                                                   StressRegime regime,
                                                   boolean hardThreeOrderPolicy,
                                                   List<DispatchPlan> sameMerchantWaves,
                                                   List<DispatchPlan> compactClusterWaves,
                                                   List<DispatchPlan> corridorAlignedWaves,
                                                   List<DispatchPlan> stretchMultiOrder) {
        if (executionProfile != OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC) {
            return;
        }

        boolean alreadyHasStrongWave = selected.stream()
                .anyMatch(plan -> isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy));
        if (alreadyHasStrongWave) {
            return;
        }

        List<DispatchPlan> promotedCandidates = new ArrayList<>();
        promotedCandidates.addAll(sameMerchantWaves.stream()
                .filter(plan -> isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy))
                .toList());
        promotedCandidates.addAll(compactClusterWaves.stream()
                .filter(plan -> isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy))
                .toList());
        promotedCandidates.addAll(corridorAlignedWaves.stream()
                .filter(plan -> isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy))
                .toList());
        promotedCandidates.addAll(stretchMultiOrder.stream()
                .filter(plan -> isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy))
                .toList());
        promotedCandidates.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());

        for (DispatchPlan candidate : promotedCandidates) {
            if (selected.contains(candidate)) {
                continue;
            }

            DispatchPlan replaceable = selected.stream()
                    .filter(plan -> !plan.isWaitingForThirdOrder())
                    .filter(plan -> !isHighUtilityWaveCandidate(plan, regime, hardThreeOrderPolicy))
                    .min(Comparator.comparingDouble(DispatchPlan::getTotalScore))
                    .orElse(null);
            boolean candidateMateriallyBetter = replaceable != null
                    && candidate.getTotalScore() >= replaceable.getTotalScore() - 0.02;
            boolean replacesIdleOrWeakDirect = replaceable != null
                    && (replaceable.getOrders().isEmpty()
                    || replaceable.getBundleSize() <= 2
                    && replaceable.getExpectedPostCompletionEmptyKm() >= 1.6);
            if (selected.size() < maxPlans && candidate.getTotalScore() >= 0.66) {
                selected.add(candidate);
                return;
            }
            if (replaceable != null && candidateMateriallyBetter && replacesIdleOrWeakDirect) {
                selected.remove(replaceable);
                selected.add(candidate);
                return;
            }
        }
    }

    private boolean isHighUtilityWaveCandidate(DispatchPlan candidate,
                                               StressRegime regime,
                                               boolean hardThreeOrderPolicy) {
        if (candidate == null || candidate.getBundleSize() < 3) {
            return false;
        }
        double maxDeadhead = regime == StressRegime.NORMAL ? 2.9 : 2.5;
        double maxEmptyFinish = regime == StressRegime.NORMAL ? 2.0 : 1.6;
        double minLanding = hardThreeOrderPolicy ? 0.24 : 0.28;
        double minCorridor = hardThreeOrderPolicy ? 0.38 : 0.42;
        return candidate.getPredictedDeadheadKm() <= maxDeadhead
                && candidate.getExpectedPostCompletionEmptyKm() <= maxEmptyFinish
                && candidate.getDeliveryCorridorScore() >= minCorridor
                && candidate.getLastDropLandingScore() >= minLanding
                && candidate.getDeliveryZigZagPenalty() <= 0.64
                && candidate.getTotalScore() >= 0.62
                && candidate.isWaveLaunchEligible();
    }

    private void addTop(List<DispatchPlan> selected, List<DispatchPlan> source, int limit) {
        if (limit <= 0) {
            return;
        }
        int added = 0;
        for (DispatchPlan plan : source) {
            if (added >= limit) {
                break;
            }
            if (selected.contains(plan)) {
                continue;
            }
            selected.add(plan);
            added++;
        }
    }

    private void populateRouteAndLandingMetrics(DispatchPlan plan,
                                                SequenceOptimizer.RouteObjectiveMetrics routeMetrics) {
        plan.setRemainingDropProximityScore(routeMetrics.remainingDropProximityScore());
        plan.setDeliveryCorridorScore(routeMetrics.deliveryCorridorScore());
        plan.setLastDropLandingScore(routeMetrics.lastDropLandingScore());
        plan.setExpectedPostCompletionEmptyKm(routeMetrics.expectedPostCompletionEmptyKm());
        plan.setDeliveryZigZagPenalty(routeMetrics.deliveryZigZagPenalty());
        plan.setExpectedNextOrderIdleMinutes(routeMetrics.expectedNextOrderIdleMinutes());
    }

    private boolean hasVisibleThreeOrderWave(List<DispatchPlan> sameMerchantWaves,
                                             List<DispatchPlan> compactClusterWaves,
                                             List<DispatchPlan> corridorAlignedWaves,
                                             List<DispatchPlan> stretchMultiOrder) {
        return sameMerchantWaves.stream().anyMatch(plan -> plan.getBundleSize() >= 3)
                || compactClusterWaves.stream().anyMatch(plan -> plan.getBundleSize() >= 3)
                || corridorAlignedWaves.stream().anyMatch(plan -> plan.getBundleSize() >= 3)
                || stretchMultiOrder.stream().anyMatch(plan -> plan.getBundleSize() >= 3);
    }

    private boolean hasWaveExtensionOpportunity(Driver driver, List<DispatchPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return false;
        }
        int currentCommittedOrders = driver == null ? 0 : Math.max(0, driver.getCurrentOrderCount());
        boolean augmentable = isPrePickupAugmentable(driver);
        return plans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .anyMatch(plan -> {
                    int projectedSize = augmentable
                            ? currentCommittedOrders + plan.getBundleSize()
                            : plan.getBundleSize();
                    return projectedSize >= 3
                            && plan.getPredictedDeadheadKm() <= 3.6
                            && plan.getOnTimeProbability() >= 0.60
                            && plan.getDeliveryCorridorScore() >= 0.30
                            && plan.getLastDropLandingScore() >= 0.18;
                });
    }

    private boolean isPrePickupAugmentable(Driver driver) {
        return driver != null && driver.isPrePickupAugmentable();
    }

    private void applyPolicyMetadata(DispatchPlan plan,
                                     DriverDecisionContext ctx,
                                     StressRegime regime,
                                     boolean cleanWaveEligible,
                                     boolean waitingForThirdOrder) {
        boolean prePickupExtensionEligible = isPrePickupExtensionEligible(
                plan.getDriver(),
                plan.getOrders(),
                ctx,
                ctx != null && ctx.harshWeatherStress() ? WeatherProfile.HEAVY_RAIN : WeatherProfile.CLEAR,
                regime);
        boolean cleanThreeLaunch = (prefersThreeOrderLaunch(executionProfile, ctx)
                || (!plan.getOrders().isEmpty()
                && (plan.getBundleSize() >= 3 || prePickupExtensionEligible)
                && cleanWaveEligible))
                && regime != StressRegime.SEVERE_STRESS
                && !ctx.harshWeatherStress();
        plan.setStressRegime(regime);
        plan.setHarshWeatherStress(ctx.harshWeatherStress());
        boolean planLevelThreeOrderPolicy = cleanThreeLaunch
                && (waitingForThirdOrder
                || plan.getBundleSize() >= 3
                || prePickupExtensionEligible);
        plan.setHardThreeOrderPolicyActive(planLevelThreeOrderPolicy);
        plan.setWaitingForThirdOrder(waitingForThirdOrder);
        boolean stressFallbackOnly = ctx.harshWeatherStress()
                || regime == StressRegime.SEVERE_STRESS;
        stressFallbackOnly = stressFallbackOnly
                && !waitingForThirdOrder
                && !plan.getOrders().isEmpty()
                && plan.getBundleSize() < 3
                && !prePickupExtensionEligible;
        plan.setStressFallbackOnly(stressFallbackOnly);
        boolean waveLaunchEligible = !plan.getOrders().isEmpty()
                && (plan.getBundleSize() >= 3 || prePickupExtensionEligible)
                && (!cleanThreeLaunch || cleanWaveEligible || prePickupExtensionEligible);
        plan.setWaveLaunchEligible(waveLaunchEligible);
        if (waitingForThirdOrder) {
            plan.setSelectionBucket(SelectionBucket.HOLD_WAIT3);
            plan.setHoldRemainingCycles(regime == StressRegime.NORMAL ? 2
                    : regime == StressRegime.STRESS ? 1 : 0);
            plan.setHoldReason("wait_for_third_order");
            plan.setHoldAnchorZoneId(plan.getDriver() == null ? null : plan.getDriver().getRegionId());
        } else if (waveLaunchEligible) {
            if (plan.getBundleSize() >= 3) {
                plan.setSelectionBucket(SelectionBucket.WAVE_LOCAL);
            } else {
                plan.setSelectionBucket(SelectionBucket.EXTENSION_LOCAL);
            }
            plan.setHoldRemainingCycles(0);
        } else if (!plan.getOrders().isEmpty()) {
            plan.setSelectionBucket(SelectionBucket.SINGLE_LOCAL);
            plan.setHoldRemainingCycles(0);
        } else {
            plan.setSelectionBucket(SelectionBucket.EMERGENCY_COVERAGE);
            plan.setHoldRemainingCycles(0);
        }
    }

    public void setHoldPlansEnabled(boolean holdPlansEnabled) {
        this.holdPlansEnabled = holdPlansEnabled;
    }

    public void setRepositionPlansEnabled(boolean repositionPlansEnabled) {
        this.repositionPlansEnabled = repositionPlansEnabled;
    }

    public void setSmallBatchOnly(boolean smallBatchOnly) {
        this.smallBatchOnly = smallBatchOnly;
    }

    public void setExecutionProfile(OmegaDispatchAgent.ExecutionProfile executionProfile) {
        this.executionProfile = executionProfile == null
                ? OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC
                : executionProfile;
    }
}
