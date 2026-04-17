package com.routechain.v2.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.domain.GeoPoint;
import com.routechain.v2.context.WeatherSignalMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public final class HttpOpenMeteoClient implements OpenMeteoClient {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI baseUri;
    private final Duration readTimeout;
    private final WeatherSignalMapper weatherSignalMapper;

    public HttpOpenMeteoClient(String baseUrl,
                               Duration connectTimeout,
                               Duration readTimeout,
                               RouteChainDispatchV2Properties properties) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        this.objectMapper = JsonMapper.builder().findAndAddModules().build();
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        this.readTimeout = readTimeout;
        this.weatherSignalMapper = new WeatherSignalMapper(properties);
    }

    @Override
    public OpenMeteoSnapshot fetchForecast(GeoPoint point, Instant decisionTime) {
        return fetch(point, decisionTime, "forecast");
    }

    @Override
    public OpenMeteoSnapshot fetchHistorical(GeoPoint point, Instant decisionTime) {
        return fetch(point, decisionTime, "historical");
    }

    private OpenMeteoSnapshot fetch(GeoPoint point, Instant decisionTime, String mode) {
        if (point == null) {
            return OpenMeteoSnapshot.unavailable();
        }
        long startedAt = System.nanoTime();
        try {
            URI requestUri = buildUri(point, decisionTime, mode);
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .timeout(readTimeout)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return unavailable("open-meteo-http-error", startedAt);
            }
            OpenMeteoForecastResponse forecastResponse = objectMapper.readValue(response.body(), OpenMeteoForecastResponse.class);
            if (forecastResponse.current() == null || forecastResponse.current().weatherCode() == null || forecastResponse.current().time() == null) {
                return unavailable("open-meteo-malformed-payload", startedAt);
            }
            long sourceAgeMs = ageMs(forecastResponse.current().time(), decisionTime);
            WeatherSignalMapper.ResolvedWeatherSignal resolvedSignal = weatherSignalMapper.resolve(forecastResponse.current().weatherCode());
            return new OpenMeteoSnapshot(
                    true,
                    resolvedSignal.condition(),
                    resolvedSignal.multiplier(),
                    resolvedSignal.weatherBadSignal(),
                    sourceAgeMs,
                    confidence(forecastResponse),
                    latencyMs(startedAt),
                    "");
        } catch (java.net.http.HttpTimeoutException exception) {
            return unavailable("open-meteo-timeout", startedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable("open-meteo-interrupted", startedAt);
        } catch (IOException | RuntimeException exception) {
            return unavailable("open-meteo-unavailable", startedAt);
        }
    }

    private URI buildUri(GeoPoint point, Instant decisionTime, String mode) {
        StringBuilder builder = new StringBuilder(baseUri.toString());
        builder.append("v1/").append(mode)
                .append("?latitude=").append(point.latitude())
                .append("&longitude=").append(point.longitude())
                .append("&current=weather_code,rain");
        if (decisionTime != null) {
            builder.append("&time=").append(URLEncoder.encode(decisionTime.toString(), StandardCharsets.UTF_8));
        }
        return URI.create(builder.toString());
    }

    private long ageMs(String sourceTime, Instant decisionTime) {
        try {
            Instant sourceInstant = Instant.parse(sourceTime);
            Instant effectiveDecisionTime = decisionTime == null ? Instant.now() : decisionTime;
            return Math.max(0L, Duration.between(sourceInstant, effectiveDecisionTime).toMillis());
        } catch (RuntimeException exception) {
            return Long.MAX_VALUE;
        }
    }

    private double confidence(OpenMeteoForecastResponse forecastResponse) {
        double generationTimePenalty = forecastResponse.generationTimeMs() == null ? 0.0 : Math.min(0.15, forecastResponse.generationTimeMs() / 1000.0);
        return Math.max(0.1, 0.92 - generationTimePenalty);
    }

    private OpenMeteoSnapshot unavailable(String degradeReason, long startedAt) {
        return new OpenMeteoSnapshot(false, "unknown", 1.0, false, Long.MAX_VALUE, 0.0, latencyMs(startedAt), degradeReason);
    }

    private long latencyMs(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
