package com.routechain.ai;

/**
 * Declarative description of a tool available to the dispatch brain.
 */
public record AgentToolDescriptor(
        String name,
        String responsibility,
        boolean hotPath,
        boolean llmCapable
) {}
