package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.integration.MlStageMetadataAccumulator;
import com.routechain.v2.integration.TabularScoreResult;
import com.routechain.v2.integration.TabularScoringClient;
import com.routechain.v2.integration.TomTomTrafficRefineClient;
import com.routechain.v2.integration.TomTomTrafficRefineResult;

import java.util.ArrayList;
import java.util.List;

public final class EtaService {
    private final RouteChainDispatchV2Properties properties;
    private final BaselineTravelTimeEstimator baselineTravelTimeEstimator;
    private final TrafficProfileService trafficProfileService;
    private final WeatherContextService weatherContextService;
    private final TomTomTrafficRefineClient tomTomTrafficRefineClient;
    private final TabularScoringClient tabularScoringClient;
    private final EtaFeatureBuilder etaFeatureBuilder;
    private final EtaUncertaintyEstimator etaUncertaintyEstimator;
    private final LiveTrafficSelectionPolicy liveTrafficSelectionPolicy;
    private final TrafficConfidenceEvaluator trafficConfidenceEvaluator;

    public EtaService(RouteChainDispatchV2Properties properties,
                      BaselineTravelTimeEstimator baselineTravelTimeEstimator,
                      TrafficProfileService trafficProfileService,
                      WeatherContextService weatherContextService,
                      TomTomTrafficRefineClient tomTomTrafficRefineClient,
                      TabularScoringClient tabularScoringClient,
                      EtaFeatureBuilder etaFeatureBuilder,
                      EtaUncertaintyEstimator etaUncertaintyEstimator) {
        this.properties = properties;
        this.baselineTravelTimeEstimator = baselineTravelTimeEstimator;
        this.trafficProfileService = trafficProfileService;
        this.weatherContextService = weatherContextService;
        this.tomTomTrafficRefineClient = tomTomTrafficRefineClient;
        this.tabularScoringClient = tabularScoringClient;
        this.etaFeatureBuilder = etaFeatureBuilder;
        this.etaUncertaintyEstimator = etaUncertaintyEstimator;
        this.liveTrafficSelectionPolicy = new LiveTrafficSelectionPolicy(properties);
        this.trafficConfidenceEvaluator = new TrafficConfidenceEvaluator(properties);
    }

