package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryExpansionEngineTest {

    @Test
    void acceptsValidBoundaryCandidatesAndTightensInBadWeather() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        BoundaryExpansionEngine engine = new BoundaryExpansionEngine(properties);
        List<BoundaryCandidate> candidates = List.of(
                new BoundaryCandidate("order-3", 0.70, false, true, "0:0"),
                new BoundaryCandidate("order-4", 0.58, true, false, "1:2"));

        BoundaryExpansion clear = engine.expand(BundleTestFixtures.microClusters().getFirst(), candidates, BundleTestFixtures.clearEtaContext());
        BoundaryExpansion weatherBad = engine.expand(BundleTestFixtures.microClusters().getFirst(), candidates, BundleTestFixtures.weatherBadEtaContext());

        assertEquals(List.of("order-3", "order-4"), clear.acceptedBoundaryOrderIds());
        assertEquals(List.of("order-3"), weatherBad.acceptedBoundaryOrderIds());
        assertTrue(weatherBad.rejectedBoundaryOrderIds().contains("order-4"));
    }

    @Test
    void respectsWorkingSetLimit() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getBundle().setMaxSize(2);
        BoundaryExpansionEngine engine = new BoundaryExpansionEngine(properties);

        BoundaryExpansion expansion = engine.expand(
                BundleTestFixtures.microClusters().getFirst(),
                List.of(new BoundaryCandidate("order-3", 0.8, false, true, "0:0")),
                BundleTestFixtures.clearEtaContext());

        assertTrue(expansion.acceptedBoundaryOrderIds().isEmpty());
        assertTrue(expansion.expansionReasons().contains("boundary-working-set-limit-reached"));
    }
}
