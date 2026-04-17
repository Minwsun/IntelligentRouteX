package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.time.Instant;
import java.util.List;

public record DispatchRuntimeSnapshot(
        String schemaVersion,
        String snapshotId,
        String traceId,
        Instant createdAt,
        List<String> decisionStages,
        List<String> selectedProposalIds,
        List<String> executedAssignmentIds,
        List<String> clusterSignatures,
        List<String> bundleSignatures,
        List<String> routeProposalSignatures,
        double selectorObjectiveValue,
        List<String> degradeReasons) implements SchemaVersioned {
}
