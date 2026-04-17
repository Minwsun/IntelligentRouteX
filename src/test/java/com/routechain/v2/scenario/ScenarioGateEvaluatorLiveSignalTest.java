package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.context.FreshnessMetadata;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DispatchRouteCandidateStage;
import com.routechain.v2.route.DispatchRouteProposalStage;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioGateEvaluatorLiveSignalTest {

    @Test
    void staleOrLowConfidenceSignalsAreSuppressedWithExplicitReasons() {
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
        ScenarioGateEvaluator gateEvaluator = new ScenarioGateEvaluator(properties);

        var weatherSuppressed = gateEvaluator.gate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.weatherBadEtaContext(),
                new FreshnessMetadata("freshness-metadata/v1", 1000L, 0L, 0L, false, true, false),
                List.of(new LiveStageMetadata("live-stage-metadata/v1", "eta/context", "open-meteo", false, true, 1000L, 0.2, 5L, "open-meteo-stale")));
        var trafficSuppressed = gateEvaluator.gate(
                proposal,
                driverCandidate,
                context,
                RouteTestFixtures.trafficBadEtaContext(),
                new FreshnessMetadata("freshness-metadata/v1", 0L, 1000L, 0L, true, false, false),
                List.of(new LiveStageMetadata("live-stage-metadata/v1", "eta/context", "tomtom-traffic", false, true, 1000L, 0.2, 5L, "tomtom-stale")));

        assertFalse(weatherSuppressed.stream().filter(decision -> decision.scenario() == ScenarioType.WEATHER_BAD).findFirst().orElseThrow().applied());
        assertTrue(weatherSuppressed.stream().filter(decision -> decision.scenario() == ScenarioType.WEATHER_BAD).findFirst().orElseThrow().reasons().contains("weather-signal-stale"));
        assertFalse(trafficSuppressed.stream().filter(decision -> decision.scenario() == ScenarioType.TRAFFIC_BAD).findFirst().orElseThrow().applied());
        assertTrue(trafficSuppressed.stream().filter(decision -> decision.scenario() == ScenarioType.TRAFFIC_BAD).findFirst().orElseThrow().reasons().contains("traffic-signal-stale"));
    }
}
