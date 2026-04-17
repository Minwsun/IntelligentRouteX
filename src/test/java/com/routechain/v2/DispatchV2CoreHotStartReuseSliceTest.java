package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchV2CoreHotStartReuseSliceTest {

    @Test
    void secondCompatibleDispatchReusesPairBundleAndRouteProposalStages() {
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(RouteChainDispatchV2Properties.defaults());

        DispatchV2Request firstRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result firstResult = harness.core().dispatch(firstRequest);
        DispatchV2Result secondResult = harness.core().dispatch(copyWithTraceId(firstRequest, "trace-hot-start-second"));

        assertEquals(12, secondResult.decisionStages().size());
        assertEquals(firstResult.traceId(), secondResult.hotStartState().previousTraceId());
        assertTrue(secondResult.hotStartState().reuseEligible());
        assertTrue(secondResult.hotStartState().pairClusterReused());
        assertTrue(secondResult.hotStartState().bundlePoolReused());
        assertTrue(secondResult.hotStartState().routeProposalPoolReused());
        assertTrue(secondResult.hotStartState().reusedBundleCount() > 0);
        assertTrue(secondResult.hotStartState().reusedRouteProposalCount() > 0);
    }

    @Test
    void compatibleOrdersButDriverDriftKeepUpstreamReuseAndOnlyPartiallyReuseRoutes() {
        TestDispatchV2Factory.TestDispatchRuntimeHarness harness = TestDispatchV2Factory.harness(RouteChainDispatchV2Properties.defaults());

        DispatchV2Request firstRequest = TestDispatchV2Factory.requestWithOrdersAndDriver();
        DispatchV2Result firstResult = harness.core().dispatch(firstRequest);
        DispatchV2Request driftedDriverRequest = new DispatchV2Request(
                firstRequest.schemaVersion(),
                "trace-hot-start-driver-drift",
                firstRequest.openOrders(),
                List.of(
                        new Driver("driver-1", new GeoPoint(10.7700, 106.6950)),
                        new Driver("driver-2", new GeoPoint(10.9000, 106.9000)),
                        new Driver("driver-3", new GeoPoint(10.7810, 106.7060))),
                firstRequest.regions(),
                firstRequest.weatherProfile(),
                firstRequest.decisionTime());

        DispatchV2Result secondResult = harness.core().dispatch(driftedDriverRequest);

        assertEquals(firstResult.traceId(), secondResult.hotStartState().previousTraceId());
        assertTrue(secondResult.hotStartState().pairClusterReused());
        assertTrue(secondResult.hotStartState().bundlePoolReused());
        assertTrue(secondResult.hotStartState().routeProposalPoolReused());
        assertTrue(secondResult.hotStartState().reusedRouteProposalCount() > 0);
        assertTrue(secondResult.hotStartState().reusedRouteProposalCount() < firstResult.routeProposals().size());
        assertTrue(secondResult.hotStartState().degradeReasons().contains("hot-start-route-tuple-drift"));
    }

    private DispatchV2Request copyWithTraceId(DispatchV2Request request, String traceId) {
        return new DispatchV2Request(
                request.schemaVersion(),
                traceId,
                request.openOrders(),
                request.availableDrivers(),
                request.regions(),
                request.weatherProfile(),
                request.decisionTime());
    }
}
