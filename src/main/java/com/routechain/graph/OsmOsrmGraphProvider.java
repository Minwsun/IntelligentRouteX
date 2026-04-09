package com.routechain.graph;

import com.routechain.ai.SpatiotemporalField;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.simulation.TravelTimeMatrixSnapshot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Open-source-first road graph provider.
 *
 * <p>This provider does not call an external OSRM server. Instead it produces
 * an OSRM-shaped baseline from OSM-friendly geometry assumptions so the rest of
 * the route stack can consume a stable graph abstraction without vendor lock.
 */
public final class OsmOsrmGraphProvider implements RoadGraphProvider {
    private static final String BACKEND = "osm-osrm-surrogate-v1";
    private static final double BASE_SPEED_KMH = 23.0;

    @Override
    public RoadGraphSnapshot snapshot(String runId,
                                      String serviceTier,
                                      GeoPoint driverPoint,
                                      GeoPoint pickupPoint,
                                      GeoPoint dropPoint,
                                      SpatiotemporalField field,
                                      double trafficIntensity,
                                      WeatherProfile weather) {
        GeoPoint safeDriverPoint = driverPoint == null ? pickupPoint : driverPoint;
        GeoPoint safePickupPoint = pickupPoint == null ? safeDriverPoint : pickupPoint;
        GeoPoint safeDropPoint = dropPoint == null ? safePickupPoint : dropPoint;
        String pickupCellId = field == null ? "cell-unknown" : field.cellKeyOf(safePickupPoint);
        String dropCellId = field == null ? "cell-unknown" : field.cellKeyOf(safeDropPoint);
        String driverCellId = field == null ? pickupCellId : field.cellKeyOf(safeDriverPoint);

        CorridorLiveState approachCorridor = corridorState(
                "approach",
                driverCellId,
                pickupCellId,
                safeDriverPoint,
                safePickupPoint,
                field,
                trafficIntensity,
                weather);
        CorridorLiveState deliveryCorridor = corridorState(
                "delivery",
                pickupCellId,
                dropCellId,
                safePickupPoint,
                safeDropPoint,
                field,
                trafficIntensity,
                weather);
        ZoneFeatureSnapshot pickupZone = zoneSnapshot(pickupCellId, safePickupPoint, field, weather, trafficIntensity);
        ZoneFeatureSnapshot dropZone = zoneSnapshot(dropCellId, safeDropPoint, field, weather, trafficIntensity);
        TravelTimeDriftSnapshot approachDrift = driftSnapshot(approachCorridor);
        TravelTimeDriftSnapshot deliveryDrift = driftSnapshot(deliveryCorridor);

        String matrixId = (runId == null || runId.isBlank() ? "run-unset" : runId)
                + ":" + pickupCellId + ":" + dropCellId;
        Map<String, Double> minutes = new LinkedHashMap<>();
        minutes.put("driver->pickup", approachDrift.liveTravelMinutes());
        minutes.put("pickup->drop", deliveryDrift.liveTravelMinutes());
        Map<String, Double> distances = new LinkedHashMap<>();
        distances.put("driver->pickup", approachCorridor.baselineDistanceKm());
        distances.put("pickup->drop", deliveryCorridor.baselineDistanceKm());
        TravelTimeMatrixSnapshot matrix = new TravelTimeMatrixSnapshot(matrixId, BACKEND, minutes, distances);

        return new RoadGraphSnapshot(
                matrixId,
                BACKEND,
                serviceTier,
                pickupCellId,
                dropCellId,
                matrix,
                approachCorridor,
                deliveryCorridor,
                pickupZone,
                dropZone,
                approachDrift,
                deliveryDrift);
    }

