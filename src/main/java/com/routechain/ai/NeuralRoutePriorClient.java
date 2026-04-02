package com.routechain.ai;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.routechain.domain.GeoPoint;
import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.simulation.DispatchPlan;

import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reads nearline neural route priors from local sidecar and keeps a short-lived cache.
 */
public final class NeuralRoutePriorClient {
    private static final Gson GSON = new Gson();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final URI endpoint;
    private final Duration cacheTtl;
    private final Duration requestTimeout;
    private final boolean enabled;
    private final Clock clock;
    private final PriorFetcher fetcher;
    private final Map<String, CachedPrior> cache = new ConcurrentHashMap<>();

    @FunctionalInterface
    interface PriorFetcher {
        FetchResult fetch(PriorRequest request);
    }

    record PriorRequest(
            String runId,
            String scenario,
            String zoneId,
            String driverId,
            double pickupLat,
            double pickupLng,
            WeatherProfile weather,
            double trafficIntensity,
            int trafficHorizonMinutes,
            int weatherHorizonMinutes,
            Map<String, Object> driverClusterSnapshot
    ) {}

    private record CachedPrior(
            NeuralRoutePrior prior,
            Instant expiresAt
    ) {}

    record FetchResult(
            NeuralRoutePrior prior,
            String failureReason
    ) {
        static FetchResult success(NeuralRoutePrior prior) {
            return new FetchResult(prior, "none");
        }

        static FetchResult failure(String failureReason) {
            return new FetchResult(null,
                    failureReason == null || failureReason.isBlank()
                            ? "neural-prior-unavailable"
                            : failureReason);
        }
    }

    public NeuralRoutePriorClient() {
        this(
                URI.create(System.getProperty("routechain.neuralPrior.endpoint",
                        "http://127.0.0.1:8094/prior")),
                Duration.ofSeconds(Math.max(5, Integer.getInteger("routechain.neuralPrior.ttlSec", 25))),
                Duration.ofMillis(Math.max(10, Integer.getInteger("routechain.neuralPrior.timeoutMs", 25))),
                Boolean.parseBoolean(System.getProperty("routechain.neuralPrior.enabled", "true")),
                Clock.systemUTC(),
                null
        );
    }

    NeuralRoutePriorClient(URI endpoint,
                           Duration cacheTtl,
                           Duration requestTimeout,
                           boolean enabled,
                           Clock clock,
                           PriorFetcher fetcher) {
        this.endpoint = endpoint;
        this.cacheTtl = cacheTtl;
        this.requestTimeout = requestTimeout;
        this.enabled = enabled;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.fetcher = fetcher == null ? httpFetcher() : fetcher;
    }

