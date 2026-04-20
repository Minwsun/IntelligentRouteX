package com.routechain.v2.benchmark;

import com.routechain.v2.SchemaVersioned;

public record DispatchTokenUsageSummary(
        String schemaVersion,
        int requestCount,
        long inputTokens,
        long outputTokens,
        long totalTokens) implements SchemaVersioned {

    public static DispatchTokenUsageSummary empty() {
        return new DispatchTokenUsageSummary(
                "dispatch-token-usage-summary/v1",
                0,
                0L,
                0L,
                0L);
    }
}
