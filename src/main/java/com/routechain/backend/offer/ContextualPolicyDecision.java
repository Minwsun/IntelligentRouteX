package com.routechain.backend.offer;

/**
 * Lightweight policy-tuner output for offer fanout and gating.
 */
public record ContextualPolicyDecision(
        String regime,
        String serviceTier,
        int offerFanout,
        String gateProfile,
        String reserveProfile,
        String waitProfile
) {
    public ContextualPolicyDecision {
        regime = regime == null || regime.isBlank() ? "NORMAL" : regime;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        offerFanout = Math.max(1, Math.min(3, offerFanout));
        gateProfile = gateProfile == null || gateProfile.isBlank() ? "execution-first" : gateProfile;
        reserveProfile = reserveProfile == null || reserveProfile.isBlank() ? "balanced" : reserveProfile;
        waitProfile = waitProfile == null || waitProfile.isBlank() ? "default" : waitProfile;
    }
}
