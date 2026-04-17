package com.routechain.v2.feedback;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.EtaContext;
import com.routechain.v2.HotStartState;
import com.routechain.v2.MlStageMetadata;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotStartManagerTest {

    @Test
    void matchingEtaSignaturePlansReuseAndAppliedReuseIsReported() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        InMemoryReuseStateStore reuseStateStore = new InMemoryReuseStateStore();
        ReuseStateService reuseStateService = new ReuseStateService(properties, new ReuseStateBuilder(), reuseStateStore);
        reuseStateStore.save(reuseState("trace-1", "eta|1|6.000000|6.000000|0.300000|false|false|baseline-profile-weather"));

        HotStartManager hotStartManager = new HotStartManager(properties, reuseStateService);
        HotStartReusePlan compatiblePlan = hotStartManager.plan(etaContext("trace-2", 6.0));
        HotStartState compatibleState = hotStartManager.update(
                reuseState("trace-2", compatiblePlan.reuseState().etaContextSignature()),
                compatiblePlan,
                new HotStartAppliedReuse("hot-start-applied-reuse/v1", true, true, true, 3, 5, List.of()));

        assertTrue(compatiblePlan.reuseEligible());
        assertTrue(compatibleState.pairClusterReused());
        assertTrue(compatibleState.bundlePoolReused());
        assertTrue(compatibleState.routeProposalPoolReused());
        assertTrue(compatibleState.reusedBundleCount() > 0);
        assertTrue(compatibleState.reusedRouteProposalCount() > 0);
    }

    @Test
    void etaDriftDisablesReuse() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        InMemoryReuseStateStore reuseStateStore = new InMemoryReuseStateStore();
        ReuseStateService reuseStateService = new ReuseStateService(properties, new ReuseStateBuilder(), reuseStateStore);
        reuseStateStore.save(reuseState("trace-1", "eta|1|6.000000|6.000000|0.300000|false|false|baseline-profile-weather"));

        HotStartManager hotStartManager = new HotStartManager(properties, reuseStateService);
        HotStartReusePlan driftedPlan = hotStartManager.plan(etaContext("trace-3", 8.0));

        assertFalse(driftedPlan.reuseEligible());
        assertTrue(driftedPlan.degradeReasons().contains("hot-start-eta-signature-drift"));
    }

    private DispatchRuntimeReuseState reuseState(String traceId, String etaSignature) {
        List<String> emptyStrings = List.of();
        List<MlStageMetadata> emptyMlMetadata = List.of();
        return new DispatchRuntimeReuseState(
                "dispatch-runtime-reuse-state/v1",
                traceId + "-reuse",
                traceId,
                Instant.parse("2026-04-16T12:00:00Z"),
                etaSignature,
                "buffer|0|0|",
                List.of("cluster-a"),
                List.of("bundle-a"),
                null,
                null,
                List.of(),
                null,
                emptyMlMetadata,
                emptyStrings,
                List.of(),
                null,
                List.of(),
                null,
                emptyMlMetadata,
                emptyStrings,
                null,
                null,
                List.of(),
                emptyMlMetadata,
                emptyStrings,
                emptyStrings);
    }

    private EtaContext etaContext(String traceId, double averageEtaMinutes) {
        return new EtaContext(
                "dispatch-eta-context/v1",
                traceId,
                1,
                averageEtaMinutes,
                averageEtaMinutes,
                0.3,
                false,
                false,
                "corridor-a",
                "baseline-profile-weather");
    }
}
