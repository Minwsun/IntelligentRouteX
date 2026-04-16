package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
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
        FreshnessMetadata freshnessMetadata = new FreshnessMetadata(
                "freshness-metadata/v1",
                weather.sourceAgeMs(),
                traffic.sourceAgeMs(),
                properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1,
                weather.sourceAgeMs() <= properties.getContext().getFreshness().getWeatherMaxAge().toMillis(),
                traffic.sourceAgeMs() <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis(),
                false);

        double adjustedMinutes = baselineMinutes * traffic.multiplier() * weather.multiplier();
        boolean liveRefineApplied = false;
        String refineSource = "baseline-profile-weather";

        if (!properties.isTomtomEnabled()) {
            degradeReasons.add("tomtom-disabled");
        } else {
            TomTomTrafficRefineResult refineResult = tomTomTrafficRefineClient.refine(request);
            if (refineResult.applied()) {
                adjustedMinutes *= refineResult.multiplier();
                liveRefineApplied = true;
                refineSource = "tomtom";
            } else {
                degradeReasons.add("tomtom-unavailable-or-no-data");
            }
        }

        EtaFeatureVector featureVector = etaFeatureBuilder.build(
                bridgeRequest,
                request.from(),
                request.to(),
                traffic,
                weather,
                baselineMinutes,
                distanceKm);

        boolean mlResidualApplied = false;
        if (!properties.isMlEnabled()) {
            degradeReasons.add("eta-ml-disabled");
        } else {
            TabularScoreResult scoreResult = tabularScoringClient.scoreEtaResidual(featureVector, request.timeoutBudgetMs());
            if (scoreResult.applied()) {
                adjustedMinutes += scoreResult.value();
                mlResidualApplied = true;
            } else {
                degradeReasons.add("eta-ml-unavailable-or-disabled-path");
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
                traffic.multiplier(),
                weather.multiplier(),
                traffic.trafficBadSignal(),
                weather.weatherBadSignal(),
                corridorId(request),
                refineSource,
                traffic.sourceAgeMs(),
                weather.sourceAgeMs(),
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

