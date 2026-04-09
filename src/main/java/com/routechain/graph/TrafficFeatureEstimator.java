package com.routechain.graph;

import com.routechain.ai.DriverDecisionContext;
import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.infra.FeatureStore;
import com.routechain.simulation.DispatchPlan;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Derives route-facing traffic and landing features from live operational data
 * plus the open-source road graph baseline.
 */
public final class TrafficFeatureEstimator {
    private final FeatureStore featureStore;

    public TrafficFeatureEstimator(FeatureStore featureStore) {
        this.featureStore = featureStore;
    }

    public TrafficFeatureEstimate estimateAndStore(String runId,
                                                   DispatchPlan plan,
                                                   DriverDecisionContext ctx,
                                                   RoadGraphSnapshot graphSnapshot,
                                                   SpatiotemporalField field,
                                                   double trafficIntensity,
                                                   WeatherProfile weather) {
        double approachCongestion = clamp01(graphSnapshot.approachCorridor().congestionScore());
        double deliveryCongestion = clamp01(graphSnapshot.deliveryCorridor().congestionScore());
        double approachDrift = clamp01(graphSnapshot.approachDrift().driftRatio());
        double deliveryDrift = clamp01(graphSnapshot.deliveryDrift().driftRatio());
        double weatherSeverity = clamp01(Math.max(
                weather.ordinal() / 3.0,
                Math.max(graphSnapshot.approachCorridor().weatherSeverity(), graphSnapshot.deliveryCorridor().weatherSeverity())));
        double clearCalibration = weather == WeatherProfile.CLEAR
                ? 0.82
                : weather == WeatherProfile.LIGHT_RAIN ? 0.90 : 1.0;
        double reachabilityLift = weather == WeatherProfile.CLEAR
                ? 1.05
                : weather == WeatherProfile.LIGHT_RAIN ? 1.02 : 1.0;
        double pickupDemand = clamp01(graphSnapshot.pickupZone().openDemand() / 3.5);
        double pickupSupply = clamp01(graphSnapshot.pickupZone().activeSupply() / 5.0);
        double pickupCommittedPressure = clamp01(graphSnapshot.pickupZone().committedPickupPressure());
        double pickupDeadhead = clamp01(plan.getPredictedDeadheadKm() / 4.0);
        double pickupFrictionScore = clamp01(
                pickupDeadhead * 0.28
                        + pickupCommittedPressure * 0.18
                        + clamp01(plan.getMerchantPrepRiskScore()) * 0.18
                        + graphSnapshot.pickupZone().slowdownIndex() * 0.14
                        + approachCongestion * 0.12
                        + approachDrift * 0.10
                        + Math.max(0.0, pickupDemand - pickupSupply) * 0.08);
        pickupFrictionScore = clamp01(pickupFrictionScore * clearCalibration);

        double demandReach5 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(plan.getEndZonePoint(), 5) / 3.0);
        double demandReach10 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(plan.getEndZonePoint(), 10) / 3.5);
        double demandReach15 = field == null ? 0.0 : clamp01(field.getForecastDemandAt(plan.getEndZonePoint(), 15) / 4.0);
        double dropReachabilityScore = clamp01(
                graphSnapshot.dropZone().postDropOpportunity10m() * 0.30
                        + (1.0 - graphSnapshot.dropZone().emptyZoneRisk10m()) * 0.24
                        + graphSnapshot.dropZone().attractionScore() * 0.14
                        + demandReach5 * 0.12
                        + demandReach10 * 0.10
                        + demandReach15 * 0.05
                        + Math.max(0.0, 1.0 - deliveryCongestion) * 0.05);
        dropReachabilityScore = clamp01(dropReachabilityScore * reachabilityLift);

        double corridorCongestionScore = clamp01(
                approachCongestion * 0.34
                        + deliveryCongestion * 0.40
                        + Math.max(approachDrift, deliveryDrift) * 0.16
                        + weatherSeverity * 0.10);
        corridorCongestionScore = clamp01(corridorCongestionScore * clearCalibration);
        double zoneSlowdownIndex = clamp01(
                graphSnapshot.pickupZone().slowdownIndex() * 0.30
                        + graphSnapshot.dropZone().slowdownIndex() * 0.42
                        + deliveryDrift * 0.18
                        + weatherSeverity * 0.10);
        zoneSlowdownIndex = clamp01(zoneSlowdownIndex * clearCalibration);
        double travelTimeDriftScore = clamp01(Math.max(approachDrift, deliveryDrift) * clearCalibration);
        double localTrafficDelta = ctx == null
                ? 0.0
                : Math.abs(ctx.localTrafficForecast10m() - trafficIntensity);
        double localDemandMismatch = ctx == null
                ? 0.0
                : Math.abs(ctx.localPostDropOpportunity() - graphSnapshot.dropZone().postDropOpportunity10m());
        double confidenceBlend = (
                graphSnapshot.approachCorridor().confidence()
                        + graphSnapshot.deliveryCorridor().confidence()
                        + graphSnapshot.approachDrift().confidence()
                        + graphSnapshot.deliveryDrift().confidence()) / 4.0;
        double trafficUncertaintyScore = clamp01(
                localTrafficDelta * 0.30
                        + localDemandMismatch * 0.18
                        + Math.abs(approachDrift - deliveryDrift) * 0.20
                        + Math.abs(approachCongestion - deliveryCongestion) * 0.12
                        + (1.0 - confidenceBlend) * 0.20);
        trafficUncertaintyScore = clamp01(trafficUncertaintyScore * (weather == WeatherProfile.CLEAR ? 0.88 : 1.0));

        Map<String, Object> traceSnapshot = new LinkedHashMap<>();
        traceSnapshot.put("roadGraphBackend", graphSnapshot.backend());
        traceSnapshot.put("pickupCellId", graphSnapshot.pickupCellId());
        traceSnapshot.put("dropCellId", graphSnapshot.dropCellId());
        traceSnapshot.put("approachCorridorId", graphSnapshot.approachCorridor().corridorId());
        traceSnapshot.put("deliveryCorridorId", graphSnapshot.deliveryCorridor().corridorId());
        traceSnapshot.put("pickupFrictionScore", pickupFrictionScore);
        traceSnapshot.put("dropReachabilityScore", dropReachabilityScore);
        traceSnapshot.put("corridorCongestionScore", corridorCongestionScore);
        traceSnapshot.put("zoneSlowdownIndex", zoneSlowdownIndex);
        traceSnapshot.put("travelTimeDriftScore", travelTimeDriftScore);
        traceSnapshot.put("trafficUncertaintyScore", trafficUncertaintyScore);
        traceSnapshot.put("approachBaselineMinutes", graphSnapshot.approachCorridor().baselineTravelMinutes());
        traceSnapshot.put("deliveryBaselineMinutes", graphSnapshot.deliveryCorridor().baselineTravelMinutes());
        traceSnapshot.put("approachLiveMinutes", graphSnapshot.approachDrift().liveTravelMinutes());
        traceSnapshot.put("deliveryLiveMinutes", graphSnapshot.deliveryDrift().liveTravelMinutes());

        TrafficFeatureEstimate estimate = new TrafficFeatureEstimate(
                plan.getTraceId(),
                graphSnapshot.backend(),
                graphSnapshot.pickupCellId(),
                graphSnapshot.dropCellId(),
                graphSnapshot.approachCorridor().corridorId(),
                graphSnapshot.deliveryCorridor().corridorId(),
                pickupFrictionScore,
                dropReachabilityScore,
                corridorCongestionScore,
                zoneSlowdownIndex,
                travelTimeDriftScore,
                trafficUncertaintyScore,
                traceSnapshot);

        storeEstimate(runId, estimate, graphSnapshot);
        return estimate;
    }

    private void storeEstimate(String runId,
                               TrafficFeatureEstimate estimate,
                               RoadGraphSnapshot graphSnapshot) {
        String safeRunId = runId == null || runId.isBlank() ? "run-unset" : runId;
        featureStore.put(
                GraphFeatureNamespaces.ROAD_GRAPH_FEATURES,
                "run:" + safeRunId + ":trace:" + estimate.traceId(),
                Map.ofEntries(
                        Map.entry("backend", graphSnapshot.backend()),
                        Map.entry("serviceTier", graphSnapshot.serviceTier()),
                        Map.entry("pickupCellId", graphSnapshot.pickupCellId()),
                        Map.entry("dropCellId", graphSnapshot.dropCellId()),
                        Map.entry("approachCorridorId", graphSnapshot.approachCorridor().corridorId()),
                        Map.entry("deliveryCorridorId", graphSnapshot.deliveryCorridor().corridorId()),
                        Map.entry("matrixId", graphSnapshot.travelTimeMatrix().matrixId()),
                        Map.entry("approachBaselineMinutes", graphSnapshot.approachCorridor().baselineTravelMinutes()),
                        Map.entry("deliveryBaselineMinutes", graphSnapshot.deliveryCorridor().baselineTravelMinutes()),
                        Map.entry("approachDistanceKm", graphSnapshot.approachCorridor().baselineDistanceKm()),
                        Map.entry("deliveryDistanceKm", graphSnapshot.deliveryCorridor().baselineDistanceKm())));
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:trace:" + estimate.traceId(),
                estimate.traceSnapshot());
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:zone:" + estimate.pickupCellId(),
                Map.ofEntries(
                        Map.entry("zoneId", graphSnapshot.pickupZone().zoneId()),
                        Map.entry("trafficForecast10m", graphSnapshot.pickupZone().trafficForecast10m()),
                        Map.entry("weatherSeverity", graphSnapshot.pickupZone().weatherSeverity()),
                        Map.entry("slowdownIndex", graphSnapshot.pickupZone().slowdownIndex()),
                        Map.entry("pickupFrictionScore", estimate.pickupFrictionScore()),
                        Map.entry("committedPickupPressure", graphSnapshot.pickupZone().committedPickupPressure())));
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:zone:" + estimate.dropCellId(),
                Map.ofEntries(
                        Map.entry("zoneId", graphSnapshot.dropZone().zoneId()),
                        Map.entry("trafficForecast10m", graphSnapshot.dropZone().trafficForecast10m()),
                        Map.entry("weatherSeverity", graphSnapshot.dropZone().weatherSeverity()),
                        Map.entry("slowdownIndex", graphSnapshot.dropZone().slowdownIndex()),
                        Map.entry("dropReachabilityScore", estimate.dropReachabilityScore()),
                        Map.entry("postDropOpportunity10m", graphSnapshot.dropZone().postDropOpportunity10m()),
                        Map.entry("emptyZoneRisk10m", graphSnapshot.dropZone().emptyZoneRisk10m())));
        featureStore.put(
                GraphFeatureNamespaces.TRAFFIC_FEATURES,
                "latest:corridor:" + estimate.deliveryCorridorId(),
                Map.ofEntries(
                        Map.entry("corridorId", estimate.deliveryCorridorId()),
                        Map.entry("corridorCongestionScore", estimate.corridorCongestionScore()),
                        Map.entry("travelTimeDriftScore", estimate.travelTimeDriftScore()),
                        Map.entry("trafficUncertaintyScore", estimate.trafficUncertaintyScore()),
                        Map.entry("baselineTravelMinutes", graphSnapshot.deliveryCorridor().baselineTravelMinutes()),
                        Map.entry("liveTravelMinutes", graphSnapshot.deliveryDrift().liveTravelMinutes())));
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
