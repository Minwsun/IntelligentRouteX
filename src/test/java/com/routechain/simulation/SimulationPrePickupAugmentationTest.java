package com.routechain.simulation;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import org.junit.jupiter.api.Test;

import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationPrePickupAugmentationTest {

    @Test
    void onCorridorOrderCanAugmentBeforeFirstPickup() {
        SimulationEngine engine = new SimulationEngine();
        engine.setDispatchMode(SimulationEngine.DispatchMode.OMEGA);
        engine.setInitialDriverCount(0);
        engine.setDemandMultiplier(0.0);
        engine.setTrafficIntensity(0.35);
        engine.setWeatherProfile(WeatherProfile.CLEAR);
        engine.setRouteLatencyMode(SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC);

        engine.tickHeadless();

        engine.injectDriver(new GeoPoint(10.7765, 106.7009));
        injectCompactOrder(engine, new GeoPoint(10.7769, 106.7013), new GeoPoint(10.7822, 106.7076));
        injectCompactOrder(engine, new GeoPoint(10.7770, 106.7014), new GeoPoint(10.7825, 106.7079));
        injectCompactOrder(engine, new GeoPoint(10.7771, 106.7015), new GeoPoint(10.7828, 106.7082));
        tagManualOrders(engine, "MERCHANT-AUG", "CLUSTER-AUG");

        Driver driver = engine.getDrivers().stream()
                .filter(d -> d.getId().startsWith("DMANUAL-"))
                .findFirst()
                .orElseThrow();

        boolean enteredPrePickupWindow = false;
        for (int i = 0; i < 180; i++) {
            engine.tickHeadless();
            if (driver.isPrePickupAugmentable() && driver.getCurrentOrderCount() >= 3) {
                enteredPrePickupWindow = true;
                break;
            }
        }

        assertTrue(enteredPrePickupWindow,
                "Driver should enter a pre-pickup augmentation window with an initial 3-order wave");

        injectCompactOrder(engine, new GeoPoint(10.77705, 106.70145), new GeoPoint(10.7830, 106.7084));
        tagManualOrders(engine, "MERCHANT-AUG", "CLUSTER-AUG");

        boolean augmentedBeforeFirstPickup = false;
        for (int i = 0; i < 150; i++) {
            engine.tickHeadless();
            if (driver.getCurrentOrderCount() >= 4 && !driver.hasCompletedFirstPickup()) {
                augmentedBeforeFirstPickup = true;
                break;
            }
        }

        assertTrue(augmentedBeforeFirstPickup,
                "A nearby on-corridor order should be merged before the first pickup is completed");

        RunReport report = engine.createRunReport("pre-pickup-augmentation-test", 42L);
        assertTrue(report.prePickupAugmentRate() > 0.0,
                "Run report should expose at least one successful pre-pickup augmentation");
    }

    private static void injectCompactOrder(SimulationEngine engine, GeoPoint pickup, GeoPoint dropoff) {
        engine.injectOrder(pickup, dropoff, 42000, 70);
    }

    private static void tagManualOrders(SimulationEngine engine, String merchantId, String clusterId) {
        engine.getActiveOrders().stream()
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .forEach(order -> {
                    order.setMerchantId(merchantId);
                    order.setPickupClusterId(clusterId);
                    order.setPickupDelayHazard(0.04);
                });
    }
}
