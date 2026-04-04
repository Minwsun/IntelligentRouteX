package com.routechain.simulation;

/**
 * Static evidence that a route-AI component is wired into the live code path.
 */
public record AiComponentEvidence(
        String componentName,
        boolean required,
        boolean detected,
        String evidenceAnchor,
        String explanation
) {
    public AiComponentEvidence {
        componentName = componentName == null ? "unknown" : componentName;
        evidenceAnchor = evidenceAnchor == null ? "" : evidenceAnchor;
        explanation = explanation == null ? "" : explanation;
    }
}
