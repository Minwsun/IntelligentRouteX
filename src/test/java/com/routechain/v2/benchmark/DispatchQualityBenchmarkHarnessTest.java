package com.routechain.v2.benchmark;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchQualityBenchmarkHarnessTest {
    private final DispatchQualityBenchmarkHarness harness = new DispatchQualityBenchmarkHarness();

    @TempDir
    Path tempDir;

    @Test
    void benchmarkRunEmitsThreeBaselinesAndOneComparisonReport() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(
                        DispatchPerfBenchmarkHarness.BaselineId.A,
                        DispatchPerfBenchmarkHarness.BaselineId.B,
                        DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchBenchmarkDecisionMode.LEGACY,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                false,
                tempDir));

        assertEquals(3, run.rawResults().size());
        assertNotNull(run.comparisonReport());
        assertTrue(run.rawResults().stream().allMatch(result -> result.decisionStages().size() == 12));
        assertTrue(run.rawResults().stream().allMatch(result -> !result.deferred()));
        assertTrue(run.rawResults().stream().allMatch(result -> result.decisionMode().equals("legacy")));
    }

    @Test
    void stableScenarioKeepsConflictFreeAssignmentsAndPopulatesMetrics() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(
                        DispatchPerfBenchmarkHarness.BaselineId.A,
                        DispatchPerfBenchmarkHarness.BaselineId.B,
                        DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchBenchmarkDecisionMode.LEGACY,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                false,
                tempDir));

        DispatchQualityBenchmarkResult fullV2 = run.rawResults().stream()
                .filter(result -> result.baselineId().equals("C"))
                .findFirst()
                .orElseThrow();
        assertTrue(fullV2.metrics().conflictFreeAssignments());
        assertTrue(fullV2.metrics().robustUtilityAverage() >= 0.0);
        assertTrue(fullV2.metrics().selectorObjectiveValue() >= 0.0);
        assertTrue(fullV2.metrics().workerFallbackRate() >= 0.0);
        assertTrue(fullV2.metrics().liveSourceFallbackRate() >= 0.0);
        assertTrue(fullV2.routeVectorMetrics().geometryCoverage() >= 0.0);
    }

    @Test
    void ablationProducesDeltaReportWithoutChangingContracts() {
        DispatchAblationResult result = harness.ablate(new DispatchQualityBenchmarkHarness.AblationRequest(
                DispatchQualityBenchmarkHarness.AblationComponent.ORTOOLS,
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                false,
                tempDir));

        assertEquals("ortools", result.toggledComponent());
        assertFalse(result.deltaSummary().isEmpty());
        assertTrue(result.controlMetrics().conflictFreeAssignments());
        assertTrue(result.variantMetrics().conflictFreeAssignments());
    }

    @Test
    void authorityLocalRealRunCarriesAuthorityClassification() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchBenchmarkDecisionMode.LEGACY,
                DispatchQualityBenchmarkHarness.ExecutionMode.LOCAL_REAL,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                true,
                false,
                tempDir));

        DispatchQualityBenchmarkResult result = run.rawResults().getFirst();
        assertEquals("AUTHORITY_REAL", result.runAuthorityClass());
        assertTrue(result.authoritative());
        assertTrue(result.authorityEligible());
        assertFalse(result.notes().contains("non-authoritative-local-real-run"));
    }

    @Test
    void authorityLocalRealRunMarksAttachFailureWhenMlWorkersDoNotApply() {
        Path missingManifest = tempDir.resolve("missing-model-manifest.yaml");
        String previous = System.getProperty("dispatchV2.ml.modelManifestPath");
        System.setProperty("dispatchV2.ml.modelManifestPath", missingManifest.toString());
        try {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                    List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                    DispatchPerfBenchmarkHarness.WorkloadSize.S,
                    DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                    DispatchBenchmarkDecisionMode.LEGACY,
                    DispatchQualityBenchmarkHarness.ExecutionMode.LOCAL_REAL,
                    DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                    true,
                    false,
                    tempDir));

            DispatchQualityBenchmarkResult result = run.rawResults().getFirst();
            assertEquals(missingManifest.toAbsolutePath().normalize().toString(), result.resolvedModelManifestPath());
            assertFalse(result.manifestExists());
            assertEquals(DispatchQualityMlAttachStatus.ML_ATTACH_FAIL, result.mlAttachStatus());
            assertTrue(result.mlAttachmentFailureReasons().contains("model-manifest-missing"));
            assertTrue(result.notes().contains("ML_ATTACH_FAIL"));
            assertTrue(result.workerAppliedSources().isEmpty());
            assertFalse(result.workerStatusSnapshot().isEmpty());
        } finally {
            restoreProperty("dispatchV2.ml.modelManifestPath", previous);
        }
    }

    @Test
    void localRealRunWithoutAuthorityFlagRemainsNonAuthoritative() {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchBenchmarkDecisionMode.LEGACY,
                DispatchQualityBenchmarkHarness.ExecutionMode.LOCAL_REAL,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                false,
                tempDir));

        DispatchQualityBenchmarkResult result = run.rawResults().getFirst();
        assertEquals("LOCAL_NON_AUTHORITY", result.runAuthorityClass());
        assertFalse(result.authoritative());
        assertFalse(result.authorityEligible());
        assertTrue(result.notes().contains("non-authoritative-local-real-run"));
    }

    @Test
    void localRealRunResolvesManifestPathFromRuntimeStyleOverride() {
        String previous = System.getProperty("dispatchV2.ml.modelManifestPath");
        Path manifest = Path.of("services", "models", "model-manifest.yaml").toAbsolutePath().normalize();
        System.setProperty("dispatchV2.ml.modelManifestPath", manifest.toString());
        try {
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                    List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                    DispatchPerfBenchmarkHarness.WorkloadSize.S,
                    DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                    DispatchBenchmarkDecisionMode.LEGACY,
                    DispatchQualityBenchmarkHarness.ExecutionMode.LOCAL_REAL,
                    DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                    false,
                    false,
                    tempDir));

            DispatchQualityBenchmarkResult result = run.rawResults().getFirst();
            assertEquals(manifest.toString(), result.resolvedModelManifestPath());
            assertTrue(result.manifestExists());
        } finally {
            restoreProperty("dispatchV2.ml.modelManifestPath", previous);
        }
    }

    @Test
    void llmShadowBenchmarkModePopulatesDecisionFeedbackSummaries() {
        String previousApiKey = System.getenv("OPENAI_API_KEY");
        DispatchQualityBenchmarkRun run = harness.benchmark(new DispatchQualityBenchmarkHarness.BenchmarkRequest(
                List.of(DispatchPerfBenchmarkHarness.BaselineId.C),
                DispatchPerfBenchmarkHarness.WorkloadSize.S,
                DispatchQualityBenchmarkHarness.ScenarioPack.NORMAL_CLEAR,
                DispatchBenchmarkDecisionMode.LLM_SHADOW,
                DispatchQualityBenchmarkHarness.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                false,
                tempDir));

        DispatchQualityBenchmarkResult result = run.rawResults().getFirst();
        assertEquals("llm-shadow", result.decisionMode());
        assertNotNull(result.llmShadowAgreement());
        assertNotNull(result.stageFallbackSummary());
        assertNotNull(result.tokenUsageSummary());
        assertTrue(result.stageFallbackSummary().totalStageOutputs() >= 1);
    }

    private void restoreProperty(String name, String previous) {
        if (previous == null) {
            System.clearProperty(name);
            return;
        }
        System.setProperty(name, previous);
    }
}
