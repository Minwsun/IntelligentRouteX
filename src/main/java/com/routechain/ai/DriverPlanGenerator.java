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
 * Generates driver-centric candidate plans from a local world snapshot.
 */
public class DriverPlanGenerator {

    private static final int MAX_PLANS_PER_DRIVER = 12;
    private static final int MAX_SEQUENCES_PER_BUNDLE = 5;
    private static final double HOLD_PLAN_BASE_SCORE = 0.03;
    private static final double REPOSITION_BASE_SCORE = 0.02;
    private boolean holdPlansEnabled = true;
    private boolean repositionPlansEnabled = true;
    private boolean smallBatchOnly = false;

    public DriverPlanGenerator() {
    }

    public List<DispatchPlan> generate(DriverDecisionContext ctx,
                                       double trafficIntensity,
                                       WeatherProfile weather,
                                       int simulatedHour) {

        List<DispatchPlan> plans = new ArrayList<>();
        Driver driver = ctx.driver();

        for (Order order : ctx.reachableOrders()) {
            DispatchPlan plan = buildSingleOrderPlan(driver, order, trafficIntensity, weather);
            if (plan != null) {
                plans.add(plan);
            }
        }

        for (OrderCluster cluster : ctx.pickupClusters()) {
            if (cluster.orders().size() < 2) {
                continue;
            }

            int dynamicMax = computeDynamicBatchCap(cluster, trafficIntensity, weather, driver);
            int maxSize = Math.min(dynamicMax, cluster.orders().size());
            for (int size = 2; size <= maxSize; size++) {
                plans.addAll(buildBundlePlans(driver, cluster, size, trafficIntensity, weather));
            }
        }

        boolean hasActionableOrderPlans = plans.stream()
                .anyMatch(plan -> !plan.getOrders().isEmpty());

        boolean allowStrategicHold = !hasActionableOrderPlans
                || (ctx.reachableOrders().size() <= 1
                && ctx.nearReadyOrders() >= 2
                && ctx.localDemandForecast5m() > ctx.localDemandIntensity() * 1.15
                && ctx.localWeatherExposure() < 0.85
                && ctx.localCorridorExposure() < 0.85);

        if (holdPlansEnabled && allowStrategicHold) {
            plans.add(buildHoldPlan(driver, ctx));
        }

        boolean allowReposition = !hasActionableOrderPlans
                && ctx.localDemandForecast5m() < 0.8
                && ctx.localDemandForecast10m() < 0.9
                && ctx.localWeatherExposure() < 0.70
                && ctx.localCorridorExposure() < 0.75;

        if (repositionPlansEnabled && allowReposition) {
            for (EndZoneCandidate zone : ctx.endZoneCandidates()) {
                DispatchPlan reposPlan = buildRepositionPlan(driver, zone);
                if (reposPlan != null) {
                    plans.add(reposPlan);
                }
            }
        }

        plans.sort(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed());
        if (plans.size() > MAX_PLANS_PER_DRIVER) {
            return new ArrayList<>(plans.subList(0, MAX_PLANS_PER_DRIVER));
        }
        return plans;
    }

