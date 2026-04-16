package com.routechain.v2.integration;

public final class ChronosAdapter {
    private final DispatchV2ModelSidecarClient sidecarClient;

    public ChronosAdapter(DispatchV2ModelSidecarClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    public String backend() {
        return sidecarClient != null && sidecarClient.isEnabled()
                ? "dispatch-v2-sidecar:chronos2"
                : "dispatch-v2-local-fallback";
    }
}
