package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.DispatchStageLatency;

import java.util.List;

public record DispatchSelectorStage(
        String schemaVersion,
        List<SelectorCandidate> selectorCandidates,
        ConflictGraph conflictGraph,
        GlobalSelectionResult globalSelectionResult,
        GlobalSelectorSummary globalSelectorSummary,
        List<DispatchStageLatency> stageLatencies,
        List<String> degradeReasons) implements SchemaVersioned {
}