    private DispatchPlan buildSingleOrderPlan(Driver driver, Order order,
                                              double trafficIntensity,
                                              WeatherProfile weather) {
        Bundle bundle = new Bundle(
                "S-" + order.getId(),
                List.of(order),
                order.getQuotedFee(),
                1
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(trafficIntensity, weather);
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
        double deliveryKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        double feeNorm = order.getQuotedFee() / 35000.0;
        double prelimScore = feeNorm * 0.5
                - (deadheadKm / 6.0) * 0.3
                + (deliveryKm > 0 ? 1.0 / (1.0 + deadheadKm / deliveryKm) : 0) * 0.2;

        plan.setTotalScore(Math.max(0.01, prelimScore));
        plan.setPredictedDeadheadKm(deadheadKm);
        plan.setTraceId("SINGLE-" + UUID.randomUUID().toString().substring(0, 6));
        return plan;
    }

    private List<DispatchPlan> buildBundlePlans(Driver driver,
                                                OrderCluster cluster,
                                                int bundleSize,
                                                double trafficIntensity,
                                                WeatherProfile weather) {
        List<DispatchPlan> result = new ArrayList<>();
        List<Order> candidates = cluster.orders();

        if (candidates.size() == bundleSize) {
            DispatchPlan plan = buildBundlePlanFromOrders(driver, candidates, trafficIntensity, weather);
            if (plan != null) {
                result.add(plan);
            }
            return result;
        }

        List<Order> byFee = new ArrayList<>(candidates);
        byFee.sort(Comparator.comparingDouble(Order::getQuotedFee).reversed());
        List<Order> topFee = byFee.subList(0, Math.min(bundleSize, byFee.size()));

        DispatchPlan feePlan = buildBundlePlanFromOrders(driver, topFee, trafficIntensity, weather);
        if (feePlan != null) {
            result.add(feePlan);
        }

        if (candidates.size() > bundleSize) {
            List<Order> nearest = new ArrayList<>(candidates);
            GeoPoint driverPos = driver.getCurrentLocation();
            nearest.sort(Comparator.comparingDouble(o -> driverPos.distanceTo(o.getPickupPoint())));
            List<Order> nearSelected = nearest.subList(0, Math.min(bundleSize, nearest.size()));

            if (!nearSelected.equals(topFee)) {
                DispatchPlan nearPlan = buildBundlePlanFromOrders(
                        driver, nearSelected, trafficIntensity, weather);
                if (nearPlan != null) {
                    result.add(nearPlan);
                }
            }
        }

        return result;
    }

    private DispatchPlan buildBundlePlanFromOrders(Driver driver,
                                                   List<Order> orders,
                                                   double trafficIntensity,
                                                   WeatherProfile weather) {
        double totalFee = orders.stream().mapToDouble(Order::getQuotedFee).sum();

        Bundle bundle = new Bundle(
                "B-" + UUID.randomUUID().toString().substring(0, 8),
                List.copyOf(orders),
                totalFee,
                orders.size()
        );

        SequenceOptimizer seqOpt = createSequenceOptimizer(trafficIntensity, weather);
        List<List<Stop>> sequences = seqOpt.generateFeasibleSequences(
                driver, bundle, MAX_SEQUENCES_PER_BUNDLE);
        if (sequences.isEmpty()) {
            return null;
        }

        List<Stop> bestSeq = sequences.get(0);
        DispatchPlan plan = new DispatchPlan(driver, bundle, bestSeq);

        double deadheadKm = driver.getCurrentLocation().distanceTo(orders.get(0).getPickupPoint()) / 1000.0;
        boolean sameMerchant = isSameMerchant(orders);
        double maxDeadheadKm = computeMaxPlanDeadheadKm(trafficIntensity, weather, sameMerchant);
        if (deadheadKm > maxDeadheadKm) {
            return null;
        }
        double standaloneDist = orders.stream()
                .mapToDouble(o -> o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double bundleRouteDist = computeRouteDistance(driver.getCurrentLocation(), bestSeq);
        if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                && !sameMerchant
                && bundleRouteDist > standaloneDist * 1.35) {
            return null;
        }
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

    private DispatchPlan buildHoldPlan(Driver driver, DriverDecisionContext ctx) {
        Bundle holdBundle = new Bundle("HOLD", List.of(), 0, 0);
        DispatchPlan plan = new DispatchPlan(driver, holdBundle, List.of());

        double holdScore = HOLD_PLAN_BASE_SCORE
                + ctx.localDemandIntensity() * 0.04
                + ctx.localDemandForecast5m() * 0.05
                + ctx.localDemandForecast10m() * 0.06
                + ctx.localDemandForecast15m() * 0.04
                + ctx.localDemandForecast30m() * 0.03
                + ctx.localSpikeProbability() * 0.06
                + ctx.localShortagePressure() * 0.03
                + Math.min(3, ctx.nearReadyOrders()) * 0.025
                - ctx.localDriverDensity() / 20.0 * 0.02
                - ctx.localWeatherExposure() * 0.05
                - ctx.localCorridorExposure() * 0.04;

        plan.setTotalScore(Math.max(0.01, Math.min(0.12, holdScore)));
        plan.setConfidence(0.3);
        plan.setTraceId("HOLD");
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
                - zone.distanceKm() / 5.0 * 0.04
                - zone.weatherExposure() * 0.04
                - zone.corridorExposure() * 0.05;

        plan.setTotalScore(Math.max(0.01, score));
        plan.setConfidence(0.2);
        plan.setEndZoneOpportunity(zone.attractionScore());
        plan.setTraceId("REPOS");
        return plan;
    }

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

    private int computeDynamicBatchCap(OrderCluster cluster,
                                       double trafficSeverity,
                                       WeatherProfile weather,
                                       Driver driver) {
        int cap = 3;

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
        if (trafficSeverity < 0.2 && weather == WeatherProfile.CLEAR) {
            cap += 1;
        }

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
        if (sameMerchant) {
            cap += 1;
        }
        if (!sameMerchant && weather == WeatherProfile.HEAVY_RAIN) {
            cap = Math.min(cap, 2);
        }
        if (!sameMerchant && weather == WeatherProfile.STORM) {
            cap = 2;
        }

        double avgDelayHazard = cluster.orders().stream()
                .mapToDouble(Order::getPickupDelayHazard)
                .average().orElse(0);
        if (avgDelayHazard > 0.5) {
            cap -= 1;
        }

        if (driver.getActiveOrderIds().size() >= 2) {
            cap -= 1;
        }

        if (smallBatchOnly) {
            cap = Math.min(cap, 2);
        }

        return Math.max(2, Math.min(cap, 5));
    }

    private double computeMaxPlanDeadheadKm(double trafficIntensity,
                                            WeatherProfile weather,
                                            boolean sameMerchant) {
        double maxDeadhead = switch (weather) {
            case CLEAR -> 5.8 - trafficIntensity * 1.1;
            case LIGHT_RAIN -> 4.8 - trafficIntensity * 1.0;
            case HEAVY_RAIN -> 3.0 - trafficIntensity * 0.8;
            case STORM -> 2.2 - trafficIntensity * 0.5;
        };
        if (sameMerchant) {
            maxDeadhead += 0.4;
        }
        return Math.max(1.8, maxDeadhead);
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

    public void setHoldPlansEnabled(boolean holdPlansEnabled) {
        this.holdPlansEnabled = holdPlansEnabled;
    }

    public void setRepositionPlansEnabled(boolean repositionPlansEnabled) {
        this.repositionPlansEnabled = repositionPlansEnabled;
    }

    public void setSmallBatchOnly(boolean smallBatchOnly) {
        this.smallBatchOnly = smallBatchOnly;
    }
}
