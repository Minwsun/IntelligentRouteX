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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioEvaluatorTest {

    @Test
    void appliedScenariosWorsenEtaAndRemainDeterministic() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = matchingDriverCandidate(routeCandidateStage, proposal);
        ScenarioEvaluator evaluator = new ScenarioEvaluator(properties);

        ScenarioEvaluationResult normal = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.NORMAL, true, List.of("baseline-scenario"), List.of()));
        ScenarioEvaluationResult weather = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.weatherBadEtaContext(),
                new ScenarioGateDecision(ScenarioType.WEATHER_BAD, true, List.of("eta-context-signal-present"), List.of()));
        ScenarioEvaluationResult traffic = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.trafficBadEtaContext(),
                new ScenarioGateDecision(ScenarioType.TRAFFIC_BAD, true, List.of("eta-context-signal-present"), List.of()));
        ScenarioEvaluationResult merchant = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.etaContext(),
                new ScenarioGateDecision(ScenarioType.MERCHANT_DELAY, true, List.of("ready-time-spread-wide"), List.of()));
        ScenarioEvaluationResult weatherAgain = evaluator.evaluate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.weatherBadEtaContext(),
                new ScenarioGateDecision(ScenarioType.WEATHER_BAD, true, List.of("eta-context-signal-present"), List.of()));

        assertTrue(weather.evaluation().projectedPickupEtaMinutes() > normal.evaluation().projectedPickupEtaMinutes());
        assertTrue(traffic.evaluation().projectedCompletionEtaMinutes() > normal.evaluation().projectedCompletionEtaMinutes());
        assertTrue(merchant.evaluation().projectedPickupEtaMinutes() > normal.evaluation().projectedPickupEtaMinutes());
        assertEquals(weather.evaluation(), weatherAgain.evaluation());
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
