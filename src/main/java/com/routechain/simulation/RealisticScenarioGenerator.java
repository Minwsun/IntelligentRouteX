package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a reproducible set of HCMC-style operating buckets for benchmark runs.
 */
public final class RealisticScenarioGenerator {

    public enum ScenarioBucket {
        MORNING_OFF_PEAK,
        LUNCH_PEAK,
        AFTERNOON_OFF_PEAK,
        DINNER_PEAK,
        NIGHT_OFF_PEAK,
        HEAVY_RAIN_LUNCH,
        WEEKEND_DEMAND_SPIKE,
        SHORTAGE_REGIME
    }

    public record RealisticScenarioSpec(
            ScenarioBucket bucket,
            String scenarioName,
            int startHour,
            int startMinute,
            int ticks,
            int drivers,
            double demandMultiplier,
            double trafficIntensity,
            WeatherProfile weatherProfile,
            long seed
    ) { }

    public List<RealisticScenarioSpec> generate(int runsPerBucket, long baseSeed) {
        int safeRunsPerBucket = Math.max(1, runsPerBucket);
        List<RealisticScenarioSpec> specs = new ArrayList<>(ScenarioBucket.values().length * safeRunsPerBucket);
        for (ScenarioBucket bucket : ScenarioBucket.values()) {
            for (int runIndex = 0; runIndex < safeRunsPerBucket; runIndex++) {
                long seed = baseSeed + bucket.ordinal() * 10_000L + runIndex * 97L;
                Random random = new Random(seed);
                specs.add(createSpec(bucket, runIndex, seed, random));
            }
        }
        return specs;
    }

    public void configureShocks(RealisticScenarioSpec spec, ScenarioShockEngine shockEngine) {
        if (spec == null || shockEngine == null) {
            return;
        }
        switch (spec.bucket()) {
            case MORNING_OFF_PEAK -> {
                shockEngine.triggerLocalBurst(List.of("q1"), 0.65, 120, 240, 180.0);
                shockEngine.triggerLocalBurst(List.of("bt"), 0.40, 480, 180, 140.0);
            }
            case LUNCH_PEAK -> {
                shockEngine.triggerLocalBurst(List.of("q1", "q7", "bt"), 1.60, 90, 420, 210.0);
                shockEngine.triggerMultiZoneWave(List.of("q1", "bt", "q3"), List.of(0L, 18L, 36L), 0.95, 120, 360, 170.0);
            }
            case AFTERNOON_OFF_PEAK -> {
                shockEngine.triggerLocalBurst(List.of("q7"), 0.55, 240, 210, 160.0);
            }
            case DINNER_PEAK -> {
                shockEngine.triggerMultiZoneWave(List.of("q1", "q3", "q7", "bt"), List.of(0L, 12L, 24L, 36L), 1.35, 60, 540, 220.0);
                shockEngine.triggerLocalBurst(List.of("q10"), 0.90, 300, 240, 170.0);
            }
            case NIGHT_OFF_PEAK -> {
                shockEngine.triggerLocalBurst(List.of("q1"), 0.45, 180, 180, 120.0);
            }
            case HEAVY_RAIN_LUNCH -> {
                shockEngine.triggerLocalBurst(List.of("q1", "bt"), 1.25, 120, 360, 210.0);
                shockEngine.triggerWeatherSpike(90, 540, 1.12, 1.18, 1.30);
            }
            case WEEKEND_DEMAND_SPIKE -> {
                shockEngine.triggerMultiZoneWave(List.of("q1", "q3", "bt", "q7"), List.of(0L, 10L, 20L, 30L), 1.70, 60, 600, 240.0);
                shockEngine.triggerLocalBurst(List.of("q10"), 1.10, 210, 240, 150.0);
            }
            case SHORTAGE_REGIME -> {
                shockEngine.triggerMultiZoneWave(List.of("q1", "q3", "q7"), List.of(0L, 20L, 40L), 1.10, 120, 540, 240.0);
                shockEngine.triggerWeatherSpike(240, 300, 1.04, 1.08, 1.12);
            }
        }
    }

    private RealisticScenarioSpec createSpec(ScenarioBucket bucket, int runIndex, long seed, Random random) {
        return switch (bucket) {
            case MORNING_OFF_PEAK -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-morning-off-peak-run" + runIndex,
                    8,
                    0,
                    1200,
                    varyDrivers(42, random, 2),
                    varyDouble(0.80, random, 0.05),
                    varyDouble(0.25, random, 0.04),
                    WeatherProfile.CLEAR,
                    seed
            );
            case LUNCH_PEAK -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-lunch-peak-run" + runIndex,
                    11,
                    0,
                    1200,
                    varyDrivers(50, random, 3),
                    varyDouble(1.80, random, 0.12),
                    varyDouble(0.65, random, 0.05),
                    WeatherProfile.CLEAR,
                    seed
            );
            case AFTERNOON_OFF_PEAK -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-afternoon-off-peak-run" + runIndex,
                    14,
                    0,
                    1200,
                    varyDrivers(40, random, 2),
                    varyDouble(0.90, random, 0.05),
                    varyDouble(0.35, random, 0.04),
                    WeatherProfile.CLEAR,
                    seed
            );
            case DINNER_PEAK -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-dinner-peak-run" + runIndex,
                    17,
                    30,
                    1200,
                    varyDrivers(52, random, 3),
                    varyDouble(2.00, random, 0.12),
                    varyDouble(0.75, random, 0.05),
                    WeatherProfile.CLEAR,
                    seed
            );
            case NIGHT_OFF_PEAK -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-night-off-peak-run" + runIndex,
                    21,
                    0,
                    1200,
                    varyDrivers(38, random, 2),
                    varyDouble(1.10, random, 0.07),
                    varyDouble(0.30, random, 0.04),
                    WeatherProfile.CLEAR,
                    seed
            );
            case HEAVY_RAIN_LUNCH -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-heavy-rain-lunch-run" + runIndex,
                    11,
                    0,
                    1200,
                    varyDrivers(48, random, 3),
                    varyDouble(1.60, random, 0.10),
                    varyDouble(0.85, random, 0.05),
                    WeatherProfile.HEAVY_RAIN,
                    seed
            );
            case WEEKEND_DEMAND_SPIKE -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-weekend-demand-spike-run" + runIndex,
                    18,
                    0,
                    1200,
                    varyDrivers(56, random, 4),
                    varyDouble(2.50, random, 0.15),
                    varyDouble(0.70, random, 0.05),
                    WeatherProfile.CLEAR,
                    seed
            );
            case SHORTAGE_REGIME -> new RealisticScenarioSpec(
                    bucket,
                    "realistic-hcmc-shortage-regime-run" + runIndex,
                    12,
                    0,
                    1200,
                    varyDrivers(28, random, 2),
                    varyDouble(1.40, random, 0.08),
                    varyDouble(0.55, random, 0.05),
                    WeatherProfile.LIGHT_RAIN,
                    seed
            );
        };
    }

    private int varyDrivers(int baseline, Random random, int spread) {
        return Math.max(8, baseline + random.nextInt(spread * 2 + 1) - spread);
    }

    private double varyDouble(double baseline, Random random, double spread) {
        double offset = (random.nextDouble() * 2.0 - 1.0) * spread;
        return Math.max(0.05, baseline + offset);
    }
}
