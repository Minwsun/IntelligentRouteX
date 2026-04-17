package com.routechain.v2.scenario;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.EtaContext;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.context.FreshnessMetadata;
import com.routechain.v2.route.DispatchCandidateContext;
import com.routechain.v2.route.DriverCandidate;
import com.routechain.v2.route.RouteProposal;

import java.util.ArrayList;
import java.util.List;

public final class ScenarioGateEvaluator {
    private final RouteChainDispatchV2Properties properties;

    public ScenarioGateEvaluator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<ScenarioGateDecision> gate(RouteProposal proposal,
                                           DriverCandidate driverCandidate,
                                           DispatchCandidateContext context,
                                           EtaContext etaContext,
                                           FreshnessMetadata freshnessMetadata,
                                           List<LiveStageMetadata> liveStageMetadata) {
        List<ScenarioGateDecision> decisions = new ArrayList<>();
        decisions.add(new ScenarioGateDecision(ScenarioType.NORMAL, true, List.of("baseline-scenario"), List.of()));
        decisions.add(weatherSignalScenario(etaContext, freshnessMetadata, liveStageMetadata));
        decisions.add(trafficSignalScenario(etaContext, freshnessMetadata, liveStageMetadata));
        decisions.add(skippedForecastScenario(ScenarioType.DEMAND_SHIFT));
        decisions.add(skippedForecastScenario(ScenarioType.ZONE_BURST));
        decisions.add(skippedForecastScenario(ScenarioType.POST_DROP_SHIFT));
        decisions.add(new ScenarioGateDecision(
                ScenarioType.MERCHANT_DELAY,
                context.readyTimeSpread(proposal.bundleId()) >= Math.max(4, properties.getPair().getReadyGapMinutesThreshold() / 2),
                List.of("ready-time-spread-wide"),
                List.of()));
        decisions.add(new ScenarioGateDecision(
                ScenarioType.DRIVER_DRIFT,
                proposal.projectedPickupEtaMinutes() >= 10.0 || driverCandidate.rerankScore() < 0.55,
                List.of("pickup-or-rerank-drift-risk"),
                List.of()));
        decisions.add(new ScenarioGateDecision(
                ScenarioType.PICKUP_QUEUE,
                context.bundle(proposal.bundleId()).orderIds().size() >= 3
                        || context.pickupCompactness(proposal.bundleId()) < 0.72
                        || context.acceptedBoundaryParticipation(proposal.bundleId()) > 0.0,
                List.of("pickup-shape-density-risk"),
                List.of()));
        return List.copyOf(decisions);
    }

    private ScenarioGateDecision weatherSignalScenario(EtaContext etaContext,
                                                       FreshnessMetadata freshnessMetadata,
                                                       List<LiveStageMetadata> liveStageMetadata) {
        if (!etaContext.weatherBadSignal()) {
            return signalScenario(ScenarioType.WEATHER_BAD, false, "weather-bad-signal-not-present");
        }
        LiveStageMetadata metadata = liveStageMetadata.stream()
                .filter(candidate -> candidate.stageName().equals("eta/context") && candidate.sourceName().equals("open-meteo"))
                .findFirst()
                .orElse(null);
        if (fresnessFalseOrMissing(freshnessMetadata == null ? null : freshnessMetadata.weatherFresh())) {
            return signalScenario(ScenarioType.WEATHER_BAD, false, "weather-signal-stale");
        }
        if (metadata != null && metadata.confidence() < properties.getWeather().getConfidenceThreshold()) {
            return signalScenario(ScenarioType.WEATHER_BAD, false, "weather-signal-low-confidence");
        }
        return signalScenario(ScenarioType.WEATHER_BAD, true, "weather-bad-signal-not-present");
    }

    private ScenarioGateDecision trafficSignalScenario(EtaContext etaContext,
                                                       FreshnessMetadata freshnessMetadata,
                                                       List<LiveStageMetadata> liveStageMetadata) {
        if (!etaContext.trafficBadSignal()) {
            return signalScenario(ScenarioType.TRAFFIC_BAD, false, "traffic-bad-signal-not-present");
        }
        LiveStageMetadata metadata = liveStageMetadata.stream()
                .filter(candidate -> candidate.stageName().equals("eta/context") && candidate.sourceName().equals("tomtom-traffic"))
                .findFirst()
                .orElse(null);
        if (fresnessFalseOrMissing(freshnessMetadata == null ? null : freshnessMetadata.trafficFresh())) {
            return signalScenario(ScenarioType.TRAFFIC_BAD, false, "traffic-signal-stale");
        }
        if (metadata != null && metadata.confidence() < properties.getTraffic().getConfidenceThreshold()) {
            return signalScenario(ScenarioType.TRAFFIC_BAD, false, "traffic-signal-low-confidence");
        }
        return signalScenario(ScenarioType.TRAFFIC_BAD, true, "traffic-bad-signal-not-present");
    }

    private ScenarioGateDecision signalScenario(ScenarioType scenario, boolean applied, String missingSignalReason) {
        return new ScenarioGateDecision(
                scenario,
                applied,
                applied ? List.of("eta-context-signal-present") : List.of(missingSignalReason),
                List.of());
    }

    private ScenarioGateDecision skippedForecastScenario(ScenarioType scenario) {
        return new ScenarioGateDecision(
                scenario,
                false,
                List.of("forecast-not-integrated-yet"),
                List.of("forecast-unavailable-scenario-skipped"));
    }

    private boolean fresnessFalseOrMissing(Boolean fresh) {
        return fresh == null || !fresh;
    }
}
