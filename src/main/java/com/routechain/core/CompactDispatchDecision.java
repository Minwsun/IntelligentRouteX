package com.routechain.core;

import com.routechain.simulation.DispatchPlan;

import java.util.List;

public record CompactDispatchDecision(
        List<DispatchPlan> plans,
        List<CompactDecisionExplanation> explanations,
        List<CompactSelectedPlanEvidence> selectedPlanEvidence,
        List<CompactSelectionAudit> selectionAudits,
        WeightSnapshot weightSnapshotBefore,
        long dispatchDecisionLatencyMs) {
}
