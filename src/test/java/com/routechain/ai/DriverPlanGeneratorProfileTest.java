package com.routechain.ai;

import com.routechain.domain.Driver;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.Enums.VehicleType;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Order;
import com.routechain.simulation.SelectionBucket;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverPlanGeneratorProfileTest {

    @Test
    void cleanDenseWorldProducesLaunchableThreeOrderWave() {
        Driver driver = new Driver(
                "DTEST-CLEAN-3",
                "Clean Dense Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        List<Order> orders = createCompactOrders("CLEAN", 3, "MERCHANT-CLEAN", "CLUSTER-CLEAN");
        DriverDecisionContext context = createContext(
                driver,
                orders,
                0.24,
                1.15,
                1.30,
                1.38,
                1.42,
                1.44,
                0.20,
                0.48,
                0.16,
                0.12,
                0.14,
                0.62,
                1.8,
                3,
                9.5,
                3,
                1,
                4,
                false,
                0.82,
                5.0,
                0.22,
                0.72,
                0.18,
                0.84,
                1.5,
                0.16,
                0.08);

        DriverPlanGenerator generator = new DriverPlanGenerator();
        generator.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

        List<com.routechain.simulation.DispatchPlan> plans =
                generator.generate(context, 0.24, WeatherProfile.CLEAR, 12);

        assertTrue(plans.stream().anyMatch(plan ->
                        plan.getBundleSize() >= 3
                                && plan.isWaveLaunchEligible()
                                && !plan.isStressFallbackOnly()
                                && !plan.isWaitingForThirdOrder()),
                "Clean dense world should expose a launchable 3-order wave");
        assertFalse(plans.stream().allMatch(com.routechain.simulation.DispatchPlan::isWaitingForThirdOrder),
                "Clean dense world should not collapse into hold-only output");
    }

    @Test
    void showcaseProfileAllowsLargerPickupWaveThanMainline() {
        GeoPoint driverLocation = new GeoPoint(10.7765, 106.7009);
        Driver driver = new Driver("DTEST-1", "Test Driver", driverLocation, "R1", VehicleType.MOTORBIKE);

        List<Order> orders = new ArrayList<>();
        Instant createdAt = Instant.parse("2026-03-23T09:00:00Z");
        for (int i = 0; i < 8; i++) {
            double lat = 10.7768 + i * 0.00018;
            double lng = 106.7011 + i * 0.00018;
            GeoPoint pickup = new GeoPoint(lat, lng);
            GeoPoint dropoff = new GeoPoint(10.7825 + i * 0.00012, 106.7080 + i * 0.00015);
            Order order = new Order(
                    "O-" + i,
                    "CUS-" + i,
                    "R1",
                    pickup,
                    dropoff,
                    "R2",
                    38000 + i * 1000,
                    75,
                    createdAt.plusSeconds(i * 10L));
            order.setMerchantId("MERCHANT-A");
            order.setPickupClusterId("CLUSTER-A");
            order.setPickupDelayHazard(0.05);
            orders.add(order);
        }

        DriverDecisionContext.OrderCluster cluster = new DriverDecisionContext.OrderCluster(
                "cluster-a",
                orders,
                new GeoPoint(10.7772, 106.7015),
                140.0,
                orders.stream().mapToDouble(Order::getQuotedFee).sum());

        DriverDecisionContext context = new DriverDecisionContext(
                driver,
                orders,
                List.of(cluster),
                0.25,
                1.2,
                1.4,
                1.5,
                1.5,
                1.4,
                0.30,
                0.34,
                0.12,
                0.28,
                0.22,
                0.68,
                0.25,
                0.4,
                0.2,
                0.1,
                0.1,
                0.82,
                0.18,
                0.6,
                2.0,
                6,
                12.0,
                6,
                1,
                8,
                false,
                0.88,
                7.5,
                0.84,
                0.8,
                0.25,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "C1",
                        new GeoPoint(10.7828, 106.7082),
                        0.82,
                        1.6,
                        0.18,
                        0.10)),
                List.of(),
                StressRegime.NORMAL);

        DriverPlanGenerator mainline = new DriverPlanGenerator();
        mainline.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

        DriverPlanGenerator showcase = new DriverPlanGenerator();
        showcase.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8);

        int mainlineMaxBundle = mainline.generate(context, 0.25, com.routechain.domain.Enums.WeatherProfile.CLEAR, 12)
                .stream()
                .mapToInt(plan -> plan.getBundleSize())
                .max()
                .orElse(0);

        int showcaseMaxBundle = showcase.generate(context, 0.25, com.routechain.domain.Enums.WeatherProfile.CLEAR, 12)
                .stream()
                .mapToInt(plan -> plan.getBundleSize())
                .max()
                .orElse(0);

        assertTrue(mainlineMaxBundle <= 4,
                "Mainline profile should cap bundle size at 4 or below");
        assertTrue(mainlineMaxBundle >= 3,
                "Mainline profile should visibly expose 3+ order pickup waves");
        assertTrue(showcaseMaxBundle >= 5,
                "Showcase profile should expose a visible 5+ order pickup wave");
    }

    @Test
    void heavyRainMainlineRejectsDirtyThreeOrderWave() {
        GeoPoint driverLocation = new GeoPoint(10.7765, 106.7009);
        Driver driver = new Driver("DTEST-2", "Rain Driver", driverLocation, "R1", VehicleType.MOTORBIKE);

        List<Order> orders = new ArrayList<>();
        Instant createdAt = Instant.parse("2026-03-23T09:00:00Z");
        for (int i = 0; i < 4; i++) {
            GeoPoint pickup = new GeoPoint(10.7768 + i * 0.00055, 106.7011 + i * 0.00058);
            GeoPoint dropoff = new GeoPoint(10.7825 + i * 0.00060, 106.7080 + i * 0.00062);
            Order order = new Order(
                    "RAIN-" + i,
                    "CUS-RAIN-" + i,
                    "R1",
                    pickup,
                    dropoff,
                    "R2",
                    36000 + i * 1200,
                    55,
                    createdAt.plusSeconds(i * 20L));
            order.setMerchantId("MERCHANT-" + i);
            order.setPickupClusterId(i < 2 ? "CLUSTER-A" : "CLUSTER-B");
            order.setPickupDelayHazard(0.35 + i * 0.08);
            orders.add(order);
        }

        DriverDecisionContext.OrderCluster cluster = new DriverDecisionContext.OrderCluster(
                "dirty-heavy-rain",
                orders,
                new GeoPoint(10.7776, 106.7021),
                920.0,
                orders.stream().mapToDouble(Order::getQuotedFee).sum());

        DriverDecisionContext context = new DriverDecisionContext(
                driver,
                orders,
                List.of(cluster),
                0.72,
                1.5,
                1.4,
                1.3,
                1.2,
                1.1,
                0.74,
                0.78,
                0.80,
                0.72,
                0.66,
                0.24,
                0.74,
                0.55,
                0.48,
                0.82,
                0.78,
                0.18,
                0.88,
                0.28,
                2.6,
                2,
                5.0,
                1,
                0,
                4,
                true,
                0.18,
                0.5,
                0.30,
                0.2,
                0.72,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "DIRTY",
                        new GeoPoint(10.7845, 106.7105),
                        0.25,
                        0.4,
                        0.82,
                        0.78)),
                List.of(),
                StressRegime.STRESS);

        DriverPlanGenerator generator = new DriverPlanGenerator();
        generator.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

        int heavyRainMaxBundle = generator.generate(context, 0.72, WeatherProfile.HEAVY_RAIN, 18)
                .stream()
                .mapToInt(plan -> plan.getBundleSize())
                .max()
                .orElse(0);

        assertTrue(heavyRainMaxBundle <= 2,
                "Heavy-rain mainline should avoid dirty 3+ order waves");
    }

    @Test
    void cleanDenseTwoOrderWorldStillOffersHoldPlanWhenThirdOrderLooksLikely() {
        Driver driver = new Driver(
                "DTEST-HOLD",
                "Hold Driver",
                new GeoPoint(10.7765, 106.7009),
                "R1",
                VehicleType.MOTORBIKE);

        List<Order> orders = createCompactOrders("HOLD", 2, "MERCHANT-HOLD", "CLUSTER-HOLD");
        DriverDecisionContext context = createContext(
                driver,
                orders,
                0.22,
                1.05,
                1.42,
                1.50,
                1.52,
                1.55,
                0.18,
                0.46,
                0.12,
                0.10,
                0.18,
                0.58,
                1.6,
                2,
                11.0,
                2,
                1,
                2,
                false,
                0.88,
                4.4,
                0.40,
                0.66,
                0.20,
                0.82,
                1.4,
                0.18,
                0.08);

        DriverPlanGenerator generator = new DriverPlanGenerator();
        generator.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

        List<com.routechain.simulation.DispatchPlan> plans =
                generator.generate(context, 0.22, WeatherProfile.CLEAR, 12);

        assertTrue(plans.stream().anyMatch(com.routechain.simulation.DispatchPlan::isWaitingForThirdOrder),
                "When a third order is likely, generator should keep a hold-for-third candidate");
        assertTrue(plans.stream().anyMatch(plan -> !plan.getOrders().isEmpty() && plan.getBundleSize() <= 2),
                "Hold criteria should coexist with actionable downgrade candidates, not replace them entirely");
    }

    @Test
    void cleanSparseWorldKeepsSubThreeLocalLaunchWithoutForcedHoldOnly() {
        GeoPoint driverLocation = new GeoPoint(10.7765, 106.7009);
        Driver driver = new Driver("DTEST-3", "Sparse Driver", driverLocation, "R1", VehicleType.MOTORBIKE);

        List<Order> orders = new ArrayList<>();
        Instant createdAt = Instant.parse("2026-03-23T09:00:00Z");
        for (int i = 0; i < 2; i++) {
            GeoPoint pickup = new GeoPoint(10.7775 + i * 0.00012, 106.7020 + i * 0.00013);
            GeoPoint dropoff = new GeoPoint(10.7845 + i * 0.00015, 106.7090 + i * 0.00017);
            Order order = new Order(
                    "SPARSE-" + i,
                    "CUS-SPARSE-" + i,
                    "R1",
                    pickup,
                    dropoff,
                    "R2",
                    38000 + i * 1500,
                    70,
                    createdAt.plusSeconds(i * 15L));
            order.setMerchantId("MERCHANT-SPARSE");
            order.setPickupClusterId("CLUSTER-SPARSE");
            order.setPickupDelayHazard(0.08);
            orders.add(order);
        }

        DriverDecisionContext.OrderCluster cluster = new DriverDecisionContext.OrderCluster(
                "sparse-cluster",
                orders,
                new GeoPoint(10.7776, 106.7021),
                120.0,
                orders.stream().mapToDouble(Order::getQuotedFee).sum());

        DriverDecisionContext context = new DriverDecisionContext(
                driver,
                orders,
                List.of(cluster),
                0.28,
                0.8,
                0.85,
                0.92,
                0.96,
                1.0,
                0.30,
                0.34,
                0.12,
                0.22,
                0.18,
                0.72,
                0.25,
                0.30,
                0.12,
                0.10,
                0.18,
                0.42,
                0.36,
                0.55,
                3.0,
                2,
                10.0,
                2,
                1,
                2,
                false,
                0.42,
                4.0,
                0.30,
                0.25,
                0.32,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "SPARSE-C1",
                        new GeoPoint(10.7848, 106.7092),
                        0.76,
                        0.9,
                        0.22,
                        0.10)),
                List.of(),
                StressRegime.NORMAL);

        DriverPlanGenerator generator = new DriverPlanGenerator();
        generator.setExecutionProfile(OmegaDispatchAgent.ExecutionProfile.MAINLINE_REALISTIC);

        List<com.routechain.simulation.DispatchPlan> plans = generator.generate(context, 0.28, WeatherProfile.CLEAR, 12);

        assertTrue(plans.stream().anyMatch(plan -> !plan.getOrders().isEmpty() && plan.getBundleSize() <= 2),
                "Sparse clean world should keep viable 1/2-order local launch plans");
        assertTrue(plans.stream().anyMatch(plan -> !plan.getOrders().isEmpty()
                        && plan.getSelectionBucket() == SelectionBucket.SINGLE_LOCAL),
                "Non-wave clean-regime plans should stay in the single-local lane instead of being mislabeled as fallback");
        assertFalse(plans.stream().allMatch(com.routechain.simulation.DispatchPlan::isWaitingForThirdOrder),
                "Sparse clean world should not collapse into hold-only plans");
    }

    private static List<Order> createCompactOrders(String prefix,
                                                   int count,
                                                   String merchantId,
                                                   String clusterId) {
        List<Order> orders = new ArrayList<>();
        Instant createdAt = Instant.parse("2026-03-23T09:00:00Z");
        for (int i = 0; i < count; i++) {
            GeoPoint pickup = new GeoPoint(10.7768 + i * 0.00012, 106.7011 + i * 0.00011);
            GeoPoint dropoff = new GeoPoint(10.7825 + i * 0.00014, 106.7080 + i * 0.00016);
            Order order = new Order(
                    prefix + "-" + i,
                    "CUS-" + prefix + "-" + i,
                    "R1",
                    pickup,
                    dropoff,
                    "R2",
                    38000 + i * 1200,
                    75,
                    createdAt.plusSeconds(i * 12L));
            order.setMerchantId(merchantId);
            order.setPickupClusterId(clusterId);
            order.setPickupDelayHazard(0.05);
            orders.add(order);
        }
        return orders;
    }

    private static DriverDecisionContext createContext(Driver driver,
                                                       List<Order> orders,
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
                                                       double corridorScore,
                                                       double demandSignal,
                                                       double congestionExposure,
                                                       double corridorWeatherExposure) {
        DriverDecisionContext.OrderCluster cluster = new DriverDecisionContext.OrderCluster(
                "cluster-" + driver.getId(),
                orders,
                new GeoPoint(10.7770, 106.7014),
                110.0,
                orders.stream().mapToDouble(Order::getQuotedFee).sum());

        return new DriverDecisionContext(
                driver,
                orders,
                List.of(cluster),
                localTrafficIntensity,
                localDemandIntensity,
                localDemandForecast5m,
                localDemandForecast10m,
                localDemandForecast15m,
                localDemandForecast30m,
                Math.min(1.0, localTrafficIntensity + 0.05),
                Math.min(1.0, localTrafficIntensity + 0.10),
                Math.min(1.0, localWeatherExposure + 0.05),
                Math.min(1.0, localShortagePressure + 0.08),
                Math.max(0.0, Math.min(1.0, localShortagePressure * 0.30 + localWeatherExposure * 0.20 + 0.05)),
                Math.max(0.0, Math.min(1.0, 0.78 - localShortagePressure * 0.35 - endZoneIdleRisk * 0.15
                        + localDriverDensity * 0.10)),
                localShortagePressure,
                localDriverDensity,
                localSpikeProbability,
                localWeatherExposure,
                localCorridorExposure,
                Math.max(0.0, Math.min(1.0, deliveryDemandGradient * 0.45 + 0.35)),
                Math.max(0.0, Math.min(1.0, endZoneIdleRisk * 0.65 + 0.10)),
                currentAttractionScore,
                estimatedIdleMinutes,
                nearReadyOrders,
                effectiveSlaSlackMinutes,
                nearReadySameMerchantCount,
                compactClusterCount,
                localReachableBacklog,
                harshWeatherStress,
                thirdOrderFeasibilityScore,
                threeOrderSlackBuffer,
                waveAssemblyPressure,
                deliveryDemandGradient,
                endZoneIdleRisk,
                List.of(new DriverDecisionContext.DropCorridorCandidate(
                        "C1",
                        new GeoPoint(10.7828, 106.7082),
                        corridorScore,
                        demandSignal,
                        congestionExposure,
                        corridorWeatherExposure)),
                List.of(),
                StressRegime.NORMAL);
    }
}
