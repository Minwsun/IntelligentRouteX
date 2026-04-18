package com.routechain.v2;

public record DispatchStageLatency(
        String schemaVersion,
        String stageName,
        long elapsedMs,
        long budgetMs,
        boolean budgetBreached,
        boolean hotStartReused,
        long estimatedSavedMs) implements SchemaVersioned {

    public static DispatchStageLatency measured(String stageName,
                                                long elapsedMs,
                                                boolean hotStartReused) {
        return new DispatchStageLatency(
                "dispatch-stage-latency/v1",
                stageName,
                elapsedMs,
                0L,
                false,
                hotStartReused,
                0L);
    }

    public DispatchStageLatency withBudget(long newBudgetMs, boolean newBudgetBreached) {
        return new DispatchStageLatency(
                schemaVersion,
                stageName,
                elapsedMs,
                newBudgetMs,
                newBudgetBreached,
                hotStartReused,
                estimatedSavedMs);
    }

    public DispatchStageLatency withEstimatedSavedMs(long newEstimatedSavedMs) {
        return new DispatchStageLatency(
                schemaVersion,
                stageName,
                elapsedMs,
                budgetMs,
                budgetBreached,
                hotStartReused,
                newEstimatedSavedMs);
    }
}
