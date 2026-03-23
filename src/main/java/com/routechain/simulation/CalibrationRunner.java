package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.config.SimulationConfigs.CalibrationTargets;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.List;

/**
 * Headless runner that executes the simulation across multiple scenarios
 * to compare output KPIs against configured realistic targets.
 */
public class CalibrationRunner {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Simulation Calibration Runner ===");
        System.out.println("Running 100 scenarios (180 ticks each)...");

        // Target KPIs from the Spec/Plan
        CalibrationTargets targets = new CalibrationTargets(
                85.0, // completion rate %
                5.0,  // cancellation rate %
                0.60, // driver utilization
                0.15, // deadhead ratio
                12.0, // avg ETA mins
                1.5,  // backlog ratio
                0.1   // surge frequency
        );

        SimulationEngine engine = new SimulationEngine();
        
        Method tickMethod = SimulationEngine.class.getDeclaredMethod("tick");
        tickMethod.setAccessible(true);
        
        Method initDriversMethod = SimulationEngine.class.getDeclaredMethod("initializeDrivers", int.class);
        initDriversMethod.setAccessible(true);

        List<Double> completionRates = new ArrayList<>();
        List<Double> cancellationRates = new ArrayList<>();
        List<Double> utilizations = new ArrayList<>();
        List<Double> deadheads = new ArrayList<>();

        int runs = 100;
        int ticksPerRun = 180;

        for (int i = 0; i < runs; i++) {
            engine.reset();
            
            // Setup Scenario Variation
            engine.setTrafficIntensity(0.3 + (Math.random() * 0.4));
            engine.setWeatherProfile(Math.random() > 0.8 ? WeatherProfile.HEAVY_RAIN : WeatherProfile.CLEAR);
            engine.setDemandMultiplier(1.0); // Base target
            
            // Initialize Drivers (simulate start of day)
            initDriversMethod.invoke(engine, 50); // Start with 50 drivers

            for (int t = 0; t < ticksPerRun; t++) {
                tickMethod.invoke(engine);
            }

            // Extract Metrics
            int delivered = engine.getTotalDelivered();
            int cancelled = engine.getTotalCancelled();
            int active = engine.getActiveOrders().size();
            int total = delivered + cancelled + active;

            if (total > 0) {
                completionRates.add(delivered * 100.0 / total);
                cancellationRates.add(cancelled * 100.0 / total);
            }

            double avgUtil = engine.getDrivers().stream()
                    .mapToDouble(d -> d.getComputedUtilization())
                    .average().orElse(0.0);
            utilizations.add(avgUtil);

            double avgDead = engine.getDrivers().stream()
                    .mapToDouble(d -> d.getDeadheadDistanceRatio())
                    .average().orElse(0.0);
            deadheads.add(avgDead);
        }

        // Print Calibration Report
        System.out.println("\n=== Calibration Report ===");
        printComparison("Completion Rate (%)", getAvg(completionRates), targets.completionRate());
        printComparison("Cancellation Rate (%)", getAvg(cancellationRates), targets.cancellationRate());
        printComparison("Avg Driver Utilization", getAvg(utilizations), targets.driverUtilization());
        printComparison("Avg Deadhead Ratio", getAvg(deadheads), targets.deadheadRatio());
        
        System.out.println("\nTuning recommendations: Adjust lambda multipliers, driver supply shift weights, " +
                "or cancellation Sigmoid base to align gaps.");
    }

    private static double getAvg(List<Double> list) {
        return list.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private static void printComparison(String metric, double actual, double target) {
        double diff = actual - target;
        System.out.printf("%-25s | Actual: %6.2f | Target: %6.2f | Delta: %6.2f%n", 
                metric, actual, target, diff);
    }
}
