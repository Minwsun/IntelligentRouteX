package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioGateEvaluatorTest {

    @Test
    void normalIsAlwaysFirstAndSignalScenariosRespectEtaContext() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        DispatchCandidateContext context = RouteTestFixtures.candidateContext(properties);
        DispatchRouteCandidateStage routeCandidateStage = RouteTestFixtures.routeCandidateStage(properties);
        DispatchRouteProposalStage routeProposalStage = RouteTestFixtures.routeProposalStage(properties);
        RouteProposal proposal = routeProposalStage.routeProposals().getFirst();
        DriverCandidate driverCandidate = matchingDriverCandidate(routeCandidateStage, proposal);
        ScenarioGateEvaluator gateEvaluator = new ScenarioGateEvaluator(properties);

        var clearDecisions = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.etaContext());
        var weatherDecisions = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.weatherBadEtaContext());
        var trafficDecisions = gateEvaluator.gate(proposal, driverCandidate, context, RouteTestFixtures.trafficBadEtaContext());

        assertEquals(ScenarioType.NORMAL, clearDecisions.getFirst().scenario());
        assertTrue(clearDecisions.getFirst().applied());
        assertFalse(clearDecisions.stream().filter(decision -> decision.scenario() == ScenarioType.WEATHER_BAD).findFirst().orElseThrow().applied());
        assertFalse(clearDecisions.stream().filter(decision -> decision.scenario() == ScenarioType.TRAFFIC_BAD).findFirst().orElseThrow().applied());
        assertTrue(weatherDecisions.stream().filter(decision -> decision.scenario() == ScenarioType.WEATHER_BAD).findFirst().orElseThrow().applied());
        assertTrue(trafficDecisions.stream().filter(decision -> decision.scenario() == ScenarioType.TRAFFIC_BAD).findFirst().orElseThrow().applied());
        assertFalse(clearDecisions.stream().filter(decision -> decision.scenario() == ScenarioType.DEMAND_SHIFT).findFirst().orElseThrow().applied());
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
