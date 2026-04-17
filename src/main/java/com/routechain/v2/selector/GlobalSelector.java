package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;

import java.util.ArrayList;
import java.util.List;

public final class GlobalSelector {
    private final RouteChainDispatchV2Properties properties;
    private final GreedyRepairSelector greedyRepairSelector;
    private final SelectorSolver selectorSolver;

    public GlobalSelector(RouteChainDispatchV2Properties properties,
                          GreedyRepairSelector greedyRepairSelector,
                          SelectorSolver selectorSolver) {
        this.properties = properties;
        this.greedyRepairSelector = greedyRepairSelector;
        this.selectorSolver = selectorSolver;
    }

    public SelectorSelectionOutcome select(List<SelectorCandidateEnvelope> candidateEnvelopes,
                                           ConflictGraph conflictGraph) {
        boolean repairEnabled = properties.getSelector().isGreedyRepairEnabled()
                && properties.getSelector().getRepairPassLimit() > 0;
        if (!properties.isSelectorOrtoolsEnabled()) {
            return greedyRepairSelector.select(candidateEnvelopes, conflictGraph, SelectionSolverMode.GREEDY_REPAIR, repairEnabled);
        }

        SelectorSolverResult solverResult = selectorSolver.solve(
                candidateEnvelopes.stream().map(SelectorCandidateEnvelope::candidate).toList(),
                conflictGraph);
        if (solverResult.selectionResult().isPresent()) {
            GlobalSelectionResult solverSelection = solverResult.selectionResult().get();
            return new SelectorSelectionOutcome(
                    withDegradeReasons(solverSelection, solverResult.degradeReasons()),
                    SelectorDecisionTrace.empty());
        }

        SelectorSelectionOutcome degradedOutcome = greedyRepairSelector.select(
                candidateEnvelopes,
                conflictGraph,
                SelectionSolverMode.DEGRADED_GREEDY,
                repairEnabled);
        return new SelectorSelectionOutcome(
                withDegradeReasons(degradedOutcome.selectionResult(), solverResult.degradeReasons()),
                degradedOutcome.decisionTrace());
    }

    private GlobalSelectionResult withDegradeReasons(GlobalSelectionResult selectionResult, List<String> extraDegradeReasons) {
        List<String> degradeReasons = new ArrayList<>(selectionResult.degradeReasons());
        degradeReasons.addAll(extraDegradeReasons);
        return new GlobalSelectionResult(
                selectionResult.schemaVersion(),
                selectionResult.selectedProposals(),
                selectionResult.retainedCandidateCount(),
                selectionResult.selectedCount(),
                selectionResult.solverMode(),
                selectionResult.objectiveValue(),
                degradeReasons.stream().distinct().toList());
    }
}
