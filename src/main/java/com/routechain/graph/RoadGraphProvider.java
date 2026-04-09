package com.routechain.graph;

import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

/**
 * Open-source road graph abstraction used by route scoring.
 *
 * <p>The current default implementation provides an OSRM-like baseline using
 * OSM-shaped geometry assumptions and leaves live traffic estimation to the
 * internal streaming feature layer.
 */
public interface RoadGraphProvider {
    RoadGraphSnapshot snapshot(String runId,
                               String serviceTier,
                               GeoPoint driverPoint,
                               GeoPoint pickupPoint,
                               GeoPoint dropPoint,
                               SpatiotemporalField field,
                               double trafficIntensity,
                               WeatherProfile weather);
}
