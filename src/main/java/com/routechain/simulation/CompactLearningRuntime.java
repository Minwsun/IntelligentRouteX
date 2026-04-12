package com.routechain.simulation;

import com.routechain.core.CompactDecisionLedger;
import com.routechain.core.CompactDecisionResolution;
import com.routechain.core.CompactPolicyConfig;
import com.routechain.core.DriftMonitor;
import com.routechain.core.WeightSnapshot;
import com.routechain.core.WeightSnapshotStore;
import com.routechain.core.AdaptiveWeightEngine;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CompactLearningRuntime {
    private final CompactPolicyConfig policyConfig;
    private final CompactDecisionLedger ledger = new CompactDecisionLedger();
    private final WeightSnapshotStore snapshotStore = new WeightSnapshotStore();
    private final DriftMonitor driftMonitor = new DriftMonitor();
    private final CompactCalibrationRuntime calibrationRuntime = new CompactCalibrationRuntime();
    private final Set<String> appliedDecisionStages = ConcurrentHashMap.newKeySet();
    private volatile String latestSnapshotTag = "snapshot-none";

    public CompactLearningRuntime(CompactPolicyConfig policyConfig) {
        this.policyConfig = policyConfig;
    }

    public void reset() {
        ledger.reset();
        calibrationRuntime.reset();
        appliedDecisionStages.clear();
        latestSnapshotTag = "snapshot-none";
    }

    public CompactDecisionLedger ledger() {
        return ledger;
    }

    public String latestSnapshotTag() {
        return latestSnapshotTag;
    }

    public boolean rollbackAvailable() {
        return snapshotStore.hasLastGood();
    }

    public CompactCalibrationRuntime calibrationRuntime() {
        return calibrationRuntime;
    }

    public DriftMonitor.DriftAssessment resolveAndApply(CompactDecisionResolution resolution,
                                                        Instant resolvedAt,
                                                        AdaptiveWeightEngine weightEngine) {
        if (resolution == null) {
            return null;
        }
        if (resolution.resolvedSample() == null
                || !appliedDecisionStages.add(stageKey(resolution.resolvedSample()))) {
            return driftMonitor.snapshot();
        }
        calibrationRuntime.recordResolvedSample(resolution.resolvedSample());
        if (!resolution.resolvedSample().eligibleForWeightUpdate()) {
            return driftMonitor.snapshot();
        }

        double predicted = resolution.resolvedSample().predictedReward();
        double actual = resolution.resolvedSample().actualReward();
        DriftMonitor.DriftAssessment assessment = driftMonitor.record(predicted, actual, actual >= 0.60);
        weightEngine.setLearningFrozen(assessment.freezeUpdates());
        weightEngine.setLearningRateMultiplier(assessment.freezeUpdates() ? 0.40 : 1.0);

        if (assessment.rollbackRecommended() && snapshotStore.hasLastGood()) {
            WeightSnapshot rollback = snapshotStore.rollbackToLastGood();
            if (rollback != null) {
                weightEngine.restore(rollback);
                latestSnapshotTag = "last-good";
                return assessment;
            }
        }

        boolean applied = weightEngine.recordResolvedSample(resolution.resolvedSample());
        if (applied) {
            WeightSnapshot latest = weightEngine.snapshot();
            latestSnapshotTag = snapshotStore.saveLatest(
                    latest,
                    resolution.traceId() + "-" + resolvedAt.toEpochMilli()).tag();
            if (!assessment.freezeUpdates() && actual >= 0.60) {
                snapshotStore.saveLastGood(latest);
            }
        }
        return assessment;
    }

    private String stageKey(com.routechain.core.ResolvedDecisionSample sample) {
        return sample.decisionId() + "|" + sample.outcomeStage().name();
    }

    public List<CompactDecisionResolution> expire(long currentTick,
                                                  Instant now) {
        return ledger.expirePostDrop(currentTick, now, policyConfig.postDropWindowTicks());
    }
}
