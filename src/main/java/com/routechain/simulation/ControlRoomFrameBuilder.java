package com.routechain.simulation;

import com.routechain.ai.FeatureExtractor;
import com.routechain.ai.ModelArtifactProvider;
import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.ai.PolicySelector;
import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DeliveryServiceTier;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.OrderStatus;
import com.routechain.domain.Enums.SurgeSeverity;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.graph.GraphExplanationTrace;
import com.routechain.graph.GraphShadowSnapshot;
import com.routechain.infra.AdminQueryService;
import com.routechain.infra.PlatformRuntimeBootstrap;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a digital twin/control-room frame directly from the current simulation state.
 */
public final class ControlRoomFrameBuilder {
    private static final List<String> MODEL_KEYS = List.of(
            "eta-model",
            "plan-ranker-model",
            "empty-zone-risk-model",
            "neural-route-prior-model");

    private ControlRoomFrameBuilder() {}

    public static ControlRoomFrame buildFromEngine(SimulationEngine engine, RunReport report) {
        if (engine == null) {
            throw new IllegalArgumentException("engine must not be null");
        }
        if (report == null) {
            throw new IllegalArgumentException("report must not be null");
        }

        OmegaDispatchAgent omegaAgent = engine.getOmegaAgent();
        SpatiotemporalField field = omegaAgent.getField();
        field.update(
                new ArrayList<>(engine.getActiveOrders()),
                new ArrayList<>(engine.getDrivers()),
                engine.getSimulatedHour(),
                engine.getTrafficIntensity(),
                engine.getWeatherProfile());

        String serviceTier = report.dominantServiceTier();
        List<CellValueSnapshot> topCells = field.topCellSnapshots(serviceTier, 8);
        GraphShadowSnapshot graphShadow = PlatformRuntimeBootstrap.getGraphShadowProjector().project(
                report.runId(),
                report.scenarioName(),
                serviceTier,
                engine.getDrivers(),
                engine.getActiveOrders(),
                field);
        RoutePolicyProfile policyProfile = buildPolicyProfile(engine, report, omegaAgent);
        ForecastDriftSnapshot forecastDrift = buildForecastDrift(report);
        List<ModelPromotionDecision> modelPromotions = buildModelPromotions(report, forecastDrift);
        List<DriverFutureValue> driverFutureValues = buildDriverFutureValues(engine, field, serviceTier, topCells);
        List<MarketplaceEdge> marketplaceEdges = buildMarketplaceEdges(engine, field, graphShadow, report.runId());
        List<GraphExplanationTrace> graphExplanations = marketplaceEdges.stream()
                .map(MarketplaceEdge::graphExplanationTrace)
                .toList();
        List<RiderCopilotRecommendation> copilot = buildCopilotRecommendations(serviceTier, topCells);
        AdminQueryService.SystemAdminSnapshot adminSnapshot =
                PlatformRuntimeBootstrap.getAdminQueryService().snapshot();

        return new ControlRoomFrame(
                report.runId(),
                report.scenarioName(),
                Instant.now(),
                serviceTier,
                engine.getExecutionProfile().name(),
                omegaAgent.getActivePolicy(),
                engine.getRouteLatencyMode().name(),
                buildHeadline(report, forecastDrift),
                buildEvidence(report, topCells, forecastDrift, modelPromotions, graphShadow, marketplaceEdges),
                report.latency(),
                report.intelligence(),
                report.acceptance(),
                forecastDrift,
                policyProfile,
                topCells,
                graphShadow.futureCellValues(),
                driverFutureValues,
                graphShadow.affinities(),
                graphExplanations,
                marketplaceEdges,
                copilot,
                modelPromotions,
                adminSnapshot
        );
    }

