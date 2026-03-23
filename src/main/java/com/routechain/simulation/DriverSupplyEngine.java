package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.DriverState;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Region;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events.DriverOnline;
import com.routechain.infra.Events.DriverStateChanged;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages driver shifts, online/offline behaviors, and archetypes.
 * Target Supply = baseSupply * shiftProfile * weatherAvailability * earningAttraction
 */
public class DriverSupplyEngine {

    public enum DriverArchetype {
        COMMUTER_HUNTER,      // Likes office zones, strict on earnings
        FOOD_CLUSTER_CAMPER,  // Stays near food regions, short trips
        APARTMENT_CAMPER,     // High patience, waiting for outbound
        OPPORTUNISTIC_MOVER,  // Chases surges across the map
        CONSERVATIVE_DRIVER   // Stable, unaffected much by weather/surge
    }

    private final Map<String, DriverArchetype> driverArchetypes = new ConcurrentHashMap<>();
    private final Map<String, Double> driverEarningsTarget = new ConcurrentHashMap<>();
    private final Random rng = new Random(42);

    private double manualSupplyMultiplier = 1.0;

    public void setManualSupplyMultiplier(double multiplier) {
        this.manualSupplyMultiplier = multiplier;
    }

    public void initializeDriver(Driver driver) {
        DriverArchetype[] types = DriverArchetype.values();
        DriverArchetype type = types[rng.nextInt(types.length)];
        driverArchetypes.put(driver.getId(), type);
        
        // Random target 300k - 800k VND
        double target = 300_000 + rng.nextDouble() * 500_000;
        driverEarningsTarget.put(driver.getId(), target);
    }

    public DriverArchetype getArchetype(String driverId) {
        return driverArchetypes.getOrDefault(driverId, DriverArchetype.CONSERVATIVE_DRIVER);
    }

    /**
     * Called at decision boundaries to evaluate driver online/offline shifts.
     */
    public void evaluateShifts(
            List<Driver> drivers,
            List<Region> regions,
            SimulationClock clock,
            WeatherProfile weatherProfile,
            double baseDemandMultiplier
    ) {
        int simulatedHour = clock.getSimulatedHour();
        // Shift arrivals typically happen on 15 or 30 min marks (or broadly distributed)
        // For simplicity, we process a probability per tick per driver

        double weatherAvailability = switch(weatherProfile) {
            case CLEAR, LIGHT_RAIN -> 1.0;
            case HEAVY_RAIN -> 0.85;
            case STORM -> 0.60;
        };

        double shiftProfile = getShiftMultiplier(simulatedHour);

        for (Driver driver : drivers) {
             boolean isOnline = driver.getState() != DriverState.OFFLINE;

             if (isOnline) {
                 // Check exit hazards (fatigue, target met, idle too long, weather)
                 double exitHazard = computeExitHazard(driver, weatherProfile);
                 
                 // Bernoulli trial for exit
                 if (rng.nextDouble() < exitHazard) {
                     // Can only go offline if not currently holding active orders
                     if (driver.getActiveOrderIds().isEmpty() && driver.getState() == DriverState.ONLINE_IDLE) {
                         DriverState oldState = driver.getState();
                         driver.setState(DriverState.OFFLINE);
                         EventBus.getInstance().publish(new DriverStateChanged(driver.getId(), oldState, DriverState.OFFLINE));
                     }
                 }
             } else {
                 // Offline driver might come online
                 // Mu is target supply minus current supply broadly.
                 // Simplification: Check target arrival probability
                 DriverArchetype archetype = getArchetype(driver.getId());
                 double arrivalProb = 0.005 * shiftProfile * weatherAvailability * manualSupplyMultiplier;
                 
                 // If archetype matches current hour peaks
                 if (archetype == DriverArchetype.COMMUTER_HUNTER && (simulatedHour == 8 || simulatedHour == 17)) arrivalProb *= 1.5;
                 if (archetype == DriverArchetype.FOOD_CLUSTER_CAMPER && (simulatedHour == 11 || simulatedHour == 19)) arrivalProb *= 1.5;
                 
                 if (rng.nextDouble() < arrivalProb) {
                     // Spawn online in a favorable region
                     Region targetRegion = selectFavorableRegion(driver, regions);
                     driver.setCurrentLocation(randomPointInRegion(targetRegion, rng));
                     driver.setState(DriverState.ONLINE_IDLE);
                     
                     // Reset some daily counters if it's a new shift
                     driver.setMicroDelayTicksRemaining(0);
                     driver.setQueueTicksRemaining(0);
                     
                     EventBus.getInstance().publish(new DriverStateChanged(driver.getId(), DriverState.OFFLINE, DriverState.ONLINE_IDLE));
                     EventBus.getInstance().publish(new DriverOnline(driver.getId()));
                 }
             }
        }
    }

    private double computeExitHazard(Driver driver, WeatherProfile weather) {
        double hazard = 0.0001; // Base small hazard per minute
        
        // Fatigue (online duration)
        if (driver.getOnlineTicks() > 8 * 60) {
            hazard += 0.002;
        }
        if (driver.getOnlineTicks() > 12 * 60) {
            hazard += 0.05; // Critical fatigue
        }

        // Target reached
        double target = driverEarningsTarget.getOrDefault(driver.getId(), 500_000.0);
        if (driver.getNetEarningToday() >= target) {
            hazard += 0.01;
        }

        // Idle too long
        if (driver.getIdleTicks() > 60) {
            hazard += 0.005;
        }

        // Weather intolerance
        DriverArchetype type = getArchetype(driver.getId());
        if (weather == WeatherProfile.HEAVY_RAIN && type != DriverArchetype.CONSERVATIVE_DRIVER) {
            hazard += 0.005;
        }
        if (weather == WeatherProfile.STORM) {
            hazard += (type == DriverArchetype.OPPORTUNISTIC_MOVER) ? 0.0 : 0.02; // Mover chases storm surges
        }

        return Math.min(1.0, hazard);
    }

    private Region selectFavorableRegion(Driver driver, List<Region> regions) {
        // Simple attraction: favor regions with high surge or base
        if (regions.isEmpty()) return null;
        return regions.stream()
                .max((r1, r2) -> Double.compare(
                        r1.getCurrentDemandPressure() + r1.getSurgeSeverity().ordinal(), 
                        r2.getCurrentDemandPressure() + r2.getSurgeSeverity().ordinal()))
                .orElse(regions.get(rng.nextInt(regions.size())));
    }

    private double getShiftMultiplier(int hour) {
        return switch (hour) {
            case 6, 7, 8, 9 -> 1.5;
            case 10, 14, 15 -> 1.0;
            case 11, 12, 13 -> 1.3;
            case 16, 17, 18 -> 1.8;
            case 19, 20, 21 -> 1.5;
            default -> 0.5;
        };
    }

    private GeoPoint randomPointInRegion(Region r, Random rng) {
        double lat = r.getCenter().lat() + (rng.nextDouble() - 0.5) * 0.02;
        double lng = r.getCenter().lng() + (rng.nextDouble() - 0.5) * 0.02;
        return new GeoPoint(lat, lng);
    }
}
