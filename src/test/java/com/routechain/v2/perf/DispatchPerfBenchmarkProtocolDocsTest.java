package com.routechain.v2.perf;

import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.feedback.DecisionLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchPerfBenchmarkProtocolDocsTest {
    @Test
    void protocolDocsExistAndDescribeAuthorityMachineAndBaselines() throws Exception {
        Path docsDir = Path.of("docs");
        String protocol = Files.readString(docsDir.resolve("dispatch_v2_benchmark_protocol.md"));
        String baselines = Files.readString(docsDir.resolve("dispatch_v2_baseline_matrix.md"));
        String gates = Files.readString(docsDir.resolve("dispatch_v2_acceptance_gates.md"));

        assertTrue(protocol.contains("dispatch-v2-benchmark-authority-v1"));
        assertTrue(baselines.contains("Baseline A"));
        assertTrue(baselines.contains("Baseline B"));
        assertTrue(baselines.contains("Baseline C"));
        assertTrue(gates.contains("## Correctness Gates"));
        assertTrue(gates.contains("## Performance Gates"));
        assertTrue(gates.contains("## Observation-Only Metrics"));
    }

    @Test
    void benchmarkSupportDoesNotWidenRuntimeContracts() {
        List<String> dispatchResultComponents = Arrays.stream(DispatchV2Result.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        List<String> decisionLogComponents = Arrays.stream(DecisionLogRecord.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertFalse(dispatchResultComponents.contains("perfBenchmarkResult"));
        assertFalse(dispatchResultComponents.contains("perfBenchmarkSummary"));
        assertFalse(decisionLogComponents.contains("perfBenchmarkResult"));
        assertFalse(decisionLogComponents.contains("perfBenchmarkSummary"));
    }
}
