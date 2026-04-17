package com.routechain.v2.feedback;

import com.routechain.v2.MlStageMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReuseStateStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void inMemoryStoreSavesAndLoadsLatestReuseState() {
        InMemoryReuseStateStore store = new InMemoryReuseStateStore();
        DispatchRuntimeReuseState reuseState = reuseState("trace-in-memory");

        ReuseStateWriteResult writeResult = store.save(reuseState);
        ReuseStateLoadResult loadResult = store.loadLatest();

        assertTrue(writeResult.written());
        assertTrue(loadResult.loaded());
        assertEquals(reuseState, loadResult.reuseState());
    }

    @Test
    void fileStoreSavesAndLoadsLatestReuseState() {
        FileReuseStateStore store = new FileReuseStateStore(tempDir, 5);
        DispatchRuntimeReuseState reuseState = reuseState("trace-file");

        ReuseStateWriteResult writeResult = store.save(reuseState);
        ReuseStateLoadResult loadResult = store.loadLatest();

        assertTrue(writeResult.written());
        assertTrue(loadResult.loaded());
        assertEquals(reuseState.traceId(), loadResult.reuseState().traceId());
        assertEquals(reuseState.etaContextSignature(), loadResult.reuseState().etaContextSignature());
    }

    private DispatchRuntimeReuseState reuseState(String traceId) {
        return new DispatchRuntimeReuseState(
                "dispatch-runtime-reuse-state/v1",
                traceId + "-reuse",
                traceId,
                Instant.parse("2026-04-16T12:00:00Z"),
                "eta|1|6.000000|6.000000|0.300000|false|false|baseline-profile-weather",
                "buffer|0|0|",
                List.of("cluster-a"),
                List.of("bundle-a"),
                null,
                null,
                List.of(),
                null,
                List.<MlStageMetadata>of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null,
                List.<MlStageMetadata>of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.<MlStageMetadata>of(),
                List.of(),
                List.of());
    }
}
