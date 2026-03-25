package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;

import java.util.List;

/**
 * Immutable snapshot of a driver's local world at decision time.
 */
public record DriverDecisionContext(
        Driver driver,
        List<Order> reachableOrders,
        List<OrderCluster> pickupClusters,
        double localTrafficIntensity,
        double localDemandIntensity,
        double localDemandForecast5m,
        double localDemandForecast10m,
        double localDemandForecast15m,
        double localDemandForecast30m,
        double localShortagePressure,
        double localDriverDensity,
        double localSpikeProbability,
        double localWeatherExposure,
        double localCorridorExposure,
        double currentAttractionScore,
        double estimatedIdleMinutes,
        int nearReadyOrders,
        double effectiveSlaSlackMinutes,
        int nearReadySameMerchantCount,
        int compactClusterCount,
        int localReachableBacklog,
        boolean harshWeatherStress,
        double thirdOrderFeasibilityScore,
        double threeOrderSlackBuffer,
        double waveAssemblyPressure,
        double deliveryDemandGradient,
        double endZoneIdleRisk,
        List<DropCorridorCandidate> dropCorridorCandidates,
        List<EndZoneCandidate> endZoneCandidates,
        StressRegime stressRegime
) {

    public record OrderCluster(
            String clusterId,
            List<Order> orders,
            GeoPoint centroid,
            double spreadMeters,
            double totalFee
    ) {}

    public record EndZoneCandidate(
            GeoPoint position,
            double attractionScore,
            double distanceKm,
            double weatherExposure,
            double corridorExposure
    ) {}

    public record DropCorridorCandidate(
            String corridorKey,
            GeoPoint anchorPoint,
            double corridorScore,
            double demandSignal,
            double congestionExposure,
            double weatherExposure
    ) {}
}
