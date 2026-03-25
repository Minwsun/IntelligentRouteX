package com.routechain.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * Deterministic fallback that mimics the shape of an LLM reply without any
 * network dependency. Safe for demo and offline defense mode.
 */
public final class OfflineFallbackLLMAdvisorClient implements LLMAdvisorClient {
    @Override
    public String mode() {
        return "OFFLINE";
    }

    @Override
    public LLMAdvisorResponse advise(LLMAdvisorRequest request) {
        LLMAdvisorRequest.CandidatePlanSummary selected = request.candidatePlans().stream()
                .filter(LLMAdvisorRequest.CandidatePlanSummary::selected)
                .findFirst()
                .orElseGet(() -> request.candidatePlans().isEmpty()
                        ? null
                        : request.candidatePlans().get(0));
        if (selected == null) {
            return LLMAdvisorResponse.skipped("no candidate plans available");
        }

        List<String> risks = new ArrayList<>();
        if (selected.deliveryCorridorScore() < 0.55) {
            risks.add("corridor-fragile");
        }
        if (selected.lastDropLandingScore() < 0.45) {
            risks.add("landing-cold-zone");
        }
        if (selected.onTimeProbability() < 0.70) {
            risks.add("sla-tight");
        }
        if (selected.expectedPostCompletionEmptyKm() > 1.8) {
            risks.add("post-completion-empty-km-high");
        }

        String corridorPreference = selected.deliveryCorridorScore() >= 0.70
                ? "keep current corridor"
                : "prefer straighter drop corridor";
        String pickupWaveComment = selected.bundleSize() >= 3
                ? "pickup-wave is visible and defensible"
                : "bundle is thin; keep as fallback only";
        String landingComment = selected.lastDropLandingScore() >= 0.65
                ? "last drop lands in a strong next-order zone"
                : "last drop landing is acceptable but not premium";

        return new LLMAdvisorResponse(
                mode(),
                true,
                selected.bundleSize() >= 3 ? "preserve multi-order corridor" : "stay conservative",
                corridorPreference,
                pickupWaveComment,
                selected.deliveryCorridorScore() >= 0.60
                        ? "drop sequence is corridor-aligned"
                        : "candidate set should prioritize cleaner drop ordering",
                landingComment,
                List.copyOf(risks),
                Math.max(0.35, Math.min(0.85,
                        0.45
                                + selected.deliveryCorridorScore() * 0.20
                                + selected.lastDropLandingScore() * 0.20
                                + selected.onTimeProbability() * 0.15)),
                "offline shadow advisor based on structured route, SLA, corridor, and landing signals",
                "offline",
                "offline-shadow",
                request.requestClass() == null ? LLMRequestClass.SHADOW_FAST : request.requestClass(),
                request.estimatedInputTokens(),
                false,
                "",
                "",
                "offline",
                0L
        );
    }
}
