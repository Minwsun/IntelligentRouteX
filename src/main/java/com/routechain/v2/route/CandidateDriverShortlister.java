package com.routechain.v2.route;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.v2.EtaContext;
import com.routechain.v2.cluster.EtaLegCache;

import java.util.Comparator;
import java.util.List;

public final class CandidateDriverShortlister {
    private final RouteChainDispatchV2Properties properties;
    private final DriverRouteFeatureBuilder driverRouteFeatureBuilder;

    public CandidateDriverShortlister(RouteChainDispatchV2Properties properties,
                                      DriverRouteFeatureBuilder driverRouteFeatureBuilder) {
        this.properties = properties;
        this.driverRouteFeatureBuilder = driverRouteFeatureBuilder;
    }

    public List<DriverRouteFeatures> shortlist(List<Driver> availableDrivers,
                                               PickupAnchor pickupAnchor,
                                               DispatchCandidateContext context,
                                               EtaContext etaContext,
                                               EtaLegCache etaLegCache) {
        return availableDrivers.stream()
                .sorted(Comparator.comparing(Driver::driverId))
                .map(driver -> driverRouteFeatureBuilder.build(driver, pickupAnchor, context, etaContext, etaLegCache))
                .sorted(Comparator.comparingDouble(DriverRouteFeatures::driverFitScore).reversed()
                        .thenComparingDouble(DriverRouteFeatures::pickupEtaMinutes)
                        .thenComparing(DriverRouteFeatures::driverId))
                .limit(Math.max(1, properties.getCandidate().getMaxDrivers()))
                .toList();
    }
}
