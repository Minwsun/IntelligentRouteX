package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.integration.ForecastResult;
import com.routechain.v2.integration.MlWorkerMetadata;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioEvaluatorForecastIntegrationTest {

    @Test
    void appliedForecastScenariosPerturbValueLandingAndStabilityDeterministically() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = matchingDriverCandidate(routeCandidateStage, proposal);
        ScenarioEvaluator evaluator = new ScenarioEvaluator(properties);
        MlWorkerMetadata metadata = new MlWorkerMetadata("chronos-2", "v1", "sha256:chronos", 11L);
        ForecastScenarioContext forecastContext = new ForecastScenarioContext(
                ForecastResult.applied(30, 0.71, Map.of("q50", -0.09), 0.84, 90000L, metadata),
                ForecastResult.applied(20, 0.74, Map.of("q50", 0.16), 0.82, 80000L, metadata),
                ForecastResult.applied(45, 0.69, Map.of("q50", 0.12), 0.8, 85000L, metadata),
                new com.routechain.v2.context.FreshnessMetadata("freshness-metadata/v1", 0L, 0L, 90000L, true, true, true),
                List.of(),
                List.of());

        ScenarioEvaluationResult normal = evaluator.evaluate(
                proposal, driverCandidate, context, RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.NORMAL, true, List.of("baseline-scenario"), List.of()),
                forecastContext);
        ScenarioEvaluationResult demandShift = evaluator.evaluate(
                proposal, driverCandidate, context, RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.DEMAND_SHIFT, true, List.of("forecast-signal-present"), List.of()),
                forecastContext);
        ScenarioEvaluationResult zoneBurst = evaluator.evaluate(
                proposal, driverCandidate, context, RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.ZONE_BURST, true, List.of("forecast-signal-present"), List.of()),
                forecastContext);
        ScenarioEvaluationResult postDrop = evaluator.evaluate(
                proposal, driverCandidate, context, RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.POST_DROP_SHIFT, true, List.of("forecast-signal-present"), List.of()),
                forecastContext);

        assertTrue(demandShift.evaluation().landingValue() < normal.evaluation().landingValue());
        assertTrue(zoneBurst.evaluation().value() > normal.evaluation().value());
        assertNotEquals(normal.evaluation().stabilityScore(), postDrop.evaluation().stabilityScore());
        assertTrue(postDrop.evaluation().reasons().contains("post-drop-shift-forecast-perturbation"));
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
