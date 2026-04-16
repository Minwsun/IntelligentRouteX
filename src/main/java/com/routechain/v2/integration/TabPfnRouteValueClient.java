package com.routechain.v2.integration;

public final class TabPfnRouteValueClient {
    private final DispatchV2ModelSidecarClient sidecarClient;

    public TabPfnRouteValueClient(DispatchV2ModelSidecarClient sidecarClient) {
        this.sidecarClient = sidecarClient;
    }

    public String backend() {
        return sidecarClient != null && sidecarClient.isEnabled()
                ? "dispatch-v2-sidecar:tabpfn-v2"
                : "dispatch-v2-local-surrogate";
    }
}
