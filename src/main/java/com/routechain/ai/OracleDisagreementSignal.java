package com.routechain.ai;

/**
 * Oracle disagreement summary kept separate from the raw critic score.
 */
public record OracleDisagreementSignal(
        double oracleScore,
        double challengerScore,
        double disagreementPenalty,
        String backend,
        boolean hardDisagreement
) {
    public static OracleDisagreementSignal from(ShadowOracleScore score) {
        if (score == null) {
            return new OracleDisagreementSignal(0.0, 0.0, 0.0, "none", false);
        }
        return new OracleDisagreementSignal(
                score.oracleScore(),
                score.challengerScore(),
                score.disagreementPenalty(),
                score.backend(),
                score.disagreementPenalty() >= 0.10);
    }
}
