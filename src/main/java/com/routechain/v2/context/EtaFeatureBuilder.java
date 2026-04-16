package com.routechain.v2.context;

import com.routechain.domain.GeoPoint;
import com.routechain.v2.DispatchV2Request;

import java.time.Instant;
import java.time.ZoneOffset;

public final class EtaFeatureBuilder {
    public EtaFeatureVector build(DispatchV2Request request,
                                  GeoPoint from,
                                  GeoPoint to,
                                  TrafficProfileSnapshot traffic,
                                  WeatherContextSnapshot weather,
                                  double baselineMinutes,
                                  double distanceKm) {
        return new EtaFeatureVector(
                "eta-feature-vector/v1",
                baselineMinutes,
                traffic.multiplier(),
                weather.multiplier(),
                distanceKm,
                hourOfDay(request == null ? null : request.decisionTime()));
    }

    private int hourOfDay(Instant instant) {
        Instant safeInstant = instant == null ? Instant.EPOCH : instant;
        return safeInstant.atZone(ZoneOffset.UTC).getHour();
    }
}

