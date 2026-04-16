package com.routechain.v2.integration;

public final class GreedRLAdapter {
    private final DispatchV2ModelSidecarClient sidecarClient;

    public GreedRLAdapter(DispatchV2ModelSidecarClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    public String backend() {
        return sidecarClient != null && sidecarClient.isEnabled()
                ? "dispatch-v2-sidecar:greedrl"
                : "dispatch-v2-local-fallback";
    }
}
