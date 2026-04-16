package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.cluster.MicroCluster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BundleSeedGenerator {
    private final RouteChainDispatchV2Properties properties;

    public BundleSeedGenerator(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public List<BundleSeed> generate(List<MicroCluster> microClusters, BundleContext context) {
        List<BundleSeed> seeds = new ArrayList<>();
        for (MicroCluster cluster : microClusters.stream().sorted(Comparator.comparing(MicroCluster::clusterId)).toList()) {
            BoundaryExpansion expansion = context.expansionFor(cluster);
            List<String> acceptedBoundaryOrderIds = expansion == null ? List.of() : expansion.acceptedBoundaryOrderIds();
            List<String> distinctWorkingOrders = java.util.stream.Stream.concat(
                            cluster.orderIds().stream(),
                            acceptedBoundaryOrderIds.stream())
                    .distinct()
                    .sorted()
                    .toList();
            List<String> prioritizedOrderIds = distinctWorkingOrders.stream()
                    .sorted(Comparator
                            .comparingDouble((String orderId) -> expansion == null ? 0.0 : expansion.supportScoreByOrder().getOrDefault(orderId, 0.0))
                            .reversed()
                            .thenComparing(orderId -> context.order(orderId).readyAt())
                            .thenComparing(orderId -> orderId))
                    .limit(properties.getBundle().getTopNeighbors())
                    .toList();
            seeds.add(new BundleSeed(
                    cluster,
                    distinctWorkingOrders,
                    acceptedBoundaryOrderIds,
                    prioritizedOrderIds,
                    expansion == null ? java.util.Map.of() : expansion.supportScoreByOrder()));
        }
        return List.copyOf(seeds);
    }
}
