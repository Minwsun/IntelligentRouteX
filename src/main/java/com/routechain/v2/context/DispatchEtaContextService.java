package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.EtaContext;
import com.routechain.v2.LiveStageMetadata;

import java.util.ArrayList;
import java.util.List;

public final class DispatchEtaContextService {
    private final RouteChainDispatchV2Properties properties;
    private final EtaService etaService;

    public DispatchEtaContextService(RouteChainDispatchV2Properties properties, EtaService etaService) {
        this.properties = properties;
        this.etaService = etaService;
    }

    public DispatchEtaContextStage evaluate(DispatchV2Request request) {
        long etaStartedAt = System.nanoTime();
        List<String> degradeReasons = new ArrayList<>();
        EtaEstimate estimate = sampleAndEstimate(request, degradeReasons);
        if (estimate == null) {
            FreshnessMetadata freshnessMetadata = new FreshnessMetadata(
                    "freshness-metadata/v1",
                    properties.getContext().getFreshness().getWeatherMaxAge().toMillis() + 1,
                    properties.getContext().getFreshness().getTrafficMaxAge().toMillis() + 1,
                    properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1,
                    false,
                    false,
                    false);
            return new DispatchEtaContextStage(
                    "dispatch-eta-context-stage/v1",
                    EtaContext.empty(request.traceId()),
                    new EtaStageTrace(
                            "eta-stage-trace/v1",
                            0.0,
                            1.0,
                            1.0,
                            false,
                            false,
                            false,
                            0.0,
                            0.0,
                            freshnessMetadata.weatherAgeMs(),
                            freshnessMetadata.trafficAgeMs(),
                            0.0,
                            List.copyOf(degradeReasons)),
                    freshnessMetadata,
                    List.of(DispatchStageLatency.measured("eta/context", elapsedMs(etaStartedAt), false)),
                    List.of(),
                    LiveStageMetadata.emptyList(),
                    List.copyOf(degradeReasons));
        }

        FreshnessMetadata freshnessMetadata = new FreshnessMetadata(
                "freshness-metadata/v1",
                estimate.weatherSourceAgeMs(),
                estimate.trafficSourceAgeMs(),
                properties.getContext().getFreshness().getForecastMaxAge().toMillis() + 1,
                estimate.weatherSourceAgeMs() <= properties.getContext().getFreshness().getWeatherMaxAge().toMillis(),
                estimate.trafficSourceAgeMs() <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis(),
                false);
        EtaContext etaContext = new EtaContext(
                "dispatch-eta-context/v1",
                request.traceId(),
                1,
                estimate.etaMinutes(),
                estimate.etaMinutes(),
                estimate.etaUncertainty(),
                estimate.trafficBadSignal(),
                estimate.weatherBadSignal(),
                estimate.corridorId(),
                estimate.refineSource());
        boolean liveWeatherApplied = estimate.liveStageMetadata().stream()
                .anyMatch(metadata -> metadata.stageName().equals("eta/context")
                        && metadata.sourceName().equals("open-meteo")
                        && metadata.applied());
        double weatherConfidence = estimate.liveStageMetadata().stream()
                .filter(metadata -> metadata.sourceName().equals("open-meteo"))
                .mapToDouble(LiveStageMetadata::confidence)
                .findFirst()
                .orElse(0.0);
        double trafficConfidence = estimate.liveStageMetadata().stream()
                .filter(metadata -> metadata.sourceName().equals("tomtom-traffic"))
                .mapToDouble(LiveStageMetadata::confidence)
                .findFirst()
                .orElse(estimate.trafficSourceAgeMs() <= properties.getContext().getFreshness().getTrafficMaxAge().toMillis() ? 0.95 : 0.4);
        EtaStageTrace etaStageTrace = new EtaStageTrace(
                "eta-stage-trace/v1",
                estimate.etaMinutes() / Math.max(estimate.trafficMultiplier() * estimate.weatherMultiplier(), 0.0001),
                estimate.trafficMultiplier(),
                estimate.weatherMultiplier(),
                liveWeatherApplied,
                "tomtom".equals(estimate.refineSource()),
                !estimate.degradeReasons().contains("eta-ml-disabled")
                        && !estimate.degradeReasons().contains("eta-ml-unavailable"),
                weatherConfidence,
                trafficConfidence,
                estimate.weatherSourceAgeMs(),
                estimate.trafficSourceAgeMs(),
                estimate.etaUncertainty(),
                estimate.degradeReasons());
        return new DispatchEtaContextStage(
                "dispatch-eta-context-stage/v1",
                etaContext,
                etaStageTrace,
                freshnessMetadata,
                List.of(DispatchStageLatency.measured("eta/context", elapsedMs(etaStartedAt), false)),
                estimate.mlStageMetadata(),
                estimate.liveStageMetadata(),
                estimate.degradeReasons());
    }

    public EtaContext buildDispatchContext(DispatchV2Request request) {
        return evaluate(request).etaContext();
    }

    private EtaEstimate sampleAndEstimate(DispatchV2Request request, List<String> degradeReasons) {
        if (request.availableDrivers() != null && !request.availableDrivers().isEmpty()
                && request.openOrders() != null && !request.openOrders().isEmpty()) {
            Driver driver = request.availableDrivers().getFirst();
            Order order = request.openOrders().getFirst();
            return etaService.estimate(new EtaEstimateRequest(
                    "eta-estimate-request/v1",
                    request.traceId(),
                    driver.currentLocation(),
                    order.pickupPoint(),
                    request.decisionTime(),
                    request.weatherProfile(),
                    "eta/context",
                    properties.getContext().getTimeouts().getEtaMlTimeout().toMillis()));
        }
        if (request.openOrders() != null && !request.openOrders().isEmpty()) {
            Order order = request.openOrders().getFirst();
            return etaService.estimate(new EtaEstimateRequest(
                    "eta-estimate-request/v1",
                    request.traceId(),
                    order.pickupPoint(),
                    order.dropoffPoint(),
                    request.decisionTime(),
                    request.weatherProfile(),
                    "eta/context",
                    properties.getContext().getTimeouts().getEtaMlTimeout().toMillis()));
        }
        degradeReasons.add("no-sampleable-eta-leg");
        return null;
    }

    private long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }
}
