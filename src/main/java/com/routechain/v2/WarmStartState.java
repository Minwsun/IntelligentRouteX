package com.routechain.v2;

import java.util.List;

public record WarmStartState(
        String schemaVersion,
        BootMode bootMode,
        String snapshotId,
        boolean snapshotLoaded,
        String loadedTraceId,
        List<String> degradeReasons) implements SchemaVersioned {

    public static WarmStartState cold(List<String> degradeReasons) {
        return new WarmStartState(
                "warm-start-state/v1",
                BootMode.COLD,
                null,
                false,
                null,
                List.copyOf(degradeReasons));
    }

    public static WarmStartState empty() {
        return cold(List.of());
    }
}
