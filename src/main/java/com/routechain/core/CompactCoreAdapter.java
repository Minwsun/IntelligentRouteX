package com.routechain.core;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.domain.Region;

import java.time.Instant;
import java.util.List;

public class CompactCoreAdapter {
    private final CompactDispatchCore compactDispatchCore;

    public CompactCoreAdapter() {
        this(CompactPolicyConfig.defaults());
    }

    public CompactCoreAdapter(CompactPolicyConfig policyConfig) {
        this.compactDispatchCore = new CompactDispatchCore(policyConfig);
    }

    public CompactDispatchDecision dispatch(List<Order> openOrders,
                                            List<Driver> availableDrivers,
                                            List<Region> regions,
                                            int simulatedHour,
                                            double trafficIntensity,
                                            WeatherProfile weatherProfile,
                                            Instant decisionTime) {
        return compactDispatchCore.dispatch(
                openOrders,
                availableDrivers,
                regions,
                simulatedHour,
                trafficIntensity,
                weatherProfile,
                decisionTime);
    }

    public CompactDispatchCore core() {
        return compactDispatchCore;
    }
}
