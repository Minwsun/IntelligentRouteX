package com.routechain.simulation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BenchmarkCertificationSupportTest {

    @Test
    void currentOmegaDescriptorAcceptsChampionRun() {
        assertTrue(BenchmarkCertificationSupport.isCurrentOmegaDescriptor(
                "counterfactual-normal-Omega-current-seed42 RUN-s42-000002-g000004"));
    }

    @Test
    void currentOmegaDescriptorRejectsAblationRuns() {
        assertFalse(BenchmarkCertificationSupport.isCurrentOmegaDescriptor(
                "counterfactual-normal-Omega-no-neural-prior-seed42 RUN-s42-000002-g000012"));
        assertFalse(BenchmarkCertificationSupport.isCurrentOmegaDescriptor(
                "counterfactual-normal-Omega-no-positioning-model-seed42 RUN-s42-000002-g000014"));
        assertFalse(BenchmarkCertificationSupport.isCurrentOmegaDescriptor(
                "counterfactual-normal-Omega-no-stress-ai-gate-seed42 RUN-s42-000002-g000016"));
        assertFalse(BenchmarkCertificationSupport.isCurrentOmegaDescriptor(
                "counterfactual-normal-Omega-small-batch-only-seed42 RUN-s42-000002-g000018"));
    }

    @Test
    void shouldMarkAuthorityDetectionFailureAsTriageOnly() {
        BenchmarkAuthoritySnapshot snapshot = BenchmarkCertificationSupport.collectAuthoritySnapshot(
                "smoke",
                command -> new BenchmarkCertificationSupport.GitCommandResult(128, "fatal: not a git repository"));

        assertTrue(snapshot.authorityDetectionFailed());
        assertFalse(snapshot.cleanCheckpointEligible());
        assertTrue(snapshot.triageOnly());
        assertTrue(snapshot.notes().stream().anyMatch(note -> note.contains("detection failed")));
    }

    @Test
    void shouldCollectDirtyAuthorityPathsWhenGitStatusSucceeds() {
        String output = String.join(System.lineSeparator(), List.of(
                " M build.gradle.kts",
                " M src/main/java/com/routechain/simulation/SimulationEngine.java",
                " M docs/notes.md"
        ));
        BenchmarkAuthoritySnapshot snapshot = BenchmarkCertificationSupport.collectAuthoritySnapshot(
                "smoke",
                command -> new BenchmarkCertificationSupport.GitCommandResult(0, output));

        assertFalse(snapshot.authorityDetectionFailed());
        assertTrue(snapshot.workspaceDirty());
        assertTrue(snapshot.authorityDirty());
        assertTrue(snapshot.dirtyTrackedPaths().contains("build.gradle.kts"));
        assertTrue(snapshot.dirtyTrackedPaths().contains("docs/notes.md"));
        assertFalse(snapshot.dirtyTrackedPaths().contains("ocs/notes.md"));
        assertTrue(snapshot.dirtyAuthorityPaths().contains("src/main/java/com/routechain/simulation/SimulationEngine.java"));
    }
}
