package com.routechain.v2.decision;

import com.routechain.v2.SchemaVersioned;

public record DecisionUsageRecord(
        String schemaVersion,
        String traceId,
        String runId,
        String tickId,
        DecisionStageName stageName,
        DecisionBrainType requestedBrainType,
        DecisionBrainType appliedBrainType,
        boolean fallbackUsed,
        String fallbackReason,
        String provider,
        String model) implements SchemaVersioned {
}
