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
        return gate(proposal, driverCandidate, context, etaContext, freshnessMetadata, liveStageMetadata, ForecastScenarioContext.empty());
    }

    public List<ScenarioGateDecision> gate(RouteProposal proposal,
                                           DriverCandidate driverCandidate,
                                           DispatchCandidateContext context,
                                           EtaContext etaContext,
                                           FreshnessMetadata freshnessMetadata,
                                           List<LiveStageMetadata> liveStageMetadata,
                                           ForecastScenarioContext forecastScenarioContext) {
        List<ScenarioGateDecision> decisions = new ArrayList<>();
        decisions.add(new ScenarioGateDecision(ScenarioType.NORMAL, true, List.of("baseline-scenario"), List.of()));
        decisions.add(weatherSignalScenario(etaContext, freshnessMetadata, liveStageMetadata));
        decisions.add(trafficSignalScenario(etaContext, freshnessMetadata, liveStageMetadata));
        decisions.add(forecastScenario(
                ScenarioType.DEMAND_SHIFT,
                forecastScenarioContext.demandShift(),
                properties.getScenario().getForecast().getDemandShiftThreshold(),
                "demand-shift-threshold-not-met"));
        decisions.add(forecastScenario(
                ScenarioType.ZONE_BURST,
                forecastScenarioContext.zoneBurst(),
                properties.getScenario().getForecast().getZoneBurstThreshold(),
                "zone-burst-threshold-not-met"));
        decisions.add(forecastScenario(
                ScenarioType.POST_DROP_SHIFT,
                forecastScenarioContext.postDropShift(),
                properties.getScenario().getForecast().getPostDropShiftThreshold(),
                "post-drop-shift-threshold-not-met"));
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

    private ScenarioGateDecision forecastScenario(ScenarioType scenario,
                                                  com.routechain.v2.integration.ForecastResult forecastResult,
                                                  double threshold,
                                                  String thresholdReason) {
        if (!forecastResult.applied()) {
            return new ScenarioGateDecision(
                    scenario,
                    false,
                    List.of(reasonForUnavailableForecast(forecastResult.degradeReason())),
                    List.of(forecastResult.degradeReason()));
        }
        if (forecastResult.sourceAgeMs() > properties.getContext().getFreshness().getForecastMaxAge().toMillis()) {
            return new ScenarioGateDecision(scenario, false, List.of("forecast-stale"), List.of("forecast-stale"));
        }
        if (forecastResult.confidence() < properties.getScenario().getForecast().getConfidenceThreshold()) {
            return new ScenarioGateDecision(scenario, false, List.of("forecast-low-confidence"), List.of("forecast-low-confidence"));
        }
        if (forecastResult.probability() < threshold) {
            return new ScenarioGateDecision(scenario, false, List.of(thresholdReason), List.of());
        }
        return new ScenarioGateDecision(
                scenario,
                true,
                List.of("forecast-signal-present"),
                List.of());
    }

    private boolean fresnessFalseOrMissing(Boolean fresh) {
        return fresh == null || !fresh;
    }

    private String reasonForUnavailableForecast(String degradeReason) {
        return switch (degradeReason) {
            case "forecast-stale" -> "forecast-stale";
            case "forecast-low-confidence" -> "forecast-low-confidence";
            default -> "forecast-unavailable";
        };
    }
}
