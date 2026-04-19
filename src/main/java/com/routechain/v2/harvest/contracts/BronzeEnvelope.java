package com.routechain.v2.harvest.contracts;

import java.time.Instant;

public record BronzeEnvelope(
        String schemaVersion,
        String recordFamily,
        String runId,
        String traceId,
        Instant emittedAt,
        String decisionStage,
        String policyVersion,
        String runtimeProfile,
        String sourceCommit,
        HarvestMode harvestMode) {
}
