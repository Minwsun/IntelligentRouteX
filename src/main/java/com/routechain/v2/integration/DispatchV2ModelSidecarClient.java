package com.routechain.v2.integration;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.net.http.HttpClient;
import java.time.Duration;

public final class DispatchV2ModelSidecarClient {
    private final RouteChainDispatchV2Properties.Sidecar properties;
    private final HttpClient httpClient;

    public DispatchV2ModelSidecarClient(RouteChainDispatchV2Properties.Sidecar properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties == null ? Duration.ofMillis(400) : properties.getConnectTimeout())
                .build();
    }

    public boolean isEnabled() {
        return properties != null && properties.isEnabled();
    }

    public String baseUrl() {
        return properties == null ? "" : properties.getBaseUrl();
    }

    public HttpClient httpClient() {
        return httpClient;
    }
}
