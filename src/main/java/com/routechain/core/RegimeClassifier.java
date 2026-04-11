package com.routechain.core;

import com.routechain.domain.Enums.WeatherProfile;

public class RegimeClassifier {

    public Result classify(double trafficIntensity,
                           WeatherProfile weatherProfile,
                           int simulatedHour,
                           int pendingOrderCount,
                           int availableDriverCount) {
        double densityRatio = pendingOrderCount / Math.max(1.0, availableDriverCount);
        if (weatherProfile == WeatherProfile.HEAVY_RAIN || weatherProfile == WeatherProfile.STORM) {
            return new Result(RegimeKey.RAIN_STRESS, 0.92);
        }
        if ((simulatedHour < 8 || simulatedHour >= 22) && densityRatio < 1.15) {
            return new Result(RegimeKey.OFFPEAK_LOW_DENSITY, 0.74);
        }
        if (densityRatio >= 1.8 || trafficIntensity >= 0.72) {
            return new Result(RegimeKey.CLEAR_SHORTAGE, 0.82);
        }
        return new Result(RegimeKey.CLEAR_NORMAL, 0.88);
    }

    public record Result(RegimeKey regimeKey, double confidence) {
        public RegimeKey resolvedKey() {
            return confidence >= 0.55 ? regimeKey : RegimeKey.CLEAR_NORMAL;
        }
    }
}
