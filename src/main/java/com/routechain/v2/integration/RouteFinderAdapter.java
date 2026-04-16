package com.routechain.v2.integration;

public final class RouteFinderAdapter {
    private final DispatchV2ModelSidecarClient sidecarClient;

    public RouteFinderAdapter(DispatchV2ModelSidecarClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    public String backend() {
        return sidecarClient != null && sidecarClient.isEnabled()
                ? "dispatch-v2-sidecar:routefinder"
                : "dispatch-v2-local-fallback";
    }
}
