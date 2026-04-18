package com.routechain.v2.chaos;

import com.routechain.v2.perf.DispatchPerfBenchmarkHarness;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchChaosBenchmarkHarnessTest {
    private final DispatchChaosBenchmarkHarness harness = new DispatchChaosBenchmarkHarness();

    @TempDir
    Path tempDir;

    @Test
    void workerAndLiveSourceFaultsDegradeSafelyWithExplicitReasons() {
        DispatchChaosRunResult workerFault = harness.run(new DispatchChaosBenchmarkHarness.ChaosRequest(
                DispatchPhase3Support.ChaosFaultType.TABULAR_UNAVAILABLE,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                tempDir));
        DispatchChaosRunResult liveFault = harness.run(new DispatchChaosBenchmarkHarness.ChaosRequest(
                DispatchPhase3Support.ChaosFaultType.OPEN_METEO_UNAVAILABLE,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.HEAVY_RAIN,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                tempDir));

        assertTrue(workerFault.degradeReasons().stream().anyMatch(reason -> reason.contains("unavailable")));
        assertTrue(workerFault.conflictFreeAssignments());
        assertTrue(liveFault.degradeReasons().stream().anyMatch(reason -> reason.contains("open-meteo")));
        assertTrue(liveFault.conflictFreeAssignments());
    }

    @Test
    void malformedAndFingerprintCasesSerializeAsExplicitDeferredArtifacts() {
        DispatchChaosRunResult malformed = harness.run(new DispatchChaosBenchmarkHarness.ChaosRequest(
                DispatchPhase3Support.ChaosFaultType.WORKER_MALFORMED_RESPONSE,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                tempDir));
        DispatchChaosRunResult fingerprint = harness.run(new DispatchChaosBenchmarkHarness.ChaosRequest(
                DispatchPhase3Support.ChaosFaultType.WORKER_FINGERPRINT_MISMATCH,
                DispatchPerfBenchmarkHarness.WorkloadSize.M,
                DispatchPhase3Support.ScenarioPack.NORMAL_CLEAR,
                DispatchPhase3Support.ExecutionMode.CONTROLLED,
                DispatchPerfBenchmarkHarness.DEFAULT_MACHINE_LABEL,
                tempDir));

        assertTrue(malformed.deferred());
        assertTrue(malformed.notes().contains("deferred-existing-http-contract-only"));
        assertTrue(fingerprint.deferred());
        assertTrue(fingerprint.notes().contains("deferred-existing-http-contract-only"));
    }
}
