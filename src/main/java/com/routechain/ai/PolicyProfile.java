package com.routechain.ai;

/**
 * Policy Profile — defines weight configuration for different city conditions.
 * Each profile emphasizes different objectives based on the operational context.
 *
 * 5 standard profiles:
 * - NORMAL: balanced optimization
 * - RAIN: prioritize on-time, increase late penalty
 * - SHORTAGE: maximize bundling, continuation value
 * - SPAM: aggressive bundling, reduce per-order processing
 * - HEAVY_TRAFFIC: prioritize on-time, reduce deadhead tolerance
 */
public record PolicyProfile(
        String name,
        double wOnTime,
        double wProfit,
        double wBundle,
        double wContinuation,
        double wFee,
        double wNextOrder,
        double pDeadhead,
        double pLate,
        double pCancel,
        double pCongestion
) {
    // ── Standard profiles ───────────────────────────────────────────────

    public static final PolicyProfile NORMAL = new PolicyProfile(
            "NORMAL",     0.18, 0.15, 0.12, 0.12, 0.08, 0.10, 0.08, 0.05, 0.03, 0.05);

    public static final PolicyProfile RAIN = new PolicyProfile(
            "RAIN",       0.25, 0.10, 0.08, 0.15, 0.07, 0.08, 0.06, 0.10, 0.04, 0.07);

    public static final PolicyProfile SHORTAGE = new PolicyProfile(
            "SHORTAGE",   0.10, 0.10, 0.20, 0.20, 0.05, 0.15, 0.05, 0.03, 0.02, 0.05);

    public static final PolicyProfile SPAM = new PolicyProfile(
            "SPAM",       0.15, 0.08, 0.25, 0.08, 0.06, 0.08, 0.10, 0.08, 0.05, 0.07);

    public static final PolicyProfile HEAVY_TRAFFIC = new PolicyProfile(
            "HEAVY_TRAFFIC", 0.22, 0.12, 0.10, 0.15, 0.06, 0.08, 0.08, 0.08, 0.04, 0.07);

    public static final PolicyProfile[] ALL = {
            NORMAL, RAIN, SHORTAGE, SPAM, HEAVY_TRAFFIC
    };

    /**
     * Get profile by name.
     */
    public static PolicyProfile byName(String name) {
        for (PolicyProfile p : ALL) {
            if (p.name.equals(name)) return p;
        }
        return NORMAL;
    }

    /**
     * Apply this policy's weights to raw sub-scores to compute final utility.
     */
    public double applyWeights(
            double onTimeScore, double profitScore, double bundleEffScore,
            double continuationScore, double feeScore, double nextOrderScore,
            double deadheadPenalty, double lateRiskPenalty, double cancelPenalty,
            double congestionPenalty) {
        return wOnTime * onTimeScore
             + wProfit * profitScore
             + wBundle * bundleEffScore
             + wContinuation * continuationScore
             + wFee * feeScore
             + wNextOrder * nextOrderScore
             - pDeadhead * deadheadPenalty
             - pLate * lateRiskPenalty
             - pCancel * cancelPenalty
             - pCongestion * congestionPenalty;
    }
}
