package com.routechain.v2;

import java.util.List;

public final class DispatchV2Core {
    public DispatchV2Result dispatch(DispatchV2Request request) {
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                List.of(
                        "eta/context",
                        "order-buffer",
                        "pair-graph",
                        "micro-cluster",
                        "boundary-expansion",
                        "bundle-pool",
                        "pickup-anchor",
                        "driver-shortlist",
                        "route-proposal-pool",
                        "scenario-evaluation",
                        "global-selector",
                        "dispatch-executor",
                        "decision-log"));
    }
}

