package com.routechain.v2.certification;

import com.routechain.v2.BootMode;
import com.routechain.v2.DispatchLatencyBudgetSummary;
import com.routechain.v2.DispatchStageLatency;
import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchHotStartCertificationReport(
        String schemaVersion,
        String traceFamilyId,
        String coldTraceId,
        String warmTraceId,
        String hotRunTraceId,
        BootMode warmBootMode,
        List<String> decisionStages,
        long coldTotalLatencyMs,
        long warmTotalLatencyMs,
        long hotTotalLatencyMs,
        long latencyDeltaWarmVsColdMs,
        long latencyDeltaHotVsColdMs,
        long estimatedSavedMs,
        boolean reuseEligible,
        boolean pairClusterReused,
        boolean bundlePoolReused,
        boolean routeProposalPoolReused,
        List<String> reusedStageNames,
        List<String> reuseFailureReasons,
        List<String> correctnessMismatchReasons,
        List<DispatchStageLatency> coldStageLatencies,
        List<DispatchStageLatency> hotStageLatencies,
        List<String> budgetBreachedStageNames,
        boolean totalBudgetBreached,
        boolean selectedProposalIdsMatched,
        boolean executedAssignmentIdsMatched,
        boolean selectedCountMatched,
        boolean executedAssignmentCountMatched,
        boolean conflictFreeAssignments,
        List<String> degradeReasons) implements SchemaVersioned {

    public DispatchLatencyBudgetSummary hotLatencyBudgetSummary() {
        return new DispatchLatencyBudgetSummary(
                "dispatch-latency-budget-summary/v1",
                hotTotalLatencyMs,
                0L,
                totalBudgetBreached,
                budgetBreachedStageNames,
                estimatedSavedMs);
    }
}
