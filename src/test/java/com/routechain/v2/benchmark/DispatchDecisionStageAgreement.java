package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

public record DispatchDecisionStageAgreement(
        String schemaVersion,
        String stageName,
        boolean agreementAvailable,
        int comparisonCount,
        int exactMatchCount,
        double exactMatchRate) implements SchemaVersioned {
}
