package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FileSnapshotStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void savesLoadsLatestAndLoadsByTraceId() {
        FileSnapshotStore snapshotStore = new FileSnapshotStore(tempDir, 10);
        SnapshotService snapshotService = new SnapshotService(
                RouteChainDispatchV2Properties.defaults(),
                new SnapshotBuilder(),
                snapshotStore);
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result result = TestDispatchV2Factory.core(RouteChainDispatchV2Properties.defaults()).dispatch(request);

        SnapshotWriteResult writeResult = snapshotService.save(request, result);
        SnapshotLoadResult latestLoad = snapshotService.loadLatest();
        SnapshotLoadResult traceLoad = snapshotService.loadByTraceId(request.traceId());

        assertTrue(writeResult.written());
        assertEquals(writeResult.snapshotId(), latestLoad.snapshot().snapshotId());
        assertEquals(writeResult.snapshotId(), traceLoad.snapshot().snapshotId());
    }
}
