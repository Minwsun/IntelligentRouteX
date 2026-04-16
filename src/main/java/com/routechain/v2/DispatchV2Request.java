package com.routechain.v2;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Order;
import com.routechain.domain.Region;

import java.time.Instant;
import java.util.List;

public record DispatchV2Request(
        List<Order> openOrders,
        List<Driver> availableDrivers,
        List<Region> regions,
        int simulatedHour,
        double trafficIntensity,
        WeatherProfile weatherProfile,
        Instant decisionTime,
        String runId) {
}
