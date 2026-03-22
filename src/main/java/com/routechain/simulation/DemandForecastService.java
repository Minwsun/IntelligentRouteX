package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * Layer 2 — Demand Forecast Service.
 * Predicts demand per zone at +5m, +10m, +15m using lightweight features:
 * time-of-day, recent inflow, traffic, weather, historical density.
 */
public class DemandForecastService {

    /**
     * Update all zone forecasts based on current simulation state.
     */
    public void updateForecasts(List<Region> zones, List<Order> activeOrders,
                                List<Driver> drivers, int simulatedHour,
                                double trafficIntensity, WeatherProfile weather) {
        // Count orders and drivers per zone
        Map<String, Integer> ordersByZone = new HashMap<>();
        Map<String, Integer> driversByZone = new HashMap<>();

        for (Order order : activeOrders) {
            for (Region zone : zones) {
                if (zone.contains(order.getPickupPoint())) {
                    ordersByZone.merge(zone.getId(), 1, Integer::sum);
                    break;
                }
            }
        }

        for (Driver driver : drivers) {
            if (driver.getState() == DriverState.OFFLINE) continue;
            for (Region zone : zones) {
                if (zone.contains(driver.getCurrentLocation())) {
                    driversByZone.merge(zone.getId(), 1, Integer::sum);
                    break;
                }
            }
        }

        double hourlyMultiplier = HcmcCityData.hourlyMultiplier(simulatedHour);
        Map<String, Double> baseRates = HcmcCityData.baseDemandRates();

        for (Region zone : zones) {
            int currentOrders = ordersByZone.getOrDefault(zone.getId(), 0);
            int currentDrivers = driversByZone.getOrDefault(zone.getId(), 0);
            double baseRate = baseRates.getOrDefault(zone.getId(), 0.15);

            // Update supply/density
            double areaKm2 = Math.PI * Math.pow(zone.getRadiusMeters() / 1000.0, 2);
            zone.setDriverDensity(currentDrivers / Math.max(0.1, areaKm2));

            // Forecast: base rate × hourly × recent trend × weather/traffic
            double weatherFactor = switch (weather) {
                case CLEAR -> 1.0;
                case LIGHT_RAIN -> 1.15;
                case HEAVY_RAIN -> 1.35;
                case STORM -> 1.6;
            };
            double trafficFactor = 1.0 + trafficIntensity * 0.3;

            // Recent growth factor (if zone has more orders than typical)
            double growthFactor = Math.max(0.5,
                    Math.min(2.0, currentOrders / Math.max(1, baseRate * hourlyMultiplier * 5)));

            double predicted5m = baseRate * hourlyMultiplier * weatherFactor * growthFactor * 5;
            double predicted10m = predicted5m * 1.8; // slightly less than 2× (some overlap)
            double predicted15m = predicted5m * 2.5;

            // Future hour might change demand pattern
            int futureHour = (simulatedHour + (predicted15m > 20 ? 1 : 0)) % 24;
            double futureMultiplier = HcmcCityData.hourlyMultiplier(futureHour);
            if (futureMultiplier > hourlyMultiplier * 1.3) {
                predicted10m *= 1.2;
                predicted15m *= 1.3;
            }

            zone.setPredictedDemand5m(predicted5m * trafficFactor);
            zone.setPredictedDemand10m(predicted10m * trafficFactor);
            zone.setPredictedDemand15m(predicted15m * trafficFactor);

            // Spike probability
            double spike = 0;
            if (currentOrders > baseRate * hourlyMultiplier * 8) spike += 0.4;
            if (growthFactor > 1.5) spike += 0.25;
            if (weather == WeatherProfile.STORM) spike += 0.2;
            if (zone.getShortageRatio() > 0.6) spike += 0.15;
            zone.setSpikeProbability(Math.min(1.0, spike));

            // Exit traffic penalty
            zone.setExitTrafficPenalty(
                    zone.getCongestionScore() * 0.6 + trafficIntensity * 0.4);
        }
    }

    /**
     * Get top N zones predicted to have demand surges.
     */
    public List<Region> getHotZones(List<Region> zones, int topN) {
        return zones.stream()
                .sorted(Comparator.comparingDouble(Region::getSpikeProbability).reversed())
                .filter(z -> z.getSpikeProbability() > 0.2)
                .limit(topN)
                .toList();
    }
}
