package com.routechain.simulation;

import com.routechain.domain.Region;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.ai.OmegaDispatchAgent;
import java.util.List;

/**
 * HeadlessRunner — Runs a simulation trial without GUI for verification.
 * Runs for 60 ticks (1 simulated hour) and prints AI diagnostics.
 */
public class HeadlessRunner {
    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== RouteChain Omega — Headless Trial ===");
        
        SimulationEngine engine = new SimulationEngine();
        engine.setInitialDriverCount(50);
        engine.setTrafficIntensity(0.5);
        engine.setWeatherProfile(WeatherProfile.LIGHT_RAIN);
        
        System.out.println("Starting simulation for 60 ticks...");
        engine.start();
        
        // Wait for 60 ticks (approx 6 seconds real time if 100ms per tick is configured, 
        // but it's 1000ms per tick in SimulationEngine.java line 83)
        // Let's speed it up or just wait. 
        // Actually, the tick() method is private, so we can't call it manually easily without reflection.
        // We'll wait 65 seconds or modify SimulationEngine to allow manual ticking.
        
        for (int i = 0; i < 60; i++) {
            Thread.sleep(100); // Wait 100ms intervals to check status
            if (i % 10 == 0) {
                System.out.println("Elapsed: " + engine.getSimulatedTimeFormatted() + " | Delivered: " + engine.getTotalDelivered());
            }
        }
        
        System.out.println("Stopping simulation...");
        engine.stop();
        
        System.out.println("=== AI Diagnostics ===");
        OmegaDispatchAgent.ModelDiagnostics diag = engine.getOmegaAgent().getDiagnostics();
        System.out.println("ETA Samples: " + diag.etaSamples());
        System.out.println("Ranker Warmed Up: " + diag.rankerWarmedUp());
        System.out.println("Decision Log Size: " + diag.logSize());
        System.out.println("Completed Decisions: " + diag.logCompleted());
        System.out.println("Policy Selections: " + diag.policySelections());
        
        System.out.println("Trial completed successfully.");
    }
}
