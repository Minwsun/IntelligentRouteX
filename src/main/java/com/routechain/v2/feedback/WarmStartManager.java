package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.BootMode;
import com.routechain.v2.WarmStartState;

import java.util.List;

public final class WarmStartManager {
    private final WarmStartState currentState;

    public WarmStartManager(RouteChainDispatchV2Properties properties, SnapshotService snapshotService) {
        if (!properties.isWarmStartEnabled()) {
            currentState = WarmStartState.cold(List.of("warm-start-disabled"));
            return;
        }
        if (!properties.getWarmHotStart().isLoadLatestSnapshotOnBoot()) {
            currentState = WarmStartState.cold(List.of("warm-start-load-on-boot-disabled"));
            return;
        }
        SnapshotLoadResult snapshotLoadResult = snapshotService.loadLatest();
        if (snapshotLoadResult.loaded() && snapshotLoadResult.snapshot() != null) {
            currentState = new WarmStartState(
                    "warm-start-state/v1",
                    BootMode.WARM,
                    snapshotLoadResult.snapshot().snapshotId(),
                    true,
                    snapshotLoadResult.snapshot().traceId(),
                    List.of());
            return;
        }
        currentState = WarmStartState.cold(snapshotLoadResult.degradeReasons());
    }

    public WarmStartState currentState() {
        return currentState;
    }
}
