package com.routechain.core;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.v2.DispatchV2CompatibleCore;

import java.time.Instant;
import java.util.List;

public class CompactCoreAdapter {
    private final DispatchV2CompatibleCore compatibleCore;

    public CompactCoreAdapter() {
        this(CompactPolicyConfig.defaults(), RouteChainDispatchV2Properties.defaults());
    }

    public CompactCoreAdapter(CompactPolicyConfig policyConfig) {
        this(policyConfig, RouteChainDispatchV2Properties.defaults());
    }

    public CompactCoreAdapter(CompactPolicyConfig policyConfig,
                              RouteChainDispatchV2Properties dispatchV2Properties) {
        this.compatibleCore = new DispatchV2CompatibleCore(policyConfig, dispatchV2Properties);
    }

    public CompactDispatchDecision dispatch(List<Order> openOrders,
                                            List<Driver> availableDrivers,
                                            List<Region> regions,
                                            int simulatedHour,
                                            double trafficIntensity,
                                            WeatherProfile weatherProfile,
                                            Instant decisionTime) {
        return compatibleCore.dispatch(
                openOrders,
                availableDrivers,
                regions,
                simulatedHour,
                trafficIntensity,
                weatherProfile,
                decisionTime);
    }

    public DispatchV2CompatibleCore core() {
        return compatibleCore;
    }
}
