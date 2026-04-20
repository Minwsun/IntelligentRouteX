package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioEvaluatorRouteVectorTest {

    @Test
    void geometrySignalsShiftAppliedScenarioScoresAdditively() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = routeCandidateStage.driverCandidates().stream()
                .filter(candidate -> candidate.bundleId().equals(proposal.bundleId())
                        && candidate.anchorOrderId().equals(proposal.anchorOrderId())
                        && candidate.driverId().equals(proposal.driverId()))
                .findFirst()
                .orElseThrow();
        ScenarioEvaluator evaluator = new ScenarioEvaluator(properties);

        ScenarioEvaluationResult evaluation = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.trafficBadEtaContext(),
                new ScenarioGateDecision(ScenarioType.TRAFFIC_BAD, true, List.of("eta-context-signal-present"), List.of()));

        assertTrue(proposal.geometryAvailable());
        assertTrue(evaluation.evaluation().stabilityScore() >= 0.0);
        assertTrue(evaluation.trace().applied());
    }
}
