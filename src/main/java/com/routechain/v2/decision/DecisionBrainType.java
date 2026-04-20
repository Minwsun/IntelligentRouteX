package com.routechain.v2.decision;

public enum DecisionBrainType {
    LEGACY,
    LLM,
    STUDENT;

    public static DecisionBrainType fromMode(String rawMode) {
        if (rawMode == null || rawMode.isBlank()) {
            return LLM;
        }
        return switch (rawMode.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "legacy" -> LEGACY;
            case "student" -> STUDENT;
            case "llm", "hybrid" -> LLM;
            default -> LLM;
        };
    }
}
