package com.routechain.v2.bundle;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.EtaContext;
import com.routechain.v2.cluster.MicroCluster;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BoundaryExpansionEngine {
    private final RouteChainDispatchV2Properties properties;

    public BoundaryExpansionEngine(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public BoundaryExpansion expand(MicroCluster cluster, List<BoundaryCandidate> candidates, EtaContext etaContext) {
        boolean weatherTightened = etaContext.weatherBadSignal();
        double threshold = weatherTightened
                ? properties.getBoundaryExpansion().getWeatherTightenedSupportThreshold()
                : properties.getBoundaryExpansion().getMinSupportScoreThreshold();
        int workingSetLimit = Math.min(properties.getCluster().getMaxSize(), properties.getBundle().getMaxSize());
        int remainingCapacity = Math.max(0, workingSetLimit - cluster.orderIds().size());
        List<String> accepted = new ArrayList<>();
        List<String> rejected = new ArrayList<>();
        List<String> reasons = new ArrayList<>();
        Map<String, Double> supportScoreByOrder = new LinkedHashMap<>();

        for (BoundaryCandidate candidate : candidates) {
            supportScoreByOrder.put(candidate.orderId(), candidate.supportScore());
            if (candidate.supportScore() < threshold) {
                rejected.add(candidate.orderId());
                reasons.add("boundary-support-below-threshold");
                continue;
            }
            if (accepted.size() >= properties.getBoundaryExpansion().getMaxBoundaryOrdersPerCluster()) {
                rejected.add(candidate.orderId());
                reasons.add("boundary-order-limit-reached");
                continue;
            }
            if (accepted.size() >= remainingCapacity) {
                rejected.add(candidate.orderId());
                reasons.add("boundary-working-set-limit-reached");
                continue;
            }
            accepted.add(candidate.orderId());
        }
        if (accepted.isEmpty() && !candidates.isEmpty()) {
            reasons.add("boundary-expansion-not-applied");
        }

        return new BoundaryExpansion(
                "boundary-expansion/v1",
                cluster.clusterId(),
                cluster.coreOrderIds(),
                List.copyOf(accepted),
                List.copyOf(rejected),
                Map.copyOf(supportScoreByOrder),
                List.copyOf(reasons.stream().distinct().toList()),
                weatherTightened);
    }
}
