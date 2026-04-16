package com.routechain.v2.bundle;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleDominancePrunerTest {

    @Test
    void deduplicatesByOrderSetSignatureAndKeepsBestCandidate() {
        BundleDominancePruner pruner = new BundleDominancePruner();
        List<BundleCandidate> retained = pruner.prune(List.of(
                new BundleCandidate("bundle-candidate/v1", "a", BundleFamily.COMPACT_CLIQUE, List.of("order-1", "order-2"), "order-1|order-2", "order-1", "0:0", 0.70, true, List.of()),
                new BundleCandidate("bundle-candidate/v1", "b", BundleFamily.COMPACT_CLIQUE, List.of("order-1", "order-2"), "order-1|order-2", "order-1", "0:0", 0.65, true, List.of()),
                new BundleCandidate("bundle-candidate/v1", "c", BundleFamily.CORRIDOR_CHAIN, List.of("order-1", "order-2"), "order-1|order-2", "order-1", "0:0", 0.75, true, List.of())));

        assertEquals(1, retained.size());
        assertEquals("c", retained.getFirst().bundleId());
    }
}
