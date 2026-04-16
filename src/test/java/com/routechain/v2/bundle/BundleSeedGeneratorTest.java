package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BundleSeedGeneratorTest {

    @Test
    void seedsAreDeterministicAndRespectTopNeighbors() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        properties.getBundle().setTopNeighbors(2);
        BundleSeedGenerator generator = new BundleSeedGenerator(properties);
        BundleContext context = new BundleContext(
                BundleTestFixtures.window().orders(),
                BundleTestFixtures.graph(),
                List.of(new BoundaryExpansion("boundary-expansion/v1", "cluster-001", List.of("order-1"), List.of("order-3"), List.of(), java.util.Map.of("order-3", 0.82), List.of(), false)));

        List<BundleSeed> seeds = generator.generate(BundleTestFixtures.microClusters().subList(0, 1), context);

        assertEquals(1, seeds.size());
        assertEquals(2, seeds.getFirst().prioritizedOrderIds().size());
        assertEquals(seeds, generator.generate(BundleTestFixtures.microClusters().subList(0, 1), context));
    }
}