    private static RoutePolicyProfile buildPolicyProfile(SimulationEngine engine,
                                                         RunReport report,
                                                         OmegaDispatchAgent omegaAgent) {
        List<Order> pendingOrders = engine.getActiveOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED
                        || order.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .toList();
        long availableDrivers = engine.getDrivers().stream()
                .filter(driver -> driver.getState() == DriverState.ONLINE_IDLE
                        || driver.getState() == DriverState.ROUTE_PENDING
                        || driver.getState() == DriverState.PICKUP_EN_ROUTE)
                .count();
        double shortageRatio = pendingOrders.isEmpty()
                ? 0.0
                : Math.min(1.0, Math.max(0.0,
                (pendingOrders.size() - availableDrivers) / Math.max(1.0, pendingOrders.size())));
        double avgPendingWaitMinutes = pendingOrders.stream()
                .filter(order -> order.getCreatedAt() != null)
                .mapToDouble(order -> Duration.between(order.getCreatedAt(), engine.getClock().currentInstant())
                        .toSeconds() / 60.0)
                .average()
                .orElse(0.0);
        double surgeLevel = engine.getRegions().stream()
                .map(Region::getSurgeSeverity)
                .mapToDouble(ControlRoomFrameBuilder::surgeValue)
                .max()
                .orElse(0.0);
        FeatureExtractor extractor = new FeatureExtractor();
        double[] contextFeatures = extractor.contextFeatures(
                engine.getTrafficIntensity(),
                engine.getWeatherProfile(),
                engine.getSimulatedHour(),
                shortageRatio,
                avgPendingWaitMinutes,
                pendingOrders.size(),
                (int) availableDrivers,
                surgeLevel);
        PolicySelector selector = omegaAgent.getPolicySelector();
        return new RoutePolicyProfile(
                omegaAgent.getActivePolicy(),
                report.dominantServiceTier(),
                engine.getExecutionProfile().name(),
                engine.getRouteLatencyMode().name(),
                gateProfile(report, engine),
                reserveProfile(report),
                "epsilon-greedy",
                0.10,
                shortageRatio,
                avgPendingWaitMinutes,
                surgeLevel,
                engine.getTrafficIntensity(),
                engine.getWeatherProfile().name(),
                selector.getPredictedRewards(contextFeatures),
                selector.getSelectionCounts()
        );
    }

    private static ForecastDriftSnapshot buildForecastDrift(RunReport report) {
        double continuationGap = report.forecastCalibrationSummary().continuationCalibrationGap();
        double merchantPrepMae = report.forecastCalibrationSummary().merchantPrepMaeMinutes();
        double trafficForecastError = report.intelligence().trafficForecastError();
        double weatherForecastHitRate = report.intelligence().weatherForecastHitRate();
        double borrowSuccessCalibration = report.intelligence().borrowSuccessCalibration();
        boolean drifted = continuationGap > 0.18
                || merchantPrepMae > 4.0
                || trafficForecastError > 0.26
                || weatherForecastHitRate < 0.62
                || borrowSuccessCalibration > 0.22;
        String verdict = drifted ? "PROMOTION_BLOCKED" : "HEALTHY";
        String note = drifted
                ? String.format(Locale.ROOT,
                "forecast drift guard active (contGap=%.2f prepMae=%.2f trafficErr=%.2f weatherHit=%.2f borrowCal=%.2f)",
                continuationGap, merchantPrepMae, trafficForecastError, weatherForecastHitRate, borrowSuccessCalibration)
                : "forecast calibration within production-small tolerance";
        return new ForecastDriftSnapshot(
                report.runId(),
                report.scenarioName(),
                continuationGap,
                merchantPrepMae,
                trafficForecastError,
                weatherForecastHitRate,
                borrowSuccessCalibration,
                drifted,
                verdict,
                note
        );
    }

