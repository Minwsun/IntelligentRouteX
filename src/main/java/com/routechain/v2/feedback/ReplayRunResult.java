package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ReplayRunResult(
        String schemaVersion,
        ReplayRequestRecord replayRequestRecord,
        DecisionLogRecord referenceDecisionLog,
        SnapshotManifest referenceSnapshotManifest,
        ReplayComparisonResult comparisonResult,
        List<String> replayDecisionStages,
        List<String> replaySelectedProposalIds,
        List<String> replayExecutedAssignmentIds,
        int replaySelectedCount,
        int replayExecutedAssignmentCount,
        List<String> degradeReasons) implements SchemaVersioned {
}
