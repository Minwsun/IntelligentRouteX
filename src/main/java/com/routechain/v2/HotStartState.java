package com.routechain.v2;

import java.util.List;

public record HotStartState(
        String schemaVersion,
        String previousTraceId,
        List<String> previousClusterSignatures,
        List<String> previousBundleSignatures,
        List<String> previousRouteProposalSignatures,
        List<String> previousSelectedProposalIds,
        boolean reuseEligible,
        List<String> degradeReasons) implements SchemaVersioned {

    public static HotStartState empty() {
        return new HotStartState(
                "hot-start-state/v1",
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                false,
                List.of());
    }
}
