package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.WeatherProfile;
import com.routechain.v2.context.EtaService;

import java.time.Instant;

public final class EtaLegCacheFactory {
    private final RouteChainDispatchV2Properties properties;
    private final EtaService etaService;

    public EtaLegCacheFactory(RouteChainDispatchV2Properties properties, EtaService etaService) {
        this.properties = properties;
        this.etaService = etaService;
    }

    public EtaLegCache create(String traceId, Instant decisionTime, WeatherProfile weatherProfile) {
        return new EtaLegCache(
                etaService,
                traceId,
                decisionTime,
                weatherProfile,
                properties.getPair().getMlTimeout().toMillis());
    }
}
