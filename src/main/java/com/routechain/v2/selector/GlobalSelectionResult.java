package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record GlobalSelectionResult(
        String schemaVersion,
        List<SelectedProposal> selectedProposals,
        int retainedCandidateCount,
        int selectedCount,
        SelectionSolverMode solverMode,
        double objectiveValue,
        List<String> degradeReasons) implements SchemaVersioned {

    public static GlobalSelectionResult empty() {
        return new GlobalSelectionResult(
                "global-selection-result/v1",
                List.of(),
                0,
                0,
                SelectionSolverMode.GREEDY_REPAIR,
                0.0,
                List.of());
    }
}
