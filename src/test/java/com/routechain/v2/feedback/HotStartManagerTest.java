package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.HotStartState;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotStartManagerTest {

    @Test
    void matchingSignaturesBecomeReuseEligibleAndDriftDisablesReuse() {
        HotStartManager hotStartManager = new HotStartManager(RouteChainDispatchV2Properties.defaults());
        DispatchRuntimeSnapshot firstSnapshot = snapshot("trace-1", List.of("cluster-a"), List.of("bundle-a"), List.of("proposal-a"));
        DispatchRuntimeSnapshot compatibleSnapshot = snapshot("trace-2", List.of("cluster-a"), List.of("bundle-a"), List.of("proposal-a"));
        DispatchRuntimeSnapshot driftedSnapshot = snapshot("trace-3", List.of("cluster-b"), List.of("bundle-a"), List.of("proposal-a"));

        HotStartState firstState = hotStartManager.update(firstSnapshot);
        HotStartState compatibleState = hotStartManager.update(compatibleSnapshot);
        HotStartState driftedState = hotStartManager.update(driftedSnapshot);

        assertFalse(firstState.reuseEligible());
        assertTrue(compatibleState.reuseEligible());
        assertFalse(driftedState.reuseEligible());
        assertTrue(driftedState.degradeReasons().contains("hot-start-signature-drift"));
    }

    private DispatchRuntimeSnapshot snapshot(String traceId,
                                            List<String> clusterSignatures,
                                            List<String> bundleSignatures,
                                            List<String> routeProposalSignatures) {
        return new DispatchRuntimeSnapshot(
                "dispatch-runtime-snapshot/v1",
                traceId + "-snapshot",
                traceId,
                Instant.parse("2026-04-16T12:00:00Z"),
                List.of("eta/context"),
                List.of("proposal-1"),
                List.of("assignment-1"),
                clusterSignatures,
                bundleSignatures,
                routeProposalSignatures,
                1.0,
                List.of());
    }
}
