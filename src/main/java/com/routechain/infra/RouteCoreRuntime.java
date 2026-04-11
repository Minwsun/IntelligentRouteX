package com.routechain.infra;

import com.routechain.simulation.SimulationEngine;

/**
 * Authoritative live dispatch runtime for the desktop control-room.
 *
 * This keeps one shared SimulationEngine for the JavaFX app so the map UI
 * always talks to the real dispatch core instead of spinning up its own copy.
 *
 * Benchmark and certification runners may still create isolated engines on
 * purpose, but the live product surface must come through this facade.
 */
public final class RouteCoreRuntime {
    private static final Object LOCK = new Object();
    private static volatile SimulationEngine liveEngine;

    private RouteCoreRuntime() {}

    public static SimulationEngine liveEngine() {
        SimulationEngine existing = liveEngine;
        if (existing != null) {
            return existing;
        }
        synchronized (LOCK) {
            if (liveEngine == null) {
                liveEngine = new SimulationEngine();
            }
            return liveEngine;
        }
    }

    public static void stopLiveEngine() {
        SimulationEngine existing = liveEngine;
        if (existing != null) {
            existing.stop();
        }
    }
}
