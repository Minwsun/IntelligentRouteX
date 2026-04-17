package com.routechain.v2.selector;

import java.util.List;

public interface SelectorSolver {
    SelectorSolverResult solve(List<SelectorCandidate> selectorCandidates, ConflictGraph conflictGraph);
}
