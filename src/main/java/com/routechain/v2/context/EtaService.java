package com.routechain.v2.context;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.graph.OsmOsrmGraphProvider;
import com.routechain.graph.RoadGraphProvider;
import com.routechain.graph.RoadGraphSnapshot;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;

public final class EtaService {
    private static final ZoneId ASIA_SAIGON = ZoneId.of("Asia/Saigon");
    private final RoadGraphProvider roadGraphProvider;
    private final TrafficProfileService trafficProfileService;
    private final WeatherContextService weatherContextService;
    private final TomTomTrafficRefineClient tomTomTrafficRefineClient;

    public EtaService(RouteChainDispatchV2Properties properties) {
        this(new OsmOsrmGraphProvider(), properties);
    }

    public EtaService(RoadGraphProvider roadGraphProvider, RouteChainDispatchV2Properties properties) {
        this.roadGraphProvider = roadGraphProvider;
        this.trafficProfileService = new TrafficProfileService();
        this.weatherContextService = new WeatherContextService(properties);
        this.tomTomTrafficRefineClient = new TomTomTrafficRefineClient(properties.getTomtom());
    }

    public EtaEstimate estimate(GeoPoint start,
                                GeoPoint end,
                                Instant departureTime,
                                WeatherProfile weatherProfile,
                                double trafficIntensity,
                                boolean allowTomTomRefine,
                                String serviceTier) {
        GeoPoint safeStart = start == null ? end : start;
        GeoPoint safeEnd = end == null ? safeStart : end;
        Instant safeDeparture = departureTime == null ? Instant.now() : departureTime;
        WeatherProfile safeWeather = weatherProfile == null ? WeatherProfile.CLEAR : weatherProfile;
        RoadGraphSnapshot snapshot = roadGraphProvider.snapshot(
                "dispatch-v2",
                serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier,
                safeStart,
                safeEnd,
                safeEnd,
                null,
                trafficIntensity,
                safeWeather);
        double freeflowMinutes = Math.max(0.0, snapshot.approachCorridor().baselineTravelMinutes());
        String corridorId = snapshot.approachCorridor().corridorId();
        double corridorCongestionScore = snapshot.approachCorridor().congestionScore();
        double travelTimeDrift = snapshot.approachDrift().driftRatio();
        double trafficMultiplier = trafficProfileService.multiplier(corridorId, safeDeparture, trafficIntensity);
        WeatherContext weatherContext = weatherContextService.resolve(safeEnd, safeWeather);
        double weatherMultiplier = weatherContextService.multiplier(weatherContext.weatherState());
        String cacheKey = corridorId + ":" + safeDeparture.atZone(ASIA_SAIGON).getDayOfWeek().name().toLowerCase(Locale.ROOT)
                + ":" + safeDeparture.atZone(ASIA_SAIGON).getHour();
        double refineMultiplier = allowTomTomRefine
                ? tomTomTrafficRefineClient.refineMultiplier(cacheKey, trafficMultiplier)
                : 1.0;
        double etaMinutes = freeflowMinutes * trafficMultiplier * weatherMultiplier * refineMultiplier;
        double uncertainty = clamp01(
                corridorCongestionScore * 0.30
                        + travelTimeDrift * 0.25
                        + weatherContext.weatherState().severity() * 0.25
                        + Math.max(0.0, trafficMultiplier - 1.0) * 0.20);
        TrafficState trafficState = trafficProfileService.classify(trafficMultiplier * refineMultiplier, corridorCongestionScore);
        return new EtaEstimate(
                Math.max(0.0, etaMinutes),
                uncertainty,
                corridorId,
                trafficState,
                weatherContext.weatherState(),
                trafficMultiplier,
                weatherMultiplier,
                refineMultiplier,
                corridorCongestionScore,
                travelTimeDrift);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
