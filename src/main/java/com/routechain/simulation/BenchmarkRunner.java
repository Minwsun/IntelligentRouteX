package com.routechain.simulation;

import com.routechain.simulation.config.SimulationConfigs;

/**
 * Headless runner for benchmarking the Dispatch AI (Phase 3).
 * Simulates a long headless run and prints aggregate metrics.
 */
public class BenchmarkRunner {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("  ROUTECHAIN AI — DISPATCH BENCHMARK RUNNER");
        System.out.println("=================================================");

        SimulationEngine engine = new SimulationEngine();
        
        // Setup initial scenario
        engine.setInitialDriverCount(50); // Give plenty of drivers to handle load
        engine.setDemandMultiplier(1.0);  // 100% realistic demand
        
        System.out.println("[Benchmark] Starting headless simulation...");
        long startTimeMs = System.currentTimeMillis();

        // Run 3600 sub-ticks (~5 simulated hours) synchronously
        int numTicks = 3600;
        for (int i = 0; i < numTicks; i++) {
            engine.tickHeadless();
            if ((i + 1) % 600 == 0) {
                System.out.println("  ... progress: "
                        + engine.getClock().getElapsedMinutes()
                        + " simulated minutes.");
            }
        }

        long elapsedMs = System.currentTimeMillis() - startTimeMs;
        System.out.println("[Benchmark] Simulation complete in " + elapsedMs + " ms.");
        System.out.println();
        
        // Gather and print metrics
        int totalDelivered = engine.getTotalDelivered();
        int totalLate = engine.getTotalLateDelivered();
        double onTimeRate = totalDelivered > 0 ? 
            100.0 * (totalDelivered - totalLate) / totalDelivered : 0;
            
        int totalAssigned = engine.getTotalAssignments();
        int totalBundled = engine.getTotalBundled();
        double bundleEfficiency = totalAssigned > 0 ? 
            100.0 * totalBundled / totalAssigned : 0;

        double totalEarnings = engine.getTotalEarnings();
        double avgDriverProfit = 50 > 0 ? totalEarnings / 50 : 0;
        
        double avgDeadheadKm = totalDelivered > 0 ? 
            engine.getTotalDeadheadKm() / totalDelivered : 0;

        System.out.println("=================================================");
        System.out.println("                 BENCHMARK RESULTS               ");
        System.out.println("=================================================");
        System.out.println("Total Orders Delivered : " + totalDelivered);
        System.out.printf("On-Time Rate (SLA)     : %.2f%%\n", onTimeRate);
        System.out.printf("Bundle Efficiency      : %.2f%%\n", bundleEfficiency);
        System.out.printf("Average Driver Profit  : %,.0f VND/hr\n", avgDriverProfit);
        System.out.printf("Average Deadhead       : %.2f km/order\n", avgDeadheadKm);
        System.out.println("Surge Events Detected  : " + engine.getSurgeEventsCounter());
        System.out.println("=================================================");
    }
}
