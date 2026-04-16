package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleIdDeterminismTest {

    @Test
    void sameInputYieldsSameBundleIdSignatureAndFamily() {
        BundleFamilyEnumerator enumerator = new BundleFamilyEnumerator(RouteChainDispatchV2Properties.defaults());
        BundleContext context = new BundleContext(BundleTestFixtures.window().orders(), BundleTestFixtures.graph(), List.of());
        BundleSeed seed = new BundleSeed(
                BundleTestFixtures.microClusters().getFirst(),
                List.of("order-1", "order-2", "order-3"),
                List.of(),
                List.of("order-1", "order-2", "order-3"),
                java.util.Map.of());

        BundleCandidate first = enumerator.enumerate(seed, context).getFirst();
        BundleCandidate second = enumerator.enumerate(seed, context).getFirst();

        assertEquals(first.bundleId(), second.bundleId());
        assertEquals(first.orderSetSignature(), second.orderSetSignature());
        assertEquals(first.family(), second.family());
    }
}
