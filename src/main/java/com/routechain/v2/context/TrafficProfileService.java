package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.v2.DispatchV2Request;

import java.time.Instant;
import java.time.ZoneOffset;

public final class TrafficProfileService {
    private final RouteChainDispatchV2Properties properties;

    public TrafficProfileService(RouteChainDispatchV2Properties properties) {
        this.properties = properties;
    }

    public TrafficProfileSnapshot resolveTraffic(DispatchV2Request request, GeoPoint from, GeoPoint to) {
        if (request == null || from == null || to == null) {
            return new TrafficProfileSnapshot(
                    "traffic-profile-snapshot/v1",
                    1.0,
                    TrafficProfileSource.DEGRADED_PROFILE,
                    properties.getContext().getFreshness().getTrafficMaxAge().toMillis() + 1,
                    0.4,
                    false,
                    "traffic-profile-degraded");
        }
        int hour = hourOfDay(request.decisionTime());
        if (isPeak(hour)) {
            return new TrafficProfileSnapshot(
                    "traffic-profile-snapshot/v1",
                    1.35,
                    TrafficProfileSource.PROFILE_PEAK,
                    0L,
                    0.95,
                    true,
                    "");
        }
        if (isOffPeak(hour)) {
            return new TrafficProfileSnapshot(
                    "traffic-profile-snapshot/v1",
                    0.90,
                    TrafficProfileSource.PROFILE_OFFPEAK,
                    0L,
                    0.95,
                    false,
                    "");
        }
        return new TrafficProfileSnapshot(
                "traffic-profile-snapshot/v1",
                1.00,
                TrafficProfileSource.PROFILE_DEFAULT,
                0L,
                0.95,
                false,
                "");
    }

    private int hourOfDay(Instant instant) {
        Instant safeInstant = instant == null ? Instant.EPOCH : instant;
        return safeInstant.atZone(ZoneOffset.UTC).getHour();
    }

    private boolean isPeak(int hour) {
        return (hour >= 7 && hour <= 9) || (hour >= 17 && hour <= 19);
    }

    private boolean isOffPeak(int hour) {
        return hour < 6 || hour >= 22;
    }
}
