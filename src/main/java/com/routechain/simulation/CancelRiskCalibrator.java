package com.routechain.simulation;

import com.routechain.core.DecisionLogRecord;
import com.routechain.core.PlanFeatureVector;
import com.routechain.core.ResolvedDecisionSample;

final class CancelRiskCalibrator {
    private static final int BIN_COUNT = 10;
    private final long[] samples = new long[BIN_COUNT];
    private final long[] cancelled = new long[BIN_COUNT];

    public void reset() {
        java.util.Arrays.fill(samples, 0L);
        java.util.Arrays.fill(cancelled, 0L);
    }

    public double calibrate(double predictedCancelRisk) {
        int bin = bin(predictedCancelRisk);
        if (samples[bin] == 0L) {
            return PlanFeatureVector.clamp01(predictedCancelRisk);
        }
        return PlanFeatureVector.clamp01(cancelled[bin] / (double) samples[bin]);
    }

    public void record(ResolvedDecisionSample sample) {
        if (sample == null || !sample.eligibleForCancelCalibration()) {
            return;
        }
        int bin = bin(sample.decisionLog().predictedCancelRisk());
        samples[bin]++;
        if (sample.actualCancelled()) {
            cancelled[bin]++;
        }
    }

    public double calibrationGap() {
        double weightedGap = 0.0;
        long total = 0L;
        for (int i = 0; i < BIN_COUNT; i++) {
            if (samples[i] == 0L) {
                continue;
            }
            double midpoint = (i + 0.5) / BIN_COUNT;
            double observed = cancelled[i] / (double) samples[i];
            weightedGap += Math.abs(midpoint - observed) * samples[i];
            total += samples[i];
        }
        return total == 0L ? 0.0 : weightedGap / total;
    }

    public long sampleCount() {
        long total = 0L;
        for (long sample : samples) {
            total += sample;
        }
        return total;
    }

    private int bin(double value) {
        return Math.min(BIN_COUNT - 1, Math.max(0, (int) Math.floor(PlanFeatureVector.clamp01(value) * BIN_COUNT)));
    }
}
