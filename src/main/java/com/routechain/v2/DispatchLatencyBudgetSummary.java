package com.routechain.v2;

import java.util.List;

public record DispatchLatencyBudgetSummary(
        String schemaVersion,
        long totalDispatchLatencyMs,
        long totalDispatchBudgetMs,
        boolean totalBudgetBreached,
        List<String> breachedStageNames,
        long estimatedHotStartSavedMs) implements SchemaVersioned {

    public static DispatchLatencyBudgetSummary empty() {
        return new DispatchLatencyBudgetSummary(
                "dispatch-latency-budget-summary/v1",
                0L,
                0L,
                false,
                List.of(),
                0L);
    }
}
