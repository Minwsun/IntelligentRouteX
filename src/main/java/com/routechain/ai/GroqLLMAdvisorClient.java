package com.routechain.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.routechain.infra.AdminQueryService;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Quota-aware Groq implementation for the advisory/shadow plane.
 */
public final class GroqLLMAdvisorClient implements LLMAdvisorClient {
    private static final int SHADOW_REMOTE_DAILY_CAP = 750;

    private final GroqRuntimeConfig config;
    private final GroqRoutingPolicy routingPolicy;
    private final GroqPromptCompressor promptCompressor;
    private final GroqQuotaTracker quotaTracker;
    private final GroqCircuitBreaker circuitBreaker;
    private final GroqTransport transport;
    private final LLMAdvisorClient offlineFallback;
    private final Consumer<AdminQueryService.LlmRuntimeStatus> runtimeListener;

    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong onlineRequests = new AtomicLong();
    private final AtomicLong offlineFallbacks = new AtomicLong();
    private final AtomicLong quotaRejects = new AtomicLong();
    private final AtomicLong schemaRejects = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong shadowRemoteRequestsToday = new AtomicLong();
    private final ArrayDeque<Long> recentLatencies = new ArrayDeque<>();
    private volatile String lastSelectedModel = "";
    private volatile String lastRoutingClass = "";
    private volatile String lastFallbackReason = "";
    private volatile LocalDate shadowRemoteCapDay = LocalDate.now(ZoneOffset.UTC);

    public GroqLLMAdvisorClient(GroqRuntimeConfig config,
                                Consumer<AdminQueryService.LlmRuntimeStatus> runtimeListener) {
        this(config,
                new GroqRoutingPolicy(GroqModelCatalog.freeTierDefaults()),
                new GroqPromptCompressor(),
                new GroqQuotaTracker(),
                new GroqCircuitBreaker(),
                new HttpGroqTransport(),
                new OfflineFallbackLLMAdvisorClient(),
                runtimeListener);
    }

    GroqLLMAdvisorClient(GroqRuntimeConfig config,
                         GroqRoutingPolicy routingPolicy,
                         GroqPromptCompressor promptCompressor,
                         GroqQuotaTracker quotaTracker,
                         GroqCircuitBreaker circuitBreaker,
                         GroqTransport transport,
                         LLMAdvisorClient offlineFallback,
                         Consumer<AdminQueryService.LlmRuntimeStatus> runtimeListener) {
        this.config = config;
        this.routingPolicy = routingPolicy;
        this.promptCompressor = promptCompressor;
        this.quotaTracker = quotaTracker;
        this.circuitBreaker = circuitBreaker;
        this.transport = transport;
        this.offlineFallback = offlineFallback;
        this.runtimeListener = runtimeListener == null ? ignored -> { } : runtimeListener;
        publishRuntimeSnapshot();
    }

    @Override
    public String mode() {
        return config.mode();
    }

