package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioGateEvaluatorForecastIntegrationTest {

    @Test
    void freshConfidentThresholdMetForecastAppliesWhileStaleLowConfidenceAndUnavailableSkipExplicitly() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = matchingDriverCandidate(routeCandidateStage, proposal);
        ScenarioGateEvaluator gateEvaluator = new ScenarioGateEvaluator(properties);
        MlWorkerMetadata metadata = new MlWorkerMetadata("chronos-2", "v1", "sha256:chronos", 11L);

        ForecastScenarioContext appliedContext = new ForecastScenarioContext(
                ForecastResult.applied(30, 0.71, Map.of("q50", -0.1), 0.8, 90000L, metadata),
                ForecastResult.applied(20, 0.74, Map.of("q50", 0.16), 0.82, 80000L, metadata),
                ForecastResult.applied(45, 0.69, Map.of("q50", 0.12), 0.79, 85000L, metadata),
                new com.routechain.v2.context.FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 90000L, true, true, true),
                List.of(),
                List.of());

        ForecastScenarioContext staleContext = new ForecastScenarioContext(
                ForecastResult.applied(30, 0.71, Map.of("q50", -0.1), 0.8, properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1, metadata),
                ForecastResult.notApplied("forecast-unavailable", metadata),
                ForecastResult.notApplied("forecast-unavailable", metadata),
                new com.routechain.v2.context.FreshnessMetadata("freshness-metadata/v1", 0L, 0L, properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1, true, true, false),
                List.of(),
                List.of("forecast-stale"));

        ForecastScenarioContext lowConfidenceContext = new ForecastScenarioContext(
                ForecastResult.notApplied("forecast-unavailable", metadata),
                ForecastResult.applied(20, 0.74, Map.of("q50", 0.16), properties.getScenario().getForecast().getConfidenceThreshold() - 0.01, 80000L, metadata),
                ForecastResult.notApplied("forecast-unavailable", metadata),
                new com.routechain.v2.context.FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 80000L, true, true, true),
                List.of(),
                List.of());

        ForecastScenarioContext unavailableContext = new ForecastScenarioContext(
                ForecastResult.notApplied("forecast-unavailable", metadata),
                ForecastResult.notApplied("forecast-unavailable", metadata),
                ForecastResult.notApplied("forecast-unavailable", metadata),
                com.routechain.v2.context.FreshnessMetadata.empty(),
                List.of(),
                List.of("forecast-unavailable"));

        var applied = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.etaContext(), appliedContext.freshnessMetadata(), List.of(), appliedContext);
        var stale = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.etaContext(), staleContext.freshnessMetadata(), List.of(), staleContext);
        var lowConfidence = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.etaContext(), lowConfidenceContext.freshnessMetadata(), List.of(), lowConfidenceContext);
        var unavailable = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.etaContext(), unavailableContext.freshnessMetadata(), List.of(), unavailableContext);

        assertTrue(decision(applied, ScenarioType.DEMAND_SHIFT).applied());
        assertTrue(decision(applied, ScenarioType.ZONE_BURST).applied());
        assertTrue(decision(applied, ScenarioType.POST_DROP_SHIFT).applied());
        assertFalse(decision(stale, ScenarioType.DEMAND_SHIFT).applied());
        assertTrue(decision(stale, ScenarioType.DEMAND_SHIFT).reasons().contains("forecast-stale"));
        assertFalse(decision(lowConfidence, ScenarioType.ZONE_BURST).applied());
        assertTrue(decision(lowConfidence, ScenarioType.ZONE_BURST).reasons().contains("forecast-low-confidence"));
        assertFalse(decision(unavailable, ScenarioType.POST_DROP_SHIFT).applied());
        assertTrue(decision(unavailable, ScenarioType.POST_DROP_SHIFT).reasons().contains("forecast-unavailable"));
    }

    private ScenarioGateDecision decision(List<ScenarioGateDecision> decisions, ScenarioType scenarioType) {
        return decisions.stream().filter(decision -> decision.scenario() == scenarioType).findFirst().orElseThrow();
    }

    private DriverCandidate matchingDriverCandidate(DispatchRouteCandidateStage routeCandidateStage, RouteProposal proposal) {
        return routeCandidateStage.driverCandidates().stream()
                .filter(candidate -> candidate.bundleId().equals(proposal.bundleId())
                        && candidate.anchorOrderId().equals(proposal.anchorOrderId())
                        && candidate.driverId().equals(proposal.driverId()))
                .findFirst()
                .orElseThrow();
    }
}
