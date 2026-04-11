package com.routechain.simulation;

import com.routechain.ai.BanditPosteriorSnapshot;
import com.routechain.graph.FutureCellValue;
import com.routechain.graph.GraphAffinitySnapshot;
import com.routechain.graph.GraphExplanationTrace;
import com.routechain.infra.AdminQueryService;
import com.routechain.infra.PlatformRuntimeBootstrap;

import java.time.Instant;
import java.util.List;

/**
 * Snapshot artifact for the route intelligence control room.
 */
public record ControlRoomFrame(
        String runId,
        String scenarioName,
        Instant snapshotAt,
        String serviceTier,
        String executionProfile,
        String activePolicy,
        String routeLatencyMode,
        String summaryHeadline,
        List<String> evidence,
        LatencyBreakdown latency,
        IntelligenceScorecard intelligence,
        ScenarioAcceptanceResult acceptance,
        ForecastDriftSnapshot forecastDrift,
        RoutePolicyProfile routePolicyProfile,
        List<CellValueSnapshot> cityTwinCells,
        List<FutureCellValue> futureCellValues,
        List<DriverFutureValue> driverFutureValues,
        List<GraphAffinitySnapshot> graphAffinities,
        List<GraphExplanationTrace> graphExplanations,
        List<MarketplaceEdge> marketplaceEdges,
        List<RiderCopilotRecommendation> riderCopilot,
        List<ModelPromotionDecision> modelPromotions,
        BanditPosteriorSnapshot adaptiveWeightSnapshot,
        AdminQueryService.SystemAdminSnapshot adminSnapshot
) {
    public ControlRoomFrame {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        cityTwinCells = cityTwinCells == null ? List.of() : List.copyOf(cityTwinCells);
        futureCellValues = futureCellValues == null ? List.of() : List.copyOf(futureCellValues);
        driverFutureValues = driverFutureValues == null ? List.of() : List.copyOf(driverFutureValues);
        graphAffinities = graphAffinities == null ? List.of() : List.copyOf(graphAffinities);
        graphExplanations = graphExplanations == null ? List.of() : List.copyOf(graphExplanations);
        marketplaceEdges = marketplaceEdges == null ? List.of() : List.copyOf(marketplaceEdges);
        riderCopilot = riderCopilot == null ? List.of() : List.copyOf(riderCopilot);
        modelPromotions = modelPromotions == null ? List.of() : List.copyOf(modelPromotions);
    }

    public ControlRoomFrame(
            String runId,
            String scenarioName,
            Instant snapshotAt,
            String serviceTier,
            String executionProfile,
            String activePolicy,
            String routeLatencyMode,
            String summaryHeadline,
            List<String> evidence,
            LatencyBreakdown latency,
            IntelligenceScorecard intelligence,
            ScenarioAcceptanceResult acceptance,
            ForecastDriftSnapshot forecastDrift,
            RoutePolicyProfile routePolicyProfile,
            List<CellValueSnapshot> cityTwinCells,
            List<FutureCellValue> futureCellValues,
            List<DriverFutureValue> driverFutureValues,
            List<GraphAffinitySnapshot> graphAffinities,
            List<GraphExplanationTrace> graphExplanations,
            List<MarketplaceEdge> marketplaceEdges,
            List<RiderCopilotRecommendation> riderCopilot,
            List<ModelPromotionDecision> modelPromotions,
            AdminQueryService.SystemAdminSnapshot adminSnapshot
    ) {
        this(
                runId,
                scenarioName,
                snapshotAt,
                serviceTier,
                executionProfile,
                activePolicy,
                routeLatencyMode,
                summaryHeadline,
                evidence,
                latency,
                intelligence,
                acceptance,
                forecastDrift,
                routePolicyProfile,
                cityTwinCells,
                futureCellValues,
                driverFutureValues,
                graphAffinities,
                graphExplanations,
                marketplaceEdges,
                riderCopilot,
                modelPromotions,
                PlatformRuntimeBootstrap.getModelArtifactProvider().banditPosteriorSnapshot("route-utility-bandit"),
                adminSnapshot
        );
    }
}
