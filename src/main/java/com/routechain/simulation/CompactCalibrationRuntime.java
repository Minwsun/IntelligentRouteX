package com.routechain.simulation;

import com.routechain.core.DecisionLogRecord;
import com.routechain.core.ResolvedDecisionSample;

public class CompactCalibrationRuntime {
    private final EtaResidualCalibrator etaResidualCalibrator = new EtaResidualCalibrator();
    private final CancelRiskCalibrator cancelRiskCalibrator = new CancelRiskCalibrator();
    private final PostDropCalibrator postDropCalibrator = new PostDropCalibrator();

    public void reset() {
        etaResidualCalibrator.reset();
        cancelRiskCalibrator.reset();
        postDropCalibrator.reset();
    }

    public DecisionLogRecord calibrateDecisionLog(DecisionLogRecord decisionLog) {
        if (decisionLog == null) {
            return null;
        }
        double calibratedEta = etaResidualCalibrator.calibrate(decisionLog);
        double calibratedCancelRisk = cancelRiskCalibrator.calibrate(decisionLog.predictedCancelRisk());
        double calibratedPostDropProbability = postDropCalibrator.calibrateHitProbability(decisionLog);
        double calibratedPostCompletionEmptyKm = postDropCalibrator.calibrateEmptyKm(decisionLog);
        double calibratedNextIdleMinutes = postDropCalibrator.calibrateNextIdleMinutes(decisionLog);
        return new DecisionLogRecord(
                decisionLog.decisionId(),
                decisionLog.driverId(),
                decisionLog.bundleId(),
                decisionLog.planType(),
                decisionLog.orderIds(),
                decisionLog.regimeKey(),
                decisionLog.featureVector(),
                decisionLog.scoreBreakdown(),
                decisionLog.snapshotBefore(),
                decisionLog.decisionTime(),
                decisionLog.predictedUtilityRaw(),
                decisionLog.predictedRewardNormalized(),
                calibratedEta,
                decisionLog.predictedDeadheadKm(),
                decisionLog.predictedRevenue(),
                decisionLog.predictedLandingScore(),
                calibratedPostDropProbability,
                calibratedPostCompletionEmptyKm,
                calibratedNextIdleMinutes,
                calibratedCancelRisk,
                decisionLog.predictedOnTimeProbability(),
                decisionLog.predictedTripDistanceKm());
    }

    public void recordResolvedSample(ResolvedDecisionSample sample) {
        etaResidualCalibrator.record(sample);
        cancelRiskCalibrator.record(sample);
        postDropCalibrator.record(sample);
    }

    public CalibrationSnapshot snapshot() {
        return new CalibrationSnapshot(
                etaResidualCalibrator.maeMinutes(),
                cancelRiskCalibrator.calibrationGap(),
                postDropCalibrator.postDropHitCalibrationGap(),
                postDropCalibrator.nextIdleMaeMinutes(),
                postDropCalibrator.emptyKmMae(),
                etaResidualCalibrator.sampleCount(),
                cancelRiskCalibrator.sampleCount(),
                postDropCalibrator.sampleCount());
    }
}
