package com.routechain.ai;

/**
 * Shadow/advisory-only LLM integration point.
 */
public interface LLMAdvisorClient {
    String mode();
    LLMAdvisorResponse advise(LLMAdvisorRequest request);
}
