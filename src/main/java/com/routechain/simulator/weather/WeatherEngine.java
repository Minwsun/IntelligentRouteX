package com.routechain.simulator.weather;

import com.routechain.domain.WeatherProfile;
import com.routechain.simulator.calendar.StressModifier;
import com.routechain.simulator.calendar.WeatherMode;
import com.routechain.simulator.runtime.SimulatorRunConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
public class WeatherEngine {

    public WeatherSnapshot snapshot(SimulatorRunConfig config,
                                    boolean rainySeason,
                                    List<StressModifier> stressModifiers,
                                    Instant observedAt,
                                    long tickSeed) {
        WeatherMode mode = config.weatherMode();
        WeatherProfile profile;
        if (!config.weatherEnabled()) {
            profile = WeatherProfile.CLEAR;
        } else if (stressModifiers.contains(StressModifier.HEAVY_RAIN)) {
            profile = WeatherProfile.HEAVY_RAIN;
        } else if (mode == WeatherMode.DRY) {
            profile = WeatherProfile.CLEAR;
        } else if (mode == WeatherMode.RAINY || rainySeason) {
            profile = ((tickSeed & 1L) == 0L) ? WeatherProfile.LIGHT_RAIN : WeatherProfile.CLEAR;
        } else {
            profile = WeatherProfile.CLEAR;
        }
        return switch (profile) {
            case CLEAR -> new WeatherSnapshot(observedAt, profile, 1.0, 1.0, 1.0, 1.0, rainySeason ? "rainy-season" : "dry-season");
            case LIGHT_RAIN -> new WeatherSnapshot(observedAt, profile, 1.05, 0.94, 1.08, 1.03, rainySeason ? "rainy-season" : "forced-rain");
            case HEAVY_RAIN -> new WeatherSnapshot(observedAt, profile, 1.12, 0.84, 1.22, 1.08, "heavy-rain");
        };
    }
}
