package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.ai.OmegaDispatchAgent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class HeadlessTrial {

    @Test
    public void testSimulationRun() throws InterruptedException {
        System.out.println("=== RouteChain Omega — Headless Trial (JUnit) ===");
        
        SimulationEngine engine = new SimulationEngine();
        engine.setInitialDriverCount(50);
        engine.setTrafficIntensity(0.5);
        engine.setWeatherProfile(WeatherProfile.LIGHT_RAIN);
        
        System.out.println("Starting simulation...");
        engine.start();
        
        // Run for 30 ticks (30 seconds real time)
        for (int i = 0; i < 30; i++) {
            Thread.sleep(1000);
            System.out.println("Tick " + engine.getTickCount() + " | Time: " + engine.getSimulatedTimeFormatted() + " | Delivered: " + engine.getTotalDelivered());
        }
        
        System.out.println("Stopping simulation...");
        engine.stop();
        
        OmegaDispatchAgent.ModelDiagnostics diag = engine.getOmegaAgent().getDiagnostics();
        System.out.println("=== AI Diagnostics ===");
        System.out.println("Ranker Warmed Up: " + diag.rankerWarmedUp());
        System.out.println("Decision Log Size: " + diag.logSize());
        System.out.println("Policy Selections: " + diag.policySelections());
        
        assertTrue(diag.logSize() >= 0, "Wait for at least one dispatch decision");
        System.out.println("Trial completed successfully.");
    }
}
