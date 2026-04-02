package com.routechain.simulation;

import com.routechain.infra.AdminQueryService;

import java.time.Instant;
import java.util.List;

/**
 * Read-only digital twin frame for the local control-room demo.
 */
public record ControlRoomFrame(
        String runId,
        String scenarioName,
        Instant generatedAt,
        String dominantServiceTier,
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
        List<DriverFutureValue> driverFutureValues,
        List<MarketplaceEdge> marketplaceEdges,
        List<RiderCopilotRecommendation> riderCopilot,
        List<ModelPromotionDecision> modelPromotions,
        AdminQueryService.SystemAdminSnapshot adminSnapshot
) {
    public ControlRoomFrame {
        runId = runId == null || runId.isBlank() ? "run-unset" : runId;
        scenarioName = scenarioName == null || scenarioName.isBlank() ? "unknown" : scenarioName;
        dominantServiceTier = dominantServiceTier == null || dominantServiceTier.isBlank()
                ? "instant"
                : dominantServiceTier;
        executionProfile = executionProfile == null || executionProfile.isBlank()
                ? "MAINLINE_REALISTIC"
                : executionProfile;
        activePolicy = activePolicy == null || activePolicy.isBlank() ? "NORMAL" : activePolicy;
        routeLatencyMode = routeLatencyMode == null || routeLatencyMode.isBlank()
                ? "SIMULATED_ASYNC"
                : routeLatencyMode;
        summaryHeadline = summaryHeadline == null ? "" : summaryHeadline;
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
        latency = latency == null ? LatencyBreakdown.empty() : latency;
        intelligence = intelligence == null ? IntelligenceScorecard.empty() : intelligence;
        acceptance = acceptance == null ? ScenarioAcceptanceResult.empty() : acceptance;
        forecastDrift = forecastDrift == null
                ? new ForecastDriftSnapshot(runId, scenarioName, 0.0, 0.0, 0.0, 0.0, 0.0, false, "UNASSESSED", "")
                : forecastDrift;
        cityTwinCells = cityTwinCells == null ? List.of() : List.copyOf(cityTwinCells);
        driverFutureValues = driverFutureValues == null ? List.of() : List.copyOf(driverFutureValues);
        marketplaceEdges = marketplaceEdges == null ? List.of() : List.copyOf(marketplaceEdges);
        riderCopilot = riderCopilot == null ? List.of() : List.copyOf(riderCopilot);
        modelPromotions = modelPromotions == null ? List.of() : List.copyOf(modelPromotions);
    }
}
