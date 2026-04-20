package com.routechain.v2.decision;

public record ResolvedDecisionBrain(
        DecisionBrainType requestedType,
        DecisionBrainType appliedType,
        DecisionBrain brain,
        boolean fallbackUsed,
        String fallbackReason) {
}
