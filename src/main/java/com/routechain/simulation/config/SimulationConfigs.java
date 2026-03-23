package com.routechain.simulation.config;

import java.util.List;
import java.util.Map;

public class SimulationConfigs {

    public record ZoneProfile(
            String zoneId,
            String zoneType, // "OFFICE", "APARTMENT", "FOOD", "NIGHTLIFE", "CBD", "SUBURB"
            double baseOrderRatePerMin,
            double baseDriverSupply,
            double apartmentWeight,
            double officeWeight,
            double foodWeight,
            double nightlifeWeight,
            double floodSensitivity,
            double congestionSensitivity
    ) {}

    public record HourProfile(
            int hour,
            double demandMultiplier,
            double driverOnlineMultiplier,
            double officeZoneMultiplier,
            double apartmentZoneMultiplier,
            double foodZoneMultiplier
    ) {}

    public record WeatherEffects(
            String weatherCondition,
            double demandEffectApartment,
            double demandEffectOffice,
            double demandEffectFood,
            double driverAvailabilityEffect,
            double speedReduction,
            double cancellationUplift,
            double pickupDelayUplift
    ) {}

    public record ScenarioShock(
            String id,
            long startTick,
            long duration,
            List<String> affectedZones,
            double demandShock,
            double supplyShock,
            double trafficShock,
            double weatherShock,
            double incidentShock
    ) {}

    public record CalibrationTargets(
            double completionRate,
            double cancellationRate,
            double driverUtilization,
            double deadheadRatio,
            double avgEta,
            double backlogRatio,
            double surgeFrequency
    ) {}
}