    private static List<ModelPromotionDecision> buildModelPromotions(RunReport report,
                                                                     ForecastDriftSnapshot forecastDrift) {
        ModelArtifactProvider provider = PlatformRuntimeBootstrap.getModelArtifactProvider();
        List<ModelPromotionDecision> decisions = new ArrayList<>(MODEL_KEYS.size());
        for (String modelKey : MODEL_KEYS) {
            var champion = provider.activeBundle(modelKey);
            String challengerVersion = provider.challengerBundles(modelKey).stream()
                    .map(bundle -> bundle.modelVersion())
                    .findFirst()
                    .orElse("");
            boolean readyForCounterfactual = !challengerVersion.isBlank()
                    && !forecastDrift.drifted()
                    && report.latency().dispatchP95Ms() <= 120.0
                    && report.intelligence().businessScore() >= 0.65;
            String decision = challengerVersion.isBlank()
                    ? "NO_CHALLENGER_REGISTERED"
                    : (readyForCounterfactual ? "READY_FOR_COUNTERFACTUAL" : "KEEP_CHAMPION");
            String reason = challengerVersion.isBlank()
                    ? "register challenger bundle before promotion"
                    : (readyForCounterfactual
                    ? "challenger lane may be evaluated on the same event tape"
                    : "latency, business score, or forecast drift still blocks promotion");
            decisions.add(new ModelPromotionDecision(
                    modelKey,
                    champion.modelVersion(),
                    challengerVersion,
                    decision,
                    reason,
                    false,
                    champion.latencyBudgetMs(),
                    report.latency().dispatchP95Ms(),
                    report.intelligence().businessScore(),
                    forecastDrift.continuationCalibrationGap()
            ));
        }
        return decisions;
    }

    private static List<RiderCopilotRecommendation> buildCopilotRecommendations(String serviceTier,
                                                                                List<CellValueSnapshot> topCells) {
        List<RiderCopilotRecommendation> recommendations = new ArrayList<>();
        for (int i = 0; i < Math.min(3, topCells.size()); i++) {
            CellValueSnapshot cell = topCells.get(i);
            String action;
            if (cell.weatherForecast10m() > 0.62 || cell.trafficForecast10m() > 0.72) {
                action = "AVOID_CORRIDOR";
            } else if (cell.postDropOpportunity10m() >= 0.70 && cell.emptyZoneRisk10m() <= 0.35) {
                action = "SHIFT_700M_TO_CELL";
            } else if (cell.shortageForecast10m() >= 0.60) {
                action = "HOLD_COVERAGE";
            } else {
                action = "STAY_READY";
            }
            recommendations.add(new RiderCopilotRecommendation(
                    "copilot-" + (i + 1) + "-" + cell.cellId(),
                    "idle-driver",
                    serviceTier,
                    action,
                    cell.cellId(),
                    cell.centerLat(),
                    cell.centerLng(),
                    cell.compositeValue(),
                    cell.postDropOpportunity10m(),
                    cell.emptyZoneRisk10m(),
                    cell.reserveTargetScore(),
                    String.format(Locale.ROOT,
                            "%s -> postDrop=%.2f emptyRisk=%.2f demand10=%.2f reserve=%.2f",
                            action,
                            cell.postDropOpportunity10m(),
                            cell.emptyZoneRisk10m(),
                            cell.demandForecast10m(),
                            cell.reserveTargetScore())
            ));
        }
        return recommendations;
    }

    private static List<DriverFutureValue> buildDriverFutureValues(SimulationEngine engine,
                                                                   SpatiotemporalField field,
                                                                   String serviceTier,
                                                                   List<CellValueSnapshot> topCells) {
        List<Driver> candidateDrivers = engine.getDrivers().stream()
                .filter(driver -> driver.getState() == DriverState.ONLINE_IDLE
                        || driver.getState() == DriverState.ROUTE_PENDING
                        || driver.getState() == DriverState.PICKUP_EN_ROUTE
                        || driver.getState() == DriverState.WAITING_PICKUP)
                .limit(Math.min(4, topCells.size()))
                .toList();
        List<DriverFutureValue> values = new ArrayList<>();
        for (int i = 0; i < candidateDrivers.size() && i < topCells.size(); i++) {
            Driver driver = candidateDrivers.get(i);
            CellValueSnapshot targetCell = topCells.get(i);
            String currentCellId = field.cellKeyOf(driver.getCurrentLocation());
            double currentZoneValue = field.getRiskAdjustedAttractionAt(driver.getCurrentLocation());
            double targetZoneValue = targetCell.compositeValue();
            double futureScore = Math.max(0.0, Math.min(1.0,
                    targetZoneValue * 0.52
                            + targetCell.postDropOpportunity10m() * 0.28
                            + targetCell.reserveTargetScore() * 0.12
                            + (1.0 - targetCell.emptyZoneRisk10m()) * 0.08));
            values.add(new DriverFutureValue(
                    driver.getId(),
                    currentCellId,
                    targetCell.cellId(),
                    serviceTier,
                    10,
                    currentZoneValue,
                    targetZoneValue,
                    targetCell.postDropOpportunity10m(),
                    targetCell.emptyZoneRisk10m(),
                    targetCell.reserveTargetScore(),
                    futureScore,
                    String.format(Locale.ROOT,
                            "Shift %s from %s to %s because futureValue=%.2f, postDrop=%.2f, emptyRisk=%.2f",
                            driver.getId(),
                            currentCellId,
                            targetCell.cellId(),
                            futureScore,
                            targetCell.postDropOpportunity10m(),
                            targetCell.emptyZoneRisk10m())
            ));
        }
        return values;
    }

