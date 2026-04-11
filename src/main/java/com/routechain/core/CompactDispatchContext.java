package com.routechain.core;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Region;

import java.time.Instant;
import java.util.List;

public record CompactDispatchContext(
        List<Region> regions,
        int simulatedHour,
        double trafficIntensity,
        WeatherProfile weatherProfile,
        Instant decisionTime,
        int pendingOrderCount,
        int availableDriverCount) {
}