    @Override
    public LLMAdvisorResponse advise(LLMAdvisorRequest request) {
        totalRequests.incrementAndGet();
        GroqRoutingPolicy.RoutingDecision decision = routingPolicy.route(request, config);
        lastRoutingClass = decision.requestClass().name();

        if (!config.canUseGroq()) {
            return fallback(request, decision.requestClass(), request.estimatedInputTokens(),
                    "groq-disabled", "", "offline", 0L);
        }
        if (!shouldUseRemote(request, decision.requestClass())) {
            return fallback(request, decision.requestClass(), request.estimatedInputTokens(),
                    "local-budget-gate", "", "local-budget-gate", 0L);
        }
        Instant now = Instant.now();
        if (circuitBreaker.isOpen(now)) {
            return fallback(request, decision.requestClass(), request.estimatedInputTokens(),
                    "circuit-open", "", "circuit-open", 0L);
        }

        GroqPromptCompressor.CompressedPrompt prompt = promptCompressor.compress(
                request, decision, config.maxCandidates());
        int estimatedTokens = request.estimatedInputTokens() > 0
                ? request.estimatedInputTokens()
                : prompt.estimatedInputTokens();

        List<String> attemptedModels = new ArrayList<>();
        List<String> fallbackReasons = new ArrayList<>();
        for (GroqModelCatalog.ModelSpec model : decision.cascade()) {
            attemptedModels.add(model.modelId());
            GroqQuotaTracker.Reservation reservation = quotaTracker.tryReserve(
                    model,
                    estimatedTokens + decision.maxOutputTokens(),
                    now);
            if (!reservation.accepted()) {
                quotaRejects.incrementAndGet();
                fallbackReasons.add(model.modelId() + ":" + reservation.rejectionReason());
                continue;
            }
            try {
                GroqTransport.GroqTransportResult result = transport.chatCompletion(
                        config.chatCompletionsUrl(),
                        config.apiKey(),
                        model.modelId(),
                        prompt.systemPrompt(),
                        prompt.userPrompt(),
                        decision.maxOutputTokens(),
                        decision.timeoutMs());
                if (result.statusCode() == 429) {
                    quotaTracker.markQuotaExceeded(model.modelId(), now);
                    quotaRejects.incrementAndGet();
                    fallbackReasons.add(model.modelId() + ":http-429");
                    continue;
                }
                if (result.statusCode() >= 500) {
                    circuitBreaker.recordFailure(now);
                    fallbackReasons.add(model.modelId() + ":server-" + result.statusCode());
                    continue;
                }
                if (result.statusCode() >= 400) {
                    fallbackReasons.add(model.modelId() + ":client-" + result.statusCode());
                    continue;
                }

                ParsedGroqPayload parsed = parsePayload(
                        result.body(),
                        model.modelId(),
                        decision.requestClass(),
                        estimatedTokens,
                        result.latencyMs(),
                        attemptedModels);
                quotaTracker.settle(reservation, parsed.totalTokens());
                circuitBreaker.recordSuccess();
                onlineRequests.incrementAndGet();
                lastSelectedModel = model.modelId();
                lastFallbackReason = "";
                recordLatency(result.latencyMs());
                publishRuntimeSnapshot();
                return parsed.response();
            } catch (HttpTimeoutException timeout) {
                timeoutCount.incrementAndGet();
                circuitBreaker.recordFailure(now);
                fallbackReasons.add(model.modelId() + ":timeout");
                break;
            } catch (IOException | InterruptedException transportFailure) {
                if (transportFailure instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                circuitBreaker.recordFailure(now);
                fallbackReasons.add(model.modelId() + ":transport");
            } catch (RuntimeException schemaFailure) {
                schemaRejects.incrementAndGet();
                circuitBreaker.recordFailure(now);
                fallbackReasons.add(model.modelId() + ":schema");
                break;
            }
        }

        return fallback(request,
                decision.requestClass(),
                estimatedTokens,
                fallbackReasons.isEmpty() ? "cascade-exhausted" : String.join(" | ", fallbackReasons),
                String.join(" -> ", attemptedModels),
                "cascade-exhausted",
                0L);
    }

    private boolean shouldUseRemote(LLMAdvisorRequest request, LLMRequestClass requestClass) {
        if (requestClass == LLMRequestClass.REPLAY_BATCH || requestClass == LLMRequestClass.OPERATOR_FREE_TEXT) {
            return true;
        }
        List<LLMAdvisorRequest.CandidatePlanSummary> plans = request.candidatePlans();
        if (plans == null || plans.isEmpty()) {
            return false;
        }
        LLMAdvisorRequest.CandidatePlanSummary selected = plans.stream()
                .filter(LLMAdvisorRequest.CandidatePlanSummary::selected)
                .findFirst()
                .orElse(plans.get(0));
        if (selected.bundleSize() >= 2) {
            return reserveShadowRemoteBudget();
        }
        if (selected.onTimeProbability() < 0.45) {
            return reserveShadowRemoteBudget();
        }
        if ("SHOWCASE_PICKUP_WAVE_8".equalsIgnoreCase(request.executionProfile())) {
            return reserveShadowRemoteBudget();
        }
        List<LLMAdvisorRequest.CandidatePlanSummary> ranked = plans.stream()
                .sorted(Comparator.comparingDouble(LLMAdvisorRequest.CandidatePlanSummary::totalScore).reversed())
                .limit(2)
                .toList();
        if (ranked.size() >= 2) {
            double gap = ranked.get(0).totalScore() - ranked.get(1).totalScore();
            return gap <= 0.08
                    && Math.floorMod(request.traceId().hashCode(), 5) == 0
                    && reserveShadowRemoteBudget();
        }
        return false;
    }

    private ParsedGroqPayload parsePayload(String body,
                                           String modelId,
                                           LLMRequestClass requestClass,
                                           int estimatedInputTokens,
                                           long latencyMs,
                                           List<String> attemptedModels) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonObject usage = root.has("usage") && root.get("usage").isJsonObject()
                ? root.getAsJsonObject("usage")
                : new JsonObject();
        int totalTokens = usage.has("total_tokens")
                ? usage.get("total_tokens").getAsInt()
                : Math.max(estimatedInputTokens, estimatedInputTokens + 64);
        JsonArray choices = root.getAsJsonArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new IllegalStateException("Groq response contained no choices");
        }
        JsonObject firstChoice = choices.get(0).getAsJsonObject();
        JsonObject message = firstChoice.getAsJsonObject("message");
        if (message == null || !message.has("content")) {
            throw new IllegalStateException("Groq response missing message content");
        }
        String rawContent = message.get("content").getAsString().trim();
        String normalizedJson = unwrapJson(rawContent);
        JsonObject payload = JsonParser.parseString(normalizedJson).getAsJsonObject();

