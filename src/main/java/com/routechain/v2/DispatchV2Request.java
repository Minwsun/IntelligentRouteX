package com.routechain.v2;

import com.routechain.domain.Driver;
import com.routechain.domain.Order;
import com.routechain.domain.Region;
import com.routechain.domain.WeatherProfile;

import java.time.Instant;
import java.util.List;

public record DispatchV2Request(
        String schemaVersion,
        String traceId,
        List<Order> openOrders,
        List<Driver> availableDrivers,
        List<Region> regions,
        WeatherProfile weatherProfile,
        Instant decisionTime) implements SchemaVersioned {
}

