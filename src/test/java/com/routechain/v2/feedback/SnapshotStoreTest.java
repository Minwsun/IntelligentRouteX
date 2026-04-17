package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotStoreTest {

    @Test
    void savesAndLoadsLatestSnapshot() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        SnapshotService snapshotService = new SnapshotService(
                RouteChainDispatchV2Properties.defaults(),
                new SnapshotBuilder(),
                snapshotStore);
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults()).dispatch(request);

        SnapshotWriteResult writeResult = snapshotService.save(request, result);
        SnapshotLoadResult loadResult = snapshotService.loadLatest();

        assertTrue(writeResult.written());
        assertTrue(loadResult.loaded());
        assertEquals(writeResult.snapshotId(), loadResult.snapshot().snapshotId());
    }

    @Test
    void rejectsUnsupportedSnapshotVersionWithDegradeReason() {
        InMemorySnapshotStore snapshotStore = new InMemorySnapshotStore();
        snapshotStore.save(new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/legacy",
                "snapshot-legacy",
                "trace-legacy",
                Instant.parse("2026-04-16T12:00:00Z"),
                List.of("eta/context"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0.0,
                List.of()));
        SnapshotService snapshotService = new SnapshotService(
                RouteChainDispatchV2Properties.defaults(),
                new SnapshotBuilder(),
                snapshotStore);

        SnapshotLoadResult loadResult = snapshotService.loadLatest();

        assertFalse(loadResult.loaded());
        assertTrue(loadResult.degradeReasons().contains("snapshot-schema-version-mismatch"));
    }
}
