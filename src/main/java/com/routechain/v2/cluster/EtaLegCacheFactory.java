package com.routechain.v2.cluster;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.context.EtaService;

public final class EtaLegCacheFactory {
    private final RouteChainDispatchV2Properties properties;
    private final EtaService etaService;

    public EtaLegCacheFactory(RouteChainDispatchV2Properties properties, EtaService etaService) {
        this.properties = properties;
        this.etaService = etaService;
    }

    public EtaLegCache create(DispatchV2Request request) {
        return new EtaLegCache(etaService, request, properties.getPair().getMlTimeout().toMillis());
    }
}
