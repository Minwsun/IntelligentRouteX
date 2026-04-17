package com.routechain.v2.integration;

import com.fasterxml.jackson.annotation.JsonProperty;

public record OpenMeteoForecastResponse(
        @JsonProperty("generationtime_ms")
        Double generationTimeMs,
        CurrentWeather current) {

    public record CurrentWeather(
            String time,
            @JsonProperty("weather_code")
            Integer weatherCode,
            Double rain) {
    }
}
