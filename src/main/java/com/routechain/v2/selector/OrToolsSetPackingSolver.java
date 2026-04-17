package com.routechain.v2.selector;

import java.util.List;
import java.util.Optional;

public final class OrToolsSetPackingSolver implements SelectorSolver {
    @Override
    public SelectorSolverResult solve(List<SelectorCandidate> selectorCandidates, ConflictGraph conflictGraph) {
        return new SelectorSolverResult(Optional.empty(), List.of("selector-ortools-unavailable"));
    }
}
