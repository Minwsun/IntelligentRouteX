package com.routechain.v2.context;

public record WeatherContext(
        WeatherState weatherState,
        double rainProbability15m,
        double rainIntensityMmPerHour,
        double visibilityMeters,
        double windSpeed10mMetersPerSecond,
        boolean thunderOrStormAlert,
        boolean badSignal) {
}
