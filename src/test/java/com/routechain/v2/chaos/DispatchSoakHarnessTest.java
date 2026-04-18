package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                3,
                tempDir));

        assertEquals(3, result.sampleCount());
        assertEquals(3, result.latencyTrend().samples().size());
        assertEquals(3, result.memoryUsageTrend().samples().size());
        assertEquals(3, result.reuseHitTrend().samples().size());
        assertTrue(result.replayIsolationMaintained());
        assertTrue(result.passed());
    }
}