    private static List<MarketplaceEdge> buildMarketplaceEdges(SimulationEngine engine,
                                                               SpatiotemporalField field,
                                                               GraphShadowSnapshot graphShadow,
                                                               String runId) {
        List<Order> pendingOrders = engine.getActiveOrders().stream()
                .filter(order -> order.getStatus() == OrderStatus.CONFIRMED
                        || order.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .limit(4)
                .toList();
        if (pendingOrders.isEmpty()) {
            pendingOrders = engine.getActiveOrders().stream()
                    .filter(order -> order.getStatus() != OrderStatus.DELIVERED
                            && order.getStatus() != OrderStatus.CANCELLED
                            && order.getStatus() != OrderStatus.FAILED)
                    .limit(4)
                    .toList();
        }
        List<Driver> availableDrivers = engine.getDrivers().stream()
                .filter(driver -> driver.getState() == DriverState.ONLINE_IDLE
                        || driver.getState() == DriverState.ROUTE_PENDING
                        || driver.getState() == DriverState.PICKUP_EN_ROUTE
                        || driver.getState() == DriverState.WAITING_PICKUP)
                .filter(driver -> driver.getCurrentOrderCount() < 2)
                .limit(8)
                .toList();
        if (availableDrivers.isEmpty()) {
            availableDrivers = engine.getDrivers().stream()
                    .filter(driver -> driver.getState() != DriverState.OFFLINE)
                    .limit(8)
                    .toList();
        }
        List<MarketplaceEdge> edges = new ArrayList<>();
        double weatherSpeedFactor = switch (engine.getWeatherProfile()) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 0.90;
            case HEAVY_RAIN -> 0.72;
            case STORM -> 0.58;
        };

        for (Order order : pendingOrders) {
            DeliveryServiceTier tier = DeliveryServiceTier.classify(order);
            availableDrivers.stream()
                    .sorted((left, right) -> Double.compare(
                            left.getCurrentLocation().distanceTo(order.getPickupPoint()),
                            right.getCurrentLocation().distanceTo(order.getPickupPoint())))
                    .limit(2)
                    .forEach(driver -> {
                        double deadheadKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
                        double speedKmh = Math.max(8.0, 24.0 * (1.0 - engine.getTrafficIntensity() * 0.45) * weatherSpeedFactor);
                        double pickupEtaMinutes = deadheadKm / speedKmh * 60.0;
                        double pickupWeather = field.getWeatherForecastAt(order.getPickupPoint(), 10);
                        double pickupTraffic = field.getTrafficForecastAt(order.getPickupPoint(), 10);
                        double dropPostDrop = field.getPostDropOpportunityAt(order.getDropoffPoint(), 10);
                        double dropEmptyRisk = field.getEmptyZoneRiskAt(order.getDropoffPoint(), 10);
                        boolean borrowed = !driver.getRegionId().equals(order.getPickupRegionId());
                        double executionScore = clamp01(
                                clamp01(1.0 - deadheadKm / 3.0) * 0.42
                                        + clamp01(1.0 - pickupEtaMinutes / 12.0) * 0.34
                                        + (1.0 - pickupTraffic) * 0.14
                                        + (1.0 - pickupWeather) * 0.10
                                        - (borrowed ? 0.08 : 0.0));
                        double continuationScore = clamp01(
                                dropPostDrop * 0.58
                                        + (1.0 - dropEmptyRisk) * 0.30
                                        + Math.min(1.0, field.getForecastDemandAt(order.getDropoffPoint(), 10) / 4.5) * 0.12);
                        double coverageScore = clamp01(
                                (borrowed ? 0.22 : 0.46)
                                        + Math.max(0.0, 1.0 - field.getShortageAt(order.getPickupPoint())) * 0.24
                                        + Math.max(0.0, 1.0 - deadheadKm / 3.5) * 0.18
                                        + Math.max(0.0, 1.0 - pickupTraffic) * 0.12);
                        double linehaulKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
                        double dropoffEtaMinutes = pickupEtaMinutes + linehaulKm / speedKmh * 60.0;
                        DispatchPlan edgePlan = new DispatchPlan(
                                driver,
                                new DispatchPlan.Bundle(
                                        "marketplace-" + driver.getId() + "-" + order.getId(),
                                        List.of(order),
                                        0.0,
                                        1),
                                List.of(
                                        new DispatchPlan.Stop(
                                                order.getId(),
                                                order.getPickupPoint(),
                                                DispatchPlan.Stop.StopType.PICKUP,
                                                pickupEtaMinutes),
                                        new DispatchPlan.Stop(
                                                order.getId(),
                                                order.getDropoffPoint(),
                                                DispatchPlan.Stop.StopType.DROPOFF,
                                                dropoffEtaMinutes)));
                        edgePlan.setServiceTier(tier.wireValue());
                        edgePlan.setPredictedDeadheadKm(deadheadKm);
                        edgePlan.setPredictedTotalMinutes(dropoffEtaMinutes);
                        edgePlan.setPostDropDemandProbability(dropPostDrop);
                        edgePlan.setEmptyRiskAfter(dropEmptyRisk);
                        edgePlan.setExecutionScore(executionScore);
                        edgePlan.setContinuationScore(continuationScore);
                        edgePlan.setCoverageScore(coverageScore);
                        GraphExplanationTrace graphTrace = PlatformRuntimeBootstrap.getGraphAffinityScorer().scorePlan(
                                runId,
                                graphShadow,
                                null,
                                edgePlan,
                                field,
                                engine.getWeatherProfile(),
                                engine.getTrafficIntensity());
                        double edgeScore = clamp01(
                                executionScore * 0.48
                                        + continuationScore * 0.24
                                        + coverageScore * 0.08
                                        + graphTrace.graphAffinityScore() * 0.20
                                        - (borrowed ? 0.06 : 0.0));
                        edges.add(new MarketplaceEdge(
                                "edge-" + driver.getId() + "-" + order.getId(),
                                driver.getId(),
                                order.getId(),
                                tier.wireValue(),
                                field.cellKeyOf(order.getPickupPoint()),
                                field.cellKeyOf(order.getDropoffPoint()),
                                pickupEtaMinutes,
                                deadheadKm,
                                executionScore,
                                continuationScore,
                                graphTrace.graphAffinityScore(),
                                edgeScore,
                                borrowed,
                                String.format(Locale.ROOT,
                                        "%s edge with dh=%.2fkm eta=%.1fm postDrop=%.2f emptyRisk=%.2f graph=%.2f",
                                        borrowed ? "borrowed" : "local",
                                        deadheadKm,
                                        pickupEtaMinutes,
                                        dropPostDrop,
                                        dropEmptyRisk,
                                        graphTrace.graphAffinityScore()),
                                graphTrace)
                        );
                    });
        }
        edges.sort((left, right) -> Double.compare(right.edgeScore(), left.edgeScore()));
        return edges.stream().limit(8).toList();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String buildHeadline(RunReport report, ForecastDriftSnapshot forecastDrift) {
        return String.format(
                Locale.ROOT,
                "%s | tier=%s | dh/completed=%.2fkm | postDropHit=%.1f%% | completion=%.1f%% | dispatchP95=%.1fms | drift=%s",
                report.scenarioName(),
                report.dominantServiceTier(),
                report.deadheadPerCompletedOrderKm(),
                report.postDropOrderHitRate(),
                report.completionRate(),
                report.latency().dispatchP95Ms(),
                forecastDrift.verdict()
        );
    }

    private static List<String> buildEvidence(RunReport report,
                                              List<CellValueSnapshot> topCells,
                                              ForecastDriftSnapshot forecastDrift,
                                              List<ModelPromotionDecision> promotions,
                                              GraphShadowSnapshot graphShadow,
                                              List<MarketplaceEdge> marketplaceEdges) {
        List<String> evidence = new ArrayList<>();
        if (!topCells.isEmpty()) {
            CellValueSnapshot hotspot = topCells.get(0);
            evidence.add(String.format(
                    Locale.ROOT,
                    "Top future cell %s has postDrop=%.2f, emptyRisk=%.2f, demand10=%.2f",
                    hotspot.cellId(),
                    hotspot.postDropOpportunity10m(),
                    hotspot.emptyZoneRisk10m(),
                    hotspot.demandForecast10m()));
        }
        if (graphShadow != null) {
            evidence.add(String.format(
                    Locale.ROOT,
                    "Graph shadow export=%s nodes=%d affinities=%d futureCells=%d",
                    graphShadow.exportMode(),
                    graphShadow.nodes().size(),
                    graphShadow.affinities().size(),
                    graphShadow.futureCellValues().size()));
        }
        if (marketplaceEdges != null && !marketplaceEdges.isEmpty()) {
            MarketplaceEdge bestEdge = marketplaceEdges.get(0);
            evidence.add(String.format(
                    Locale.ROOT,
                    "Top graph edge %s -> %s score=%.2f graph=%.2f borrowed=%s",
                    bestEdge.driverId(),
                    bestEdge.orderId(),
                    bestEdge.edgeScore(),
                    bestEdge.graphAffinityScore(),
                    bestEdge.borrowed()));
        }
        evidence.add(String.format(
                Locale.ROOT,
                "Business=%s / Balanced=%s / dispatchP95=%.1fms",
                report.intelligence().primaryVerdict(),
                report.intelligence().secondaryVerdict(),
                report.latency().dispatchP95Ms()));
        evidence.add(String.format(
                Locale.ROOT,
                "Empty-mile north star: dh/completed=%.2fkm, postDropHit=%.1f%%, noDriverFound=%.2f%%",
                report.deadheadPerCompletedOrderKm(),
                report.postDropOrderHitRate(),
                report.intelligence().noDriverFoundRate()));
        evidence.add(forecastDrift.note());
        promotions.stream()
                .limit(2)
                .map(promotion -> promotion.modelKey() + "=" + promotion.decision())
                .forEach(evidence::add);
        return evidence;
    }

    private static String gateProfile(RunReport report, SimulationEngine engine) {
        String tier = report.dominantServiceTier();
        if ("instant".equalsIgnoreCase(tier)) {
            return "instant-local-first-execution-gate";
        }
        if ("2h".equalsIgnoreCase(tier)) {
            return "corridor-aware-wave-gate";
        }
        if ("4h".equalsIgnoreCase(tier) || "scheduled".equalsIgnoreCase(tier)) {
            return "network-utilization-gate";
        }
        if (engine.getWeatherProfile().ordinal() >= 2) {
            return "harsh-weather-conservative-gate";
        }
        return "execution-first-default";
    }

    private static String reserveProfile(RunReport report) {
        if (report.postDropOrderHitRate() < 30.0) {
            return "aggressive-post-drop-heal";
        }
        if (report.intelligence().reserveStability() < 0.50) {
            return "reserve-stabilization";
        }
        return "balanced-reserve-shaping";
    }

    private static double surgeValue(SurgeSeverity severity) {
        if (severity == null) {
            return 0.0;
        }
        return switch (severity) {
            case NORMAL -> 0.0;
            case MEDIUM -> 0.4;
            case HIGH -> 0.7;
            case CRITICAL -> 1.0;
        };
    }
}
