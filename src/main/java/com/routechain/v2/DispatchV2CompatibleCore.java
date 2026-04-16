package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;

public final class DispatchV2CompatibleCore {
    private final RouteChainDispatchV2Properties properties;
    private final DispatchV2Core dispatchV2Core;

    public DispatchV2CompatibleCore(RouteChainDispatchV2Properties properties) {
        this.properties = properties == null ? RouteChainDispatchV2Properties.defaults() : properties;
        this.dispatchV2Core = new DispatchV2Core();
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        if (!properties.isEnabled()) {
            return DispatchV2Result.fallback(request.traceId());
        }
        return dispatchV2Core.dispatch(request);
    }

    public boolean isMlEnabled() {
        return properties.isMlEnabled();
    }

    public boolean isSidecarRequired() {
        return properties.isSidecarRequired();
    }
}

