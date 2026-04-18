package com.routechain.v2.chaos;

import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.feedback.DecisionLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchPhase3ProtocolDocsTest {
    @Test
    void phase3DocsExistAndNameControlledModeAsAssertionTruth() throws Exception {
        Path docsDir = Path.of("docs");
        String largeScale = Files.readString(docsDir.resolve("dispatch_v2_large_scale_protocol.md"));
        String soak = Files.readString(docsDir.resolve("dispatch_v2_soak_protocol.md"));
        String chaos = Files.readString(docsDir.resolve("dispatch_v2_chaos_matrix.md"));

        assertTrue(largeScale.contains("controlled"));
        assertTrue(largeScale.contains("local-real"));
        assertTrue(soak.contains("1h"));
        assertTrue(soak.contains("observation-only"));
        assertTrue(chaos.contains("tabular-unavailable"));
        assertTrue(chaos.contains("worker-malformed-response"));
    }

    @Test
    void phase3SupportDoesNotWidenRuntimeContracts() {
        List<String> dispatchResultComponents = Arrays.stream(DispatchV2Result.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        List<String> decisionLogComponents = Arrays.stream(DecisionLogRecord.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertFalse(dispatchResultComponents.contains("largeScaleBenchmarkResult"));
        assertFalse(dispatchResultComponents.contains("soakRunResult"));
        assertFalse(dispatchResultComponents.contains("chaosRunResult"));
        assertFalse(decisionLogComponents.contains("largeScaleBenchmarkResult"));
        assertFalse(decisionLogComponents.contains("soakRunResult"));
        assertFalse(decisionLogComponents.contains("chaosRunResult"));
    }
}