    public EtaEstimate estimate(EtaEstimateRequest request) {
        List<String> degradeReasons = new ArrayList<>();
        DispatchV2Request bridgeRequest = new DispatchV2Request(
                "dispatch-v2-request/v1",
                request.traceId(),
                List.of(),
                List.of(),
                List.of(),
                request.weatherProfile(),
                request.decisionTime());

        double baselineMinutes = baselineTravelTimeEstimator.estimateMinutes(
                request.from(),
                request.to(),
                properties.getContext().getBaselineSpeedKph());
        double distanceKm = baselineTravelTimeEstimator.distanceKm(request.from(), request.to());

        TrafficProfileSnapshot traffic = trafficProfileService.resolveTraffic(bridgeRequest, request.from(), request.to());
        WeatherContextSnapshot weather = weatherContextService.resolveWeather(bridgeRequest, request.to());
        boolean weatherFresh = weather.sourceAgeMs() <= properties.getContext().getFreshness().getWeatherMaxAge().toMillis();
        boolean trafficFresh = traffic.sourceAgeMs() <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis();

        double adjustedMinutes = baselineMinutes * traffic.multiplier() * weather.multiplier();
        boolean liveRefineApplied = false;
        String refineSource = weather.source() == WeatherSource.OPEN_METEO ? "baseline-profile-live-weather" : "baseline-profile-weather";
        double effectiveTrafficConfidence = traffic.confidence();
        long effectiveTrafficAgeMs = traffic.sourceAgeMs();
        boolean trafficBadSignal = traffic.trafficBadSignal();
        List<LiveStageMetadata> liveStageMetadata = new ArrayList<>();

        liveStageMetadata.add(new LiveStageMetadata(
                "live-stage-metadata/v1",
                "eta/context",
                "open-meteo",
                weather.source() == WeatherSource.OPEN_METEO,
                weather.source() != WeatherSource.OPEN_METEO,
                weather.sourceAgeMs(),
                weather.confidence(),
                weather.latencyMs(),
                weather.degradeReason()));

        if (!properties.isTomtomEnabled() || !properties.getTraffic().isEnabled()) {
            degradeReasons.add("tomtom-disabled");
            liveStageMetadata.add(new LiveStageMetadata(
                    "live-stage-metadata/v1",
                    "eta/context",
                    "tomtom-traffic",
                    false,
                    true,
                    traffic.sourceAgeMs(),
                    traffic.confidence(),
                    0L,
                    "tomtom-disabled"));
        } else if (!liveTrafficSelectionPolicy.shouldRefine(request, baselineMinutes, distanceKm)) {
            degradeReasons.add("tomtom-budget-or-policy-skipped");
            liveStageMetadata.add(new LiveStageMetadata(
                    "live-stage-metadata/v1",
                    "eta/context",
                    "tomtom-traffic",
                    false,
                    true,
                    traffic.sourceAgeMs(),
                    traffic.confidence(),
                    0L,
                    "tomtom-budget-or-policy-skipped"));
        } else {
            TomTomTrafficRefineResult refineResult = tomTomTrafficRefineClient.refine(request, baselineMinutes, distanceKm);
            boolean liveTrafficFresh = refineResult.sourceAgeMs() <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis();
            double liveTrafficConfidence = trafficConfidenceEvaluator.effectiveConfidence(refineResult.confidence(), liveTrafficFresh);
            if (refineResult.applied() && trafficConfidenceEvaluator.passesThreshold(liveTrafficConfidence) && liveTrafficFresh) {
                adjustedMinutes *= refineResult.multiplier();
                liveRefineApplied = true;
                refineSource = "tomtom";
                effectiveTrafficConfidence = liveTrafficConfidence;
                effectiveTrafficAgeMs = refineResult.sourceAgeMs();
                trafficBadSignal = refineResult.trafficBadSignal();
            } else {
                degradeReasons.add(refineResult.degradeReason().isBlank() ? "tomtom-unavailable-or-no-data" : refineResult.degradeReason());
            }
            liveStageMetadata.add(new LiveStageMetadata(
                    "live-stage-metadata/v1",
                    "eta/context",
                    "tomtom-traffic",
                    liveRefineApplied,
                    !liveRefineApplied,
                    refineResult.sourceAgeMs(),
                    liveTrafficConfidence,
                    refineResult.latencyMs(),
                    refineResult.degradeReason()));
        }
        FreshnessMetadata freshnessMetadata = new FreshnessMetadata(
                "freshness-metadata/v1",
                weather.sourceAgeMs(),
                effectiveTrafficAgeMs,
                properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1,
                weatherFresh,
                effectiveTrafficAgeMs <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis(),
                false);

        EtaFeatureVector featureVector = etaFeatureBuilder.build(
                bridgeRequest,
                request.from(),
                request.to(),
                traffic,
                weather,
                baselineMinutes,
                distanceKm);

        boolean mlResidualApplied = false;
        MlStageMetadataAccumulator mlStageMetadataAccumulator = new MlStageMetadataAccumulator("eta/context");
        if (!properties.isMlEnabled()) {
            degradeReasons.add("eta-ml-disabled");
        } else if (!properties.getMl().getTabular().isEnabled()) {
            degradeReasons.add("eta-ml-disabled");
        } else {
            TabularScoreResult scoreResult = tabularScoringClient.scoreEtaResidual(featureVector, request.timeoutBudgetMs());
            mlStageMetadataAccumulator.accept(scoreResult);
            if (scoreResult.applied()) {
                adjustedMinutes += scoreResult.value();
                mlResidualApplied = true;
            } else {
                degradeReasons.add("eta-ml-unavailable");
            }
        }

        double uncertainty = etaUncertaintyEstimator.estimate(
                traffic,
                weather,
                liveRefineApplied,
                mlResidualApplied,
                freshnessMetadata);

        return new EtaEstimate(
                "eta-estimate/v1",
                request.traceId(),
                adjustedMinutes,
                uncertainty,
                liveRefineApplied ? traffic.multiplier() * adjustedMinutes / Math.max(0.0001, baselineMinutes * weather.multiplier()) : traffic.multiplier(),
                weather.multiplier(),
                trafficBadSignal,
                weather.weatherBadSignal(),
                corridorId(request),
                refineSource,
                effectiveTrafficAgeMs,
                weather.sourceAgeMs(),
                mlStageMetadataAccumulator.build().map(List::of).orElse(List.of()),
                List.copyOf(liveStageMetadata),
                List.copyOf(degradeReasons));
    }

    private String corridorId(EtaEstimateRequest request) {
        if (request.from() == null || request.to() == null) {
            return "unknown";
        }
        return "corridor-%d-%d".formatted(
                Math.round(request.from().latitude() + request.to().latitude()),
                Math.round(request.from().longitude() + request.to().longitude()));
    }
}
