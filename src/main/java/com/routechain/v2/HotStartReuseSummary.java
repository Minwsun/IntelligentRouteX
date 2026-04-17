package com.routechain.v2;

import java.util.List;

public record HotStartReuseSummary(
        String schemaVersion,
        boolean reused,
        int reusedCount,
        List<String> degradeReasons) implements SchemaVersioned {

    public static HotStartReuseSummary none() {
        return new HotStartReuseSummary(
                "hot-start-reuse-summary/v1",
                false,
                0,
                List.of());
    }

    public static HotStartReuseSummary reused(int reusedCount) {
        return new HotStartReuseSummary(
                "hot-start-reuse-summary/v1",
                true,
                reusedCount,
                List.of());
    }

    public HotStartReuseSummary withDegradeReasons(List<String> reasons) {
        return new HotStartReuseSummary(
                schemaVersion,
                reused,
                reusedCount,
                List.copyOf(reasons));
    }
}
