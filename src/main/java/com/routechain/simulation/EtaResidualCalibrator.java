package com.routechain.simulation;

import com.routechain.core.DecisionLogRecord;
import com.routechain.core.PlanFeatureVector;
import com.routechain.core.ResolvedDecisionSample;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class EtaResidualCalibrator {
    private final Map<String, ResidualBucket> buckets = new ConcurrentHashMap<>();

    public void reset() {
        buckets.clear();
    }

    public double calibrate(DecisionLogRecord decisionLog) {
        if (decisionLog == null) {
            return 0.0;
        }
        ResidualBucket bucket = buckets.get(bucketKey(decisionLog));
        return bucket == null ? decisionLog.predictedEtaMinutes() : Math.max(0.0, decisionLog.predictedEtaMinutes() + bucket.meanResidual());
    }

    public void record(ResolvedDecisionSample sample) {
        if (sample == null || !sample.eligibleForEtaCalibration() || sample.actualEtaMinutes() <= 0.0) {
            return;
        }
        buckets.computeIfAbsent(bucketKey(sample.decisionLog()), ignored -> new ResidualBucket())
                .record(sample.actualEtaMinutes() - sample.decisionLog().predictedEtaMinutes());
    }

    public double maeMinutes() {
        return buckets.values().stream().mapToDouble(ResidualBucket::meanAbsoluteResidual).average().orElse(0.0);
    }

    public long sampleCount() {
        return buckets.values().stream().mapToLong(ResidualBucket::sampleCount).sum();
    }

    private String bucketKey(DecisionLogRecord decisionLog) {
        int hourBucket = decisionLog.decisionTime() == null ? 0 : decisionLog.decisionTime().atZone(java.time.ZoneOffset.UTC).getHour() / 6;
        int distanceBucket = (int) Math.min(4, Math.floor(decisionLog.predictedTripDistanceKm() / 3.0));
        return decisionLog.regimeKey().name() + "|" + hourBucket + "|" + distanceBucket;
    }

    private static final class ResidualBucket {
        private long sampleCount = 0L;
        private double sumResidual = 0.0;
        private double sumAbsoluteResidual = 0.0;

        private void record(double residual) {
            sampleCount++;
            sumResidual += residual;
            sumAbsoluteResidual += Math.abs(residual);
        }

        private double meanResidual() {
            return sampleCount == 0L ? 0.0 : sumResidual / sampleCount;
        }

        private double meanAbsoluteResidual() {
            return sampleCount == 0L ? 0.0 : sumAbsoluteResidual / sampleCount;
        }

        private long sampleCount() {
            return sampleCount;
        }
    }
}
