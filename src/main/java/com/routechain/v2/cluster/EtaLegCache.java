package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.EtaService;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public final class EtaLegCache {
    private final EtaService etaService;
    private final String traceId;
    private final Instant decisionTime;
    private final WeatherProfile weatherProfile;
    private final long timeoutBudgetMs;
    private final Map<String, EtaEstimate> cache = new HashMap<>();

    public EtaLegCache(EtaService etaService,
                       String traceId,
                       Instant decisionTime,
                       WeatherProfile weatherProfile,
                       long timeoutBudgetMs) {
        this.etaService = etaService;
        this.traceId = traceId;
        this.decisionTime = decisionTime;
        this.weatherProfile = weatherProfile;
        this.timeoutBudgetMs = timeoutBudgetMs;
    }

    public EtaEstimate getOrEstimate(GeoPoint from, GeoPoint to, String stageName, String legTraceSuffix) {
        String key = key(from, to, stageName);
        return cache.computeIfAbsent(key, ignored -> etaService.estimate(new EtaEstimateRequest(
                "eta-estimate-request/v1",
                traceId + ":" + legTraceSuffix,
                from,
                to,
                decisionTime,
                weatherProfile,
                stageName,
                timeoutBudgetMs)));
    }

    private String key(GeoPoint from, GeoPoint to, String stageName) {
        return "%s|%s,%s|%s,%s".formatted(
                stageName,
                from == null ? "null" : from.latitude(),
                from == null ? "null" : from.longitude(),
                to == null ? "null" : to.latitude(),
                to == null ? "null" : to.longitude());
    }
}
