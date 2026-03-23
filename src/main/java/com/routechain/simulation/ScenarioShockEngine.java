package com.routechain.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages dynamic campaign shocks, local bursts, and multi-zone waves.
 */
public class ScenarioShockEngine {

    public enum ShockType {
        LOCAL_BURST,
        MULTI_ZONE_WAVE,
        WEATHER_SPIKE
    }

    public static class ActiveShock {
        public ShockType type;
        public List<String> affectedRegions;
        public long startTick;
        public long durationTicks;
        public double amplitude; // The 'A' in A * exp()
        public double decayTau;  // The 'tau'
        
        // Multi-zone sequenced offset per region
        public List<Long> offsets; 
        
        // Weather type differentiation
        public double apartmentMultMap, officeMultMap, foodMultMap;
    }

    private final List<ActiveShock> activeShocks = new CopyOnWriteArrayList<>();

    public void triggerLocalBurst(List<String> regions, double amplitude, long startTick, long duration, double tau) {
        ActiveShock shock = new ActiveShock();
        shock.type = ShockType.LOCAL_BURST;
        shock.affectedRegions = new ArrayList<>(regions);
        shock.startTick = startTick;
        shock.durationTicks = duration;
        shock.amplitude = amplitude;
        shock.decayTau = tau;
        activeShocks.add(shock);
    }

    public void triggerMultiZoneWave(List<String> regions, List<Long> offsets, double amplitude, long startTick, long duration, double tau) {
        ActiveShock shock = new ActiveShock();
        shock.type = ShockType.MULTI_ZONE_WAVE;
        shock.affectedRegions = new ArrayList<>(regions);
        shock.offsets = new ArrayList<>(offsets);
        shock.startTick = startTick;
        shock.durationTicks = duration;
        shock.amplitude = amplitude;
        shock.decayTau = tau;
        activeShocks.add(shock);
    }

    public void triggerWeatherSpike(long startTick, long duration, double aptMult, double offMult, double foodMult) {
        ActiveShock shock = new ActiveShock();
        shock.type = ShockType.WEATHER_SPIKE;
        shock.affectedRegions = new ArrayList<>(); // Empty = global
        shock.startTick = startTick;
        shock.durationTicks = duration;
        shock.apartmentMultMap = aptMult;
        shock.officeMultMap = offMult;
        shock.foodMultMap = foodMult;
        activeShocks.add(shock);
    }

    /**
     * Calculates the aggregated shock demand multiplier for a given region at a specific tick.
     */
    public double getDemandMultiplier(String regionId, String regionType, long currentTick) {
        double totalMutliplier = 1.0;

        List<ActiveShock> toRemove = new ArrayList<>();

        for (ActiveShock shock : activeShocks) {
            long elapsed = currentTick - shock.startTick;
            
            if (elapsed > shock.durationTicks) {
                toRemove.add(shock);
                continue;
            }

            if (elapsed < 0) continue; // Not started yet
            
            if (shock.type == ShockType.LOCAL_BURST) {
                if (shock.affectedRegions.contains(regionId)) {
                    // CampaignFactor = 1 + A * exp(-(t - t0)/tau)
                    double factor = shock.amplitude * Math.exp(- (double) elapsed / shock.decayTau);
                    totalMutliplier += factor;
                }
            } else if (shock.type == ShockType.MULTI_ZONE_WAVE) {
                int index = shock.affectedRegions.indexOf(regionId);
                if (index != -1) {
                    long regionElapsed = elapsed - shock.offsets.get(index);
                    if (regionElapsed >= 0 && regionElapsed <= shock.durationTicks) {
                         double factor = shock.amplitude * Math.exp(- (double) regionElapsed / shock.decayTau);
                         totalMutliplier += factor;
                    }
                }
            } else if (shock.type == ShockType.WEATHER_SPIKE) {
                // Simplified weather mapping based on type
                if ("APARTMENT".equalsIgnoreCase(regionType)) totalMutliplier *= shock.apartmentMultMap;
                else if ("OFFICE".equalsIgnoreCase(regionType)) totalMutliplier *= shock.officeMultMap;
                else if ("FOOD".equalsIgnoreCase(regionType)) totalMutliplier *= shock.foodMultMap;
            }
        }

        activeShocks.removeAll(toRemove);
        return totalMutliplier;
    }
}
