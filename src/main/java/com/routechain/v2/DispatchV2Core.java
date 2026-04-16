package com.routechain.v2;

import com.routechain.v2.context.DispatchEtaContextService;
import com.routechain.v2.context.DispatchEtaContextStage;

public final class DispatchV2Core {
    private final DispatchEtaContextService dispatchEtaContextService;

    public DispatchV2Core(DispatchEtaContextService dispatchEtaContextService) {
        this.dispatchEtaContextService = dispatchEtaContextService;
    }

    public DispatchV2Result dispatch(DispatchV2Request request) {
        DispatchEtaContextStage stage = dispatchEtaContextService.evaluate(request);
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                request.traceId(),
                false,
                null,
                java.util.List.of("eta/context"),
                stage.etaContext(),
                stage.etaStageTrace(),
                stage.freshnessMetadata(),
                stage.degradeReasons());
    }
}
