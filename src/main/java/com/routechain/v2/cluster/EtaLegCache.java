package com.routechain.v2.cluster;

import com.routechain.domain.GeoPoint;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.context.EtaEstimate;
import com.routechain.v2.context.EtaEstimateRequest;
import com.routechain.v2.context.EtaService;

import java.util.HashMap;
import java.util.Map;

public final class EtaLegCache {
    private final EtaService etaService;
    private final DispatchV2Request request;
    private final long timeoutBudgetMs;
    private final Map<String, EtaEstimate> cache = new HashMap<>();

    public EtaLegCache(EtaService etaService, DispatchV2Request request, long timeoutBudgetMs) {
        this.etaService = etaService;
        this.request = request;
        this.timeoutBudgetMs = timeoutBudgetMs;
    }

    public EtaEstimate getOrEstimate(GeoPoint from, GeoPoint to, String stageName, String legTraceSuffix) {
        String key = key(from, to, stageName);
        return cache.computeIfAbsent(key, ignored -> etaService.estimate(new EtaEstimateRequest(
                "eta-estimate-request/v1",
                request.traceId() + ":" + legTraceSuffix,
                from,
                to,
                request.decisionTime(),
                request.weatherProfile(),
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

