package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchSoakHarnessTest {
    private final DispatchSoakHarness harness = new DispatchSoakHarness();

    @TempDir
    Path tempDir;

    @Test
    void soakSmokeRunWritesTrendFieldsAndMaintainsReplayIsolation() {
        DispatchSoakRunResult result = harness.run(new DispatchSoakHarness.SoakRequest(
                DispatchSoakHarness.DurationProfile.ONE_HOUR,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                3,
                tempDir));

        assertEquals(3, result.sampleCount());
        assertEquals(3, result.latencyTrend().samples().size());
        assertEquals(3, result.memoryUsageTrend().samples().size());
        assertEquals(3, result.reuseHitTrend().samples().size());
        assertTrue(result.replayIsolationMaintained());
        assertTrue(result.passed());
    }

    @Test
    void authorityLocalRealSoakUsesDurationDefaultWithoutForcedOverride() {
        DispatchSoakRunResult result = harness.run(new DispatchSoakHarness.SoakRequest(
                DispatchSoakHarness.DurationProfile.SIX_HOURS,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.LOCAL_REAL,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                true,
                null,
                tempDir));

        assertEquals(18, result.sampleCount());
        assertEquals("AUTHORITY_REAL", result.runAuthorityClass());
        assertTrue(result.authoritative());
        assertTrue(result.authorityEligible());
        assertFalse(result.sampleCountOverrideApplied());
        assertTrue(result.notes().isEmpty());
        assertTrue(result.failureSummaries().isEmpty());
    }

    @Test
    void localRealSoakWithoutAuthorityFlagRemainsNonAuthoritative() {
        DispatchSoakRunResult result = harness.run(new DispatchSoakHarness.SoakRequest(
                DispatchSoakHarness.DurationProfile.ONE_HOUR,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.LOCAL_REAL,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                false,
                3,
                tempDir));

        assertEquals("LOCAL_NON_AUTHORITY", result.runAuthorityClass());
        assertFalse(result.authoritative());
        assertFalse(result.authorityEligible());
        assertTrue(result.sampleCountOverrideApplied());
        assertTrue(result.notes().contains("non-authoritative-local-real-run"));
        assertTrue(result.failureSummaries().contains("non-authoritative-local-real-run"));
    }
}