        String routeIntent = requiredString(payload, "routeIntent");
        String corridorPreference = requiredString(payload, "corridorPreference");
        String pickupWaveComment = requiredString(payload, "pickupWaveComment");
        String dropSequenceCritique = requiredString(payload, "dropSequenceCritique");
        String softLandingComment = requiredString(payload, "softLandingComment");
        String reasoning = requiredString(payload, "reasoning");
        double confidence = clampConfidence(payload.has("confidence")
                ? payload.get("confidence").getAsDouble()
                : 0.55);
        List<String> riskFlags = new ArrayList<>();
        JsonArray riskArray = payload.has("riskFlags") && payload.get("riskFlags").isJsonArray()
                ? payload.getAsJsonArray("riskFlags")
                : new JsonArray();
        for (JsonElement element : riskArray) {
            riskFlags.add(element.getAsString());
        }

        return new ParsedGroqPayload(
                totalTokens,
                new LLMAdvisorResponse(
                        config.mode(),
                        true,
                        routeIntent,
                        corridorPreference,
                        pickupWaveComment,
                        dropSequenceCritique,
                        softLandingComment,
                        List.copyOf(riskFlags),
                        confidence,
                        reasoning,
                        "groq",
                        modelId,
                        requestClass,
                        estimatedInputTokens,
                        attemptedModels.size() > 1,
                        "",
                        String.join(" -> ", attemptedModels),
                        "online:" + modelId,
                        latencyMs
                )
        );
    }

    private LLMAdvisorResponse fallback(LLMAdvisorRequest request,
                                        LLMRequestClass requestClass,
                                        int estimatedInputTokens,
                                        String fallbackReason,
                                        String fallbackChain,
                                        String quotaDecision,
                                        long latencyMs) {
        offlineFallbacks.incrementAndGet();
        lastSelectedModel = "offline-shadow";
        lastFallbackReason = fallbackReason == null ? "" : fallbackReason;
        publishRuntimeSnapshot();

        LLMAdvisorResponse offline = offlineFallback.advise(request);
        return new LLMAdvisorResponse(
                "OFFLINE",
                offline.triggered(),
                offline.routeIntent(),
                offline.corridorPreference(),
                offline.pickupWaveComment(),
                offline.dropSequenceCritique(),
                offline.softLandingComment(),
                offline.riskFlags(),
                offline.confidence(),
                offline.reasoning(),
                "offline",
                "offline-shadow",
                requestClass,
                estimatedInputTokens,
                true,
                fallbackReason == null ? "" : fallbackReason,
                fallbackChain == null ? "" : fallbackChain,
                quotaDecision == null ? "offline" : quotaDecision,
                latencyMs
        );
    }

    private void recordLatency(long latencyMs) {
        synchronized (recentLatencies) {
            recentLatencies.addLast(Math.max(0L, latencyMs));
            while (recentLatencies.size() > 256) {
                recentLatencies.removeFirst();
            }
        }
    }

    private synchronized boolean reserveShadowRemoteBudget() {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!today.equals(shadowRemoteCapDay)) {
            shadowRemoteCapDay = today;
            shadowRemoteRequestsToday.set(0);
        }
        if (shadowRemoteRequestsToday.get() >= SHADOW_REMOTE_DAILY_CAP) {
            return false;
        }
        shadowRemoteRequestsToday.incrementAndGet();
        return true;
    }

    private void publishRuntimeSnapshot() {
        runtimeListener.accept(new AdminQueryService.LlmRuntimeStatus(
                "groq",
                mode(),
                lastSelectedModel,
                lastRoutingClass,
                lastFallbackReason,
                totalRequests.get(),
                onlineRequests.get(),
                offlineFallbacks.get(),
                quotaRejects.get(),
                schemaRejects.get(),
                timeoutCount.get(),
                totalRequests.get() == 0 ? 0.0 : offlineFallbacks.get() * 100.0 / totalRequests.get(),
                percentileLatency(0.50),
                percentileLatency(0.95),
                circuitBreaker.isOpen(Instant.now())
        ));
    }

    private double percentileLatency(double percentile) {
        List<Long> sample;
        synchronized (recentLatencies) {
            if (recentLatencies.isEmpty()) {
                return 0.0;
            }
            sample = recentLatencies.stream()
                    .sorted(Comparator.naturalOrder())
                    .toList();
        }
        int index = (int) Math.floor((sample.size() - 1) * percentile);
        return sample.get(Math.max(0, Math.min(index, sample.size() - 1)));
    }

    private static String unwrapJson(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                return trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return trimmed;
    }

    private static String requiredString(JsonObject payload, String key) {
        if (!payload.has(key) || payload.get(key).isJsonNull()) {
            throw new IllegalStateException("Groq response missing required key: " + key);
        }
        String value = payload.get(key).getAsString();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Groq response key was blank: " + key);
        }
        return value;
    }

    private static double clampConfidence(double confidence) {
        if (Double.isNaN(confidence) || Double.isInfinite(confidence)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    private record ParsedGroqPayload(
            int totalTokens,
            LLMAdvisorResponse response
    ) {}
}
