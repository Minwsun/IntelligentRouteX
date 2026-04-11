package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

import java.util.List;

/**
 * Shared JavaFX demo scenario contract so the desktop UI and validation lanes
 * consume exactly the same seeded manual setup.
 */
public record JavaFxDemoScenarioSpec(
        String scenarioName,
        long seed,
        int startHour,
        int startMinute,
        int tickBudget,
        int initialDriverCount,
        double demandMultiplier,
        double trafficIntensity,
        WeatherProfile weatherProfile,
        List<ManualDriverSpawn> drivers,
        List<ManualOrderSpawn> orders
) {
    public JavaFxDemoScenarioSpec {
        drivers = drivers == null ? List.of() : List.copyOf(drivers);
        orders = orders == null ? List.of() : List.copyOf(orders);
    }

    public int driverCount() {
        return drivers.size();
    }

    public int orderCount() {
        return orders.size();
    }

    public void applyTo(SimulationEngine engine) {
        configureEngine(engine);
        injectManualEntities(engine);
    }

    public void configureEngine(SimulationEngine engine) {
        engine.setSimulationStartTime(startHour, startMinute);
        engine.setInitialDriverCount(initialDriverCount);
        engine.setDemandMultiplier(demandMultiplier);
        engine.setTrafficIntensity(trafficIntensity);
        engine.setWeatherProfile(weatherProfile);
    }

    public void injectManualEntities(SimulationEngine engine) {
        for (ManualDriverSpawn driver : drivers) {
            engine.injectDriver(driver.location());
        }
        for (ManualOrderSpawn order : orders) {
            engine.injectOrder(order.pickup(), order.dropoff(), order.fee(), order.promisedEtaMin());
        }
    }

    public record ManualDriverSpawn(
            String label,
            GeoPoint location
    ) { }

    public record ManualOrderSpawn(
            String label,
            GeoPoint pickup,
            GeoPoint dropoff,
            double fee,
            int promisedEtaMin
    ) { }
}
