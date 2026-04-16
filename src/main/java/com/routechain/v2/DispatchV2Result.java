package com.routechain.v2;

import java.util.List;

public record DispatchV2Result(
        String schemaVersion,
        String traceId,
        boolean fallbackUsed,
        String selectedRouteId,
        List<String> decisionStages) implements SchemaVersioned {

    public static DispatchV2Result fallback(String traceId) {
        return new DispatchV2Result(
                "dispatch-v2-result/v1",
                traceId,
                true,
                null,
                List.of("fallback-shell"));
    }
}

