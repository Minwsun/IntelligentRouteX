package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.TestDispatchV2Factory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FileReplayStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void persistsAndLoadsReplayRecords() {
        FileReplayStore replayStore = new FileReplayStore(tempDir, 10);
        DispatchV2Request request = TestDispatchV2Factory.requestWithOrdersAndDriver();
        ReplayRequestRecord record = new ReplayRequestRecord("replay-request-record/v1", request.traceId(), request);

        replayStore.save(record);

        assertNotNull(replayStore.latest());
        assertEquals(record, replayStore.findByTraceId(request.traceId()));
        assertEquals(request.traceId(), replayStore.latest().traceId());
    }
}
