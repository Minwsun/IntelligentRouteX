package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.BootMode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WarmStartManagerTest {

    @Test
    void validLatestSnapshotBootsWarm() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        snapshotStore.save(new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/v1",
                "snapshot-1",
                "trace-1",
                Instant.parse("2026-04-16T12:00:00Z"),
                List.of("eta/context"),
                List.of(),
                List.of(),
                List.of("cluster"),
                List.of("bundle"),
                List.of("proposal"),
                1.0,
                List.of()));

        WarmStartManager warmStartManager = new WarmStartManager(
                properties,
                new SnapshotService(properties, new SnapshotBuilder(), snapshotStore));

        assertEquals(BootMode.WARM, warmStartManager.currentState().bootMode());
        assertTrue(warmStartManager.currentState().snapshotLoaded());
    }

    @Test
    void missingOrInvalidSnapshotBootsColdWithReason() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        WarmStartManager missingWarmStartManager = new WarmStartManager(
                properties,
                new SnapshotService(properties, new SnapshotBuilder(), new InMemorySnapshotStore()));
        assertEquals(BootMode.COLD, missingWarmStartManager.currentState().bootMode());
        assertTrue(missingWarmStartManager.currentState().degradeReasons().contains("snapshot-not-found"));

        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        snapshotStore.save(new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/legacy",
                "snapshot-legacy",
                "trace-legacy",
                Instant.parse("2026-04-16T12:00:00Z"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0.0,
                List.of()));
        WarmStartManager invalidWarmStartManager = new WarmStartManager(
                properties,
                new SnapshotService(properties, new SnapshotBuilder(), snapshotStore));
        assertEquals(BootMode.COLD, invalidWarmStartManager.currentState().bootMode());
        assertTrue(invalidWarmStartManager.currentState().degradeReasons().contains("snapshot-schema-version-mismatch"));
    }
}
