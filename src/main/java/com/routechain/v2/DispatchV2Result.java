package com.routechain.v2;

import com.routechain.v2.context.EtaStageTrace;
import com.routechain.v2.context.FreshnessMetadata;

import java.util.List;

public record DispatchV2Result(
        String schemaVersion,
        String traceId,
        boolean fallbackUsed,
        String selectedRouteId,
        List<String> decisionStages,
        EtaContext etaContext,
        EtaStageTrace etaStageTrace,
        FreshnessMetadata freshnessMetadata,
        List<String> degradeReasons) implements SchemaVersioned {

    public static DispatchV2Result fallback(String traceId) {
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                traceId,
                true,
                null,
                List.of("fallback-shell"),
                EtaContext.empty(traceId),
                EtaStageTrace.empty(),
                FreshnessMetadata.empty(),
                List.of("dispatch-v2-disabled"));
    }
}
