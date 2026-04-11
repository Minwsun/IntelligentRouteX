package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;

public enum CompactBenchmarkRegime {
    CLEAR_NORMAL("clear-normal", 12, 0.14, 0.28, WeatherProfile.CLEAR, 8, 360),
    CLEAR_SHORTAGE("clear-shortage", 12, 0.22, 0.34, WeatherProfile.CLEAR, 6, 420),
    OFFPEAK_LOW_DENSITY("offpeak-low-density", 22, 0.06, 0.18, WeatherProfile.CLEAR, 6, 360),
    LIGHT_RAIN_MEDIUM_STRESS("light-rain-medium-stress", 18, 0.16, 0.46, WeatherProfile.LIGHT_RAIN, 7, 420);

    private final String slug;
    private final int startHour;
    private final double demandMultiplier;
    private final double trafficIntensity;
    private final WeatherProfile weatherProfile;
    private final int initialDriverCount;
    private final int ticks;

    CompactBenchmarkRegime(String slug,
                           int startHour,
                           double demandMultiplier,
                           double trafficIntensity,
                           WeatherProfile weatherProfile,
                           int initialDriverCount,
                           int ticks) {
        this.slug = slug;
        this.startHour = startHour;
        this.demandMultiplier = demandMultiplier;
        this.trafficIntensity = trafficIntensity;
        this.weatherProfile = weatherProfile;
        this.initialDriverCount = initialDriverCount;
        this.ticks = ticks;
    }

    public String slug() {
        return slug;
    }

    public int startHour() {
        return startHour;
    }

    public double demandMultiplier() {
        return demandMultiplier;
    }

    public double trafficIntensity() {
        return trafficIntensity;
    }

    public WeatherProfile weatherProfile() {
        return weatherProfile;
    }

    public int initialDriverCount() {
        return initialDriverCount;
    }

    public int ticks() {
        return ticks;
    }
}