    private CorridorLiveState corridorState(String prefix,
                                            String fromCellId,
                                            String toCellId,
                                            GeoPoint fromPoint,
                                            GeoPoint toPoint,
                                            SpatiotemporalField field,
                                            double trafficIntensity,
                                            WeatherProfile weather) {
        double distanceKm = fromPoint == null || toPoint == null
                ? 0.0
                : fromPoint.distanceTo(toPoint) / 1000.0;
        double topologyStretch = topologyStretchFactor(fromCellId, toCellId, distanceKm);
        double baselineMinutes = distanceKm <= 0.01
                ? 0.0
                : (distanceKm * topologyStretch / BASE_SPEED_KMH) * 60.0;
        double congestionScore = field == null
                ? clamp01(trafficIntensity)
                : clamp01(Math.max(
                trafficIntensity,
                Math.max(field.getCongestionExposureAt(fromPoint), field.getCongestionExposureAt(toPoint))));
        double weatherSeverity = clamp01(Math.max(
                weather.ordinal() / 3.0,
                field == null ? 0.0 : Math.max(field.getWeatherExposureAt(fromPoint), field.getWeatherExposureAt(toPoint))));
        double confidence = clamp01(0.78 - Math.min(0.24, distanceKm / 18.0));
        String corridorId = prefix + ":" + fromCellId + "->" + toCellId;
        return new CorridorLiveState(
                corridorId,
                fromCellId,
                toCellId,
                distanceKm,
                baselineMinutes,
                topologyStretch,
                congestionScore,
                weatherSeverity,
                confidence);
    }

    private ZoneFeatureSnapshot zoneSnapshot(String zoneId,
                                             GeoPoint point,
                                             SpatiotemporalField field,
                                             WeatherProfile weather,
                                             double trafficIntensity) {
        if (field == null || point == null) {
            return new ZoneFeatureSnapshot(
                    zoneId,
                    point,
                    0.0,
                    0.0,
                    clamp01(trafficIntensity),
                    clamp01(weather.ordinal() / 3.0),
                    0.0,
                    0.5,
                    clamp01(trafficIntensity),
                    0.0,
                    0.0);
        }
        double trafficForecast = clamp01(field.getTrafficForecastAt(point, 10));
        double weatherSeverity = clamp01(Math.max(weather.ordinal() / 3.0, field.getWeatherForecastAt(point, 10)));
        double postDropOpportunity = clamp01(field.getPostDropOpportunityAt(point, 10));
        double emptyRisk = clamp01(field.getEmptyZoneRiskAt(point, 10));
        double slowdownIndex = clamp01(
                trafficForecast * 0.46
                        + weatherSeverity * 0.22
                        + clamp01(field.getCommittedPickupPressureAt(point)) * 0.16
                        + clamp01(field.getShortageForecastAt(point, 10)) * 0.16);
        return new ZoneFeatureSnapshot(
                zoneId,
                point,
                Math.max(0.0, field.getDemandAt(point)),
                Math.max(0.0, field.getDriverDensityAt(point)),
                trafficForecast,
                weatherSeverity,
                postDropOpportunity,
                emptyRisk,
                slowdownIndex,
                clamp01(field.getRiskAdjustedAttractionAt(point)),
                clamp01(field.getCommittedPickupPressureAt(point)));
    }

    private TravelTimeDriftSnapshot driftSnapshot(CorridorLiveState corridor) {
        // Keep the open-source surrogate honest: in clear conditions the graph
        // should inform route choice without exaggerating friction so much that
        // the dispatcher becomes artificially conservative.
        double congestionWeight = 0.38 + corridor.weatherSeverity() * 0.17;
        double weatherWeight = 0.08 + corridor.weatherSeverity() * 0.14;
        double topologyWeight = 0.12 + corridor.weatherSeverity() * 0.06;
        double liveMultiplier = 1.0
                + corridor.congestionScore() * congestionWeight
                + corridor.weatherSeverity() * weatherWeight
                + Math.max(0.0, corridor.topologyStretchFactor() - 1.0) * topologyWeight;
        double liveTravelMinutes = corridor.baselineTravelMinutes() * liveMultiplier;
        double driftRatio = corridor.baselineTravelMinutes() <= 0.01
                ? 0.0
                : Math.max(0.0, (liveTravelMinutes - corridor.baselineTravelMinutes()) / corridor.baselineTravelMinutes());
        double confidence = clamp01(
                corridor.confidence()
                        - corridor.congestionScore() * 0.08
                        - corridor.weatherSeverity() * 0.04);
        return new TravelTimeDriftSnapshot(
                corridor.corridorId(),
                corridor.baselineTravelMinutes(),
                liveTravelMinutes,
                driftRatio,
                confidence);
    }

    private double topologyStretchFactor(String fromCellId, String toCellId, double distanceKm) {
        double base = 1.08;
        if (fromCellId == null || toCellId == null || fromCellId.equals(toCellId)) {
            base -= 0.06;
        }
        if (distanceKm > 3.5) {
            base += 0.08;
        }
        if (distanceKm > 6.0) {
            base += 0.06;
        }
        return clamp(base, 1.0, 1.35);
    }

    private double clamp01(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
