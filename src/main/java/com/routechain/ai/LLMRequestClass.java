package com.routechain.ai;

/**
 * Logical request classes for quota-aware LLM routing.
 */
public enum LLMRequestClass {
    SHADOW_FAST,
    ADVISORY_HIGH_QUALITY,
    REPLAY_BATCH,
    OPERATOR_FREE_TEXT
}