    public NeuralRoutePrior resolve(String runId,
                                    String scenario,
                                    DriverDecisionContext ctx,
                                    DispatchPlan plan,
                                    WeatherProfile weather,
                                    double trafficIntensity) {
        if (plan == null || plan.getDriver() == null) {
            return NeuralRoutePrior.fallback("unknown-zone", "invalid-plan");
        }
        String zoneId = resolveZoneId(plan);
        String cacheKey = zoneId + "|" + weather.name() + "|" + normalizeScenario(scenario);
        Instant now = clock.instant();
        CachedPrior cached = cache.get(cacheKey);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            NeuralRoutePrior prior = cached.prior();
            return new NeuralRoutePrior(
                    prior.zoneId(),
                    prior.priorScore(),
                    prior.routeTemplateIds(),
                    prior.confidence(),
                    Math.max(0L, Duration.between(prior.asOf(), now).toMillis()),
                    prior.modelVersion(),
                    prior.used(),
                    prior.fallbackUsed(),
                    prior.fallbackReason(),
                    prior.modelFamily(),
                    prior.backend(),
                    prior.latencyMs(),
                    prior.asOf());
        }
        if (!enabled) {
            return NeuralRoutePrior.fallback(zoneId, "neural-prior-disabled");
        }
        PriorRequest request = buildRequest(runId, scenario, ctx, plan, weather, trafficIntensity);
        FetchResult remotePrior;
        try {
            remotePrior = fetcher.fetch(request);
        } catch (Exception ex) {
            remotePrior = FetchResult.failure("sidecar-exception");
        }
        if (remotePrior != null && remotePrior.prior() != null) {
            NeuralRoutePrior prior = remotePrior.prior();
            cache.put(cacheKey, new CachedPrior(prior, now.plus(cacheTtl)));
            return prior;
        }
        String reason = remotePrior == null || remotePrior.failureReason() == null
                ? "neural-prior-unavailable"
                : remotePrior.failureReason();
        return NeuralRoutePrior.fallback(zoneId, cached == null
                ? reason
                : "neural-prior-stale-" + reason);
    }

    public void clear() {
        cache.clear();
    }

    private PriorRequest buildRequest(String runId,
                                      String scenario,
                                      DriverDecisionContext ctx,
                                      DispatchPlan plan,
                                      WeatherProfile weather,
                                      double trafficIntensity) {
        GeoPoint pickupFrontier = plan.getSequence().isEmpty()
                ? plan.getDriver().getCurrentLocation()
                : plan.getSequence().get(0).location();
        String safeRunId = runId == null || runId.isBlank() ? "run-unset" : runId;
        String safeScenario = normalizeScenario(scenario);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (ctx != null) {
            snapshot.put("localDriverDensity", ctx.localDriverDensity());
            snapshot.put("localReachableBacklog", ctx.localReachableBacklog());
            snapshot.put("localDemandForecast5m", ctx.localDemandForecast5m());
            snapshot.put("localDemandForecast10m", ctx.localDemandForecast10m());
            snapshot.put("localDemandForecast15m", ctx.localDemandForecast15m());
            snapshot.put("localShortagePressure", ctx.localShortagePressure());
            snapshot.put("thirdOrderFeasibilityScore", ctx.thirdOrderFeasibilityScore());
            snapshot.put("waveAssemblyPressure", ctx.waveAssemblyPressure());
            snapshot.put("stressRegime", ctx.stressRegime().name());
            snapshot.put("harshWeatherStress", ctx.harshWeatherStress());
        }
        return new PriorRequest(
                safeRunId,
                safeScenario,
                resolveZoneId(plan),
                plan.getDriver().getId(),
                pickupFrontier.lat(),
                pickupFrontier.lng(),
                weather,
                trafficIntensity,
                5,
                10,
                snapshot
        );
    }

    private PriorFetcher httpFetcher() {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(requestTimeout)
                .build();
        return request -> fetchFromHttp(client, request);
    }

    private FetchResult fetchFromHttp(HttpClient client, PriorRequest request) {
        long startedNanos = System.nanoTime();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", request.runId());
        payload.put("scenario", request.scenario());
        payload.put("zoneId", request.zoneId());
        payload.put("driverId", request.driverId());
        payload.put("pickupFrontier", Map.of("lat", request.pickupLat(), "lng", request.pickupLng()));
        payload.put("weather", request.weather().name());
        payload.put("trafficIntensity", request.trafficIntensity());
        payload.put("trafficHorizonMinutes", request.trafficHorizonMinutes());
        payload.put("weatherHorizonMinutes", request.weatherHorizonMinutes());
        payload.put("driverClusterSnapshot", request.driverClusterSnapshot());

        HttpRequest httpRequest = HttpRequest.newBuilder(endpoint)
                .timeout(requestTimeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();
        HttpResponse<String> response;
        try {
            response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException timeoutException) {
            return FetchResult.failure("timeout");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return FetchResult.failure("interrupted");
        } catch (IOException ioException) {
            return FetchResult.failure("unreachable");
        } catch (Exception ignored) {
            return FetchResult.failure("request-failed");
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            return FetchResult.failure("http-" + response.statusCode());
        }
        Map<String, Object> body;
        try {
            body = GSON.fromJson(response.body(), MAP_TYPE);
        } catch (Exception ignored) {
            return FetchResult.failure("schema-invalid-json");
        }
        if (body == null) {
            return FetchResult.failure("schema-empty-body");
        }
        Double rawPriorScore = asDoubleOrNull(body.get("priorScore"));
        if (rawPriorScore == null) {
            return FetchResult.failure("schema-missing-priorScore");
        }
        Instant now = clock.instant();
        long generatedAtEpochMs = asLong(body.get("generatedAtEpochMs"), now.toEpochMilli());
        Instant asOf = Instant.ofEpochMilli(generatedAtEpochMs);
        long freshnessMs = Math.max(0L, Duration.between(asOf, now).toMillis());
        long latencyMs = Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
        NeuralRoutePrior prior = new NeuralRoutePrior(
                request.zoneId(),
                clamp01(rawPriorScore),
                asStringList(body.get("routeTemplateIds")),
                clamp01(asDouble(body.get("confidence"), 0.0)),
                freshnessMs,
                asString(body.get("modelVersion"), "routefinder-prior-v1"),
                true,
                false,
                "none",
                asString(body.get("modelFamily"), "neural-route-prior"),
                asString(body.get("backend"), "python-sidecar"),
                latencyMs,
                asOf
        );
        return FetchResult.success(prior);
    }

    private String resolveZoneId(DispatchPlan plan) {
        if (plan.getDriver() != null && plan.getDriver().getRegionId() != null && !plan.getDriver().getRegionId().isBlank()) {
            return plan.getDriver().getRegionId();
        }
        return "unknown-zone";
    }

    private String normalizeScenario(String scenario) {
        return (scenario == null || scenario.isBlank()) ? "unspecified" : scenario;
    }

    private double asDouble(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private Double asDoubleOrNull(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text) {
            try {
                return Double.parseDouble(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long asLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private String asString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = value.toString().trim();
        return text.isEmpty() ? fallback : text;
    }

    private List<String> asStringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .map(item -> item == null ? "" : item.toString().trim())
                .filter(item -> !item.isBlank())
                .toList();
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
