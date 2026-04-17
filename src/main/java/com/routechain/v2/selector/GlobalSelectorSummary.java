package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record GlobalSelectorSummary(
        String schemaVersion,
        int candidateCount,
        int feasibleCandidateCount,
        int conflictEdgeCount,
        int selectedCount,
        SelectionSolverMode solverMode,
        List<String> degradeReasons) implements SchemaVersioned {

    public static GlobalSelectorSummary empty() {
        return new GlobalSelectorSummary(
                "global-selector-summary/v1",
                0,
                0,
                0,
                0,
                SelectionSolverMode.GREEDY_REPAIR,
                List.of());
    }
}
