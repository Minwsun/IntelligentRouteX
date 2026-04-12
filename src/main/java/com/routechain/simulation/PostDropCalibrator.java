package com.routechain.simulation;

import com.routechain.core.DecisionLogRecord;
import com.routechain.core.PlanFeatureVector;
import com.routechain.core.ResolvedDecisionSample;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class PostDropCalibrator {
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public void reset() {
        buckets.clear();
    }

    public void record(ResolvedDecisionSample sample) {
        if (sample == null || !sample.eligibleForPostDropCalibration()) {
            return;
        }
        buckets.computeIfAbsent(bucketKey(sample.decisionLog()), ignored -> new Bucket()).record(sample);
    }

    public double calibrateHitProbability(DecisionLogRecord decisionLog) {
        Bucket bucket = buckets.get(bucketKey(decisionLog));
        if (bucket == null) {
            return PlanFeatureVector.clamp01(decisionLog.predictedPostDropDemandProbability());
        }
        return PlanFeatureVector.clamp01(bucket.observedHitRate());
    }

    public double calibrateNextIdleMinutes(DecisionLogRecord decisionLog) {
        Bucket bucket = buckets.get(bucketKey(decisionLog));
        if (bucket == null) {
            return Math.max(0.0, decisionLog.predictedNextOrderIdleMinutes());
        }
        return Math.max(0.0, decisionLog.predictedNextOrderIdleMinutes() + bucket.meanIdleResidual());
    }

    public double calibrateEmptyKm(DecisionLogRecord decisionLog) {
        Bucket bucket = buckets.get(bucketKey(decisionLog));
        if (bucket == null) {
            return Math.max(0.0, decisionLog.predictedPostCompletionEmptyKm());
        }
        return Math.max(0.0, decisionLog.predictedPostCompletionEmptyKm() + bucket.meanEmptyKmResidual());
    }

    public double postDropHitCalibrationGap() {
        return buckets.values().stream().mapToDouble(Bucket::meanHitGap).average().orElse(0.0);
    }

    public double nextIdleMaeMinutes() {
        return buckets.values().stream().mapToDouble(Bucket::meanIdleMae).average().orElse(0.0);
    }

    public double emptyKmMae() {
        return buckets.values().stream().mapToDouble(Bucket::meanEmptyKmMae).average().orElse(0.0);
    }

    public long sampleCount() {
        return buckets.values().stream().mapToLong(Bucket::sampleCount).sum();
    }

    private String bucketKey(DecisionLogRecord decisionLog) {
        int hourBucket = decisionLog.decisionTime() == null ? 0 : decisionLog.decisionTime().atZone(java.time.ZoneOffset.UTC).getHour() / 6;
        return decisionLog.regimeKey().name() + "|" + hourBucket;
    }

    private static final class Bucket {
        private long sampleCount = 0L;
        private double hitGapSum = 0.0;
        private double idleMaeSum = 0.0;
        private long idleSamples = 0L;
        private double idleResidualSum = 0.0;
        private double emptyKmMaeSum = 0.0;
        private long emptyKmSamples = 0L;
        private double emptyKmResidualSum = 0.0;
        private long postDropHitCount = 0L;

        private void record(ResolvedDecisionSample sample) {
            sampleCount++;
            double actualHit = sample.actualPostDropHit() ? 1.0 : 0.0;
            if (sample.actualPostDropHit()) {
                postDropHitCount++;
            }
            hitGapSum += Math.abs(sample.decisionLog().predictedPostDropDemandProbability() - actualHit);
            if (!Double.isNaN(sample.actualNextOrderIdleMinutes())) {
                idleMaeSum += Math.abs(sample.decisionLog().predictedNextOrderIdleMinutes() - sample.actualNextOrderIdleMinutes());
                idleResidualSum += sample.actualNextOrderIdleMinutes() - sample.decisionLog().predictedNextOrderIdleMinutes();
                idleSamples++;
            }
            if (!Double.isNaN(sample.actualPostCompletionEmptyKm())) {
                emptyKmMaeSum += Math.abs(sample.decisionLog().predictedPostCompletionEmptyKm() - sample.actualPostCompletionEmptyKm());
                emptyKmResidualSum += sample.actualPostCompletionEmptyKm() - sample.decisionLog().predictedPostCompletionEmptyKm();
                emptyKmSamples++;
            }
        }

        private double observedHitRate() {
            return sampleCount == 0L ? 0.0 : postDropHitCount / (double) sampleCount;
        }

        private double meanHitGap() {
            return sampleCount == 0L ? 0.0 : hitGapSum / sampleCount;
        }

        private double meanIdleMae() {
            return idleSamples == 0L ? 0.0 : idleMaeSum / idleSamples;
        }

        private double meanIdleResidual() {
            return idleSamples == 0L ? 0.0 : idleResidualSum / idleSamples;
        }

        private double meanEmptyKmMae() {
            return emptyKmSamples == 0L ? 0.0 : emptyKmMaeSum / emptyKmSamples;
        }

        private double meanEmptyKmResidual() {
            return emptyKmSamples == 0L ? 0.0 : emptyKmResidualSum / emptyKmSamples;
        }

        private long sampleCount() {
            return sampleCount;
        }
    }
}
