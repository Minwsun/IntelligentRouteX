package com.routechain.ai;

/**
 * Runtime configuration for the Groq shadow/advisory client.
 */
public record GroqRuntimeConfig(
        boolean enabled,
        String apiKey,
        String mode,
        String chatCompletionsUrl,
        int timeoutMs,
        int maxCandidates,
        String routingPolicy
) {
    private static final String DEFAULT_URL = "https://api.groq.com/openai/v1/chat/completions";

    public static GroqRuntimeConfig fromEnvironment() {
        boolean enabled = getBoolean("GROQ_ENABLED", "routechain.groq.enabled", false);
        String apiKey = getString("GROQ_API_KEY", "routechain.groq.api-key", "");
        String mode = normalizeMode(getString("GROQ_MODE", "routechain.groq.mode", "SHADOW"));
        String url = getString("GROQ_CHAT_COMPLETIONS_URL",
                "routechain.groq.chat-completions-url", DEFAULT_URL);
        int timeoutMs = getInt("GROQ_TIMEOUT_MS", "routechain.groq.timeout-ms", 1200);
        int maxCandidates = getInt("GROQ_MAX_CANDIDATES", "routechain.groq.max-candidates", 4);
        String routingPolicy = getString("GROQ_ROUTING_POLICY",
                "routechain.groq.routing-policy", "FREE_TIER_BALANCED");
        return new GroqRuntimeConfig(
                enabled,
                apiKey == null ? "" : apiKey.trim(),
                mode,
                url == null || url.isBlank() ? DEFAULT_URL : url.trim(),
                Math.max(150, timeoutMs),
                Math.max(1, maxCandidates),
                routingPolicy == null || routingPolicy.isBlank()
                        ? "FREE_TIER_BALANCED"
                        : routingPolicy.trim());
    }

    public boolean canUseGroq() {
        return enabled && !"OFF".equalsIgnoreCase(mode) && hasValidApiKey();
    }

    public boolean hasValidApiKey() {
        return apiKey != null && apiKey.startsWith("gsk_") && apiKey.length() >= 24;
    }

    public String sanitizedKeyHint() {
        if (!hasValidApiKey()) {
            return "missing";
        }
        int tail = Math.min(4, apiKey.length());
        return "****" + apiKey.substring(apiKey.length() - tail);
    }

    private static boolean getBoolean(String envKey, String propKey, boolean defaultValue) {
        String raw = getString(envKey, propKey, Boolean.toString(defaultValue));
        return "true".equalsIgnoreCase(raw) || "1".equals(raw) || "yes".equalsIgnoreCase(raw);
    }

    private static int getInt(String envKey, String propKey, int defaultValue) {
        String raw = getString(envKey, propKey, Integer.toString(defaultValue));
        try {
            return Integer.parseInt(raw.trim());
        } catch (RuntimeException ignored) {
            return defaultValue;
        }
    }

    private static String getString(String envKey, String propKey, String defaultValue) {
        String prop = System.getProperty(propKey);
        if (prop != null && !prop.isBlank()) {
            return prop;
        }
        String env = System.getenv(envKey);
        if (env != null && !env.isBlank()) {
            return env;
        }
        return defaultValue;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "SHADOW";
        }
        String upper = mode.trim().toUpperCase();
        return switch (upper) {
            case "SHADOW", "ADVISORY", "OFF" -> upper;
            default -> "SHADOW";
        };
    }
}
