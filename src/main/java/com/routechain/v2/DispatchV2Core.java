package com.routechain.v2;

import com.routechain.v2.cluster.DispatchPairClusterService;
import com.routechain.v2.cluster.DispatchPairClusterStage;
import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.DispatchEtaContextStage;

public final class DispatchV2Core {
    private final DispatchEtaContextService dispatchEtaContextService;
    private final DispatchPairClusterService dispatchPairClusterService;

    public DispatchV2Core(DispatchEtaContextService dispatchEtaContextService,
                          DispatchPairClusterService dispatchPairClusterService) {
        this.dispatchEtaContextService = dispatchEtaContextService;
        this.dispatchPairClusterService = dispatchPairClusterService;
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        DispatchEtaContextStage etaStage = dispatchEtaContextService.evaluate(request);
        DispatchPairClusterStage pairClusterStage = dispatchPairClusterService.evaluate(request, etaStage.etaContext());
        java.util.List<String> degradeReasons = java.util.stream.Stream.concat(
                        etaStage.degradeReasons().stream(),
                        pairClusterStage.degradeReasons().stream())
                .distinct()
                .toList();
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                java.util.List.of("eta/context", "order-buffer", "pair-graph", "micro-cluster"),
                etaStage.etaContext(),
                etaStage.etaStageTrace(),
                etaStage.freshnessMetadata(),
                pairClusterStage.bufferedOrderWindow(),
                pairClusterStage.pairGraphSummary(),
                pairClusterStage.microClusters(),
                pairClusterStage.microClusterSummary(),
                degradeReasons);
    }
}
