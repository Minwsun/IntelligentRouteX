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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioEvaluationSkipSemanticsTest {

    @Test
    void forecastDrivenScenariosAreEmittedSkippedAndNearBaseline() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = matchingDriverCandidate(routeCandidateStage, proposal);
        ScenarioEvaluator evaluator = new ScenarioEvaluator(properties);

        ScenarioEvaluationResult skipped = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.DEMAND_SHIFT, false, List.of("forecast-not-integrated-yet"), List.of("forecast-unavailable-scenario-skipped")));

        assertFalse(skipped.evaluation().applied());
        assertEquals(proposal.projectedPickupEtaMinutes(), skipped.evaluation().projectedPickupEtaMinutes());
        assertEquals(proposal.projectedCompletionEtaMinutes(), skipped.evaluation().projectedCompletionEtaMinutes());
        assertTrue(skipped.evaluation().reasons().contains("forecast-not-integrated-yet"));
        assertTrue(skipped.evaluation().degradeReasons().contains("forecast-unavailable-scenario-skipped"));
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
