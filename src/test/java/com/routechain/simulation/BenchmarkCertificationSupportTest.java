package com.routechain.simulation;

import org.junit.jupiter.api.Test;

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
}
