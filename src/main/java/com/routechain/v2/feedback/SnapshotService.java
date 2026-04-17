package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;

import java.util.List;

public final class SnapshotService {
    private final RouteChainDispatchV2Properties properties;
    private final SnapshotBuilder snapshotBuilder;
    private final SnapshotStore snapshotStore;

    public SnapshotService(RouteChainDispatchV2Properties properties,
                           SnapshotBuilder snapshotBuilder,
                           SnapshotStore snapshotStore) {
        this.properties = properties;
        this.snapshotBuilder = snapshotBuilder;
        this.snapshotStore = snapshotStore;
    }

    public SnapshotWriteResult save(DispatchV2Request request, DispatchV2Result result) {
        if (!properties.getFeedback().isSnapshotEnabled()) {
            return new SnapshotWriteResult(
                    "snapshot-write-result/v1",
                    null,
                    false,
                    null,
                    null,
                    List.of("snapshot-disabled"));
        }
        return snapshotStore.save(snapshotBuilder.build(request, result));
    }

    public SnapshotLoadResult loadLatest() {
        if (!properties.getFeedback().isSnapshotEnabled()) {
            return new SnapshotLoadResult(
                    "snapshot-load-result/v1",
                    false,
                    null,
                    null,
                    List.of("snapshot-disabled"));
        }
        SnapshotLoadResult loadResult = snapshotStore.loadLatest();
        if (!loadResult.loaded() || loadResult.snapshot() == null) {
            return loadResult;
        }
        if (!"dispatch-runtime-snapshot/v1".equals(loadResult.snapshot().schemaVersion())) {
            return new SnapshotLoadResult(
                    "snapshot-load-result/v1",
                    false,
                    loadResult.manifest(),
                    null,
                    List.of("snapshot-schema-version-mismatch"));
        }
        return loadResult;
    }
}
