package com.routechain.v2.benchmark;

import com.routechain.v2.DispatchV2Result;
import com.routechain.v2.feedback.DecisionLogRecord;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchQualityBenchmarkProtocolDocsTest {
    @Test
    void qualityDocsExistAndDescribeMetricsAndAblationModes() throws Exception {
        Path docsDir = Path.of("docs");
        String metrics = Files.readString(docsDir.resolve("dispatch_v2_quality_metrics.md"));
        String ablations = Files.readString(docsDir.resolve("dispatch_v2_ablation_matrix.md"));

        assertTrue(metrics.contains("selectedProposalCount"));
        assertTrue(metrics.contains("cancel-risk proxy"));
        assertTrue(ablations.contains("local-real"));
        assertTrue(ablations.contains("tabular on/off"));
        assertTrue(ablations.contains("OR-Tools vs degraded greedy"));
    }

    @Test
    void qualityBenchmarkSupportDoesNotWidenRuntimeContracts() {
        List<String> dispatchResultComponents = Arrays.stream(DispatchV2Result.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();
        List<String> decisionLogComponents = Arrays.stream(DecisionLogRecord.class.getRecordComponents())
                .map(component -> component.getName())
                .toList();

        assertFalse(dispatchResultComponents.contains("qualityBenchmarkResult"));
        assertFalse(dispatchResultComponents.contains("qualityComparisonReport"));
        assertFalse(decisionLogComponents.contains("qualityBenchmarkResult"));
        assertFalse(decisionLogComponents.contains("qualityComparisonReport"));
    }
}
