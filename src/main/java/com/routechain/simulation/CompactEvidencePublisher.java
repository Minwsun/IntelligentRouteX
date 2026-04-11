package com.routechain.simulation;

import com.routechain.core.AdaptiveScoreBreakdown;
import com.routechain.core.CompactDecisionExplanation;
import com.routechain.core.CompactDecisionResolution;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactEvidenceBundle;
import com.routechain.core.DriftMonitor;
import com.routechain.core.WeightSnapshot;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class CompactEvidencePublisher {
    private volatile CompactEvidenceBundle latestEvidence = CompactEvidenceBundle.empty();
    private volatile CompactRuntimeStatusView latestStatus = CompactRuntimeStatusView.empty();

    public void reset() {
        latestEvidence = CompactEvidenceBundle.empty();
        latestStatus = CompactRuntimeStatusView.empty();
    }

    public void publishDecision(String runId,
                                String modeName,
                                Instant decisionTime,
                                CompactDispatchDecision decision,
                                WeightSnapshot snapshotAfter,
                                String snapshotTag,
                                boolean rollbackAvailable,
                                boolean learningFrozen) {
        latestEvidence = new CompactEvidenceBundle(
                runId,
                modeName,
                decisionTime,
                decision.selectedPlanEvidence().stream().map(e -> e.bundleId() + ":" + e.planType().name()).toList(),
                decision.explanations(),
                decision.weightSnapshotBefore(),
                snapshotAfter,
                latestEvidence.latestResolution());

        AdaptiveScoreBreakdown primaryBreakdown = decision.explanations().isEmpty()
                ? null
                : decision.explanations().get(0).breakdown();
        latestStatus = buildStatus(
                primaryBreakdown,
                snapshotTag,
                rollbackAvailable,
                learningFrozen,
                0.0,
                0.0,
                0.0);
    }

    public void publishResolution(CompactDecisionResolution resolution,
                                  String snapshotTag,
                                  boolean rollbackAvailable,
                                  boolean learningFrozen,
                                  DriftMonitor.DriftAssessment assessment) {
        latestEvidence = latestEvidence.withResolution(resolution.weightSnapshotAfter(), resolution);
        latestStatus = buildStatus(
                resolution.scoreBreakdown(),
                snapshotTag,
                rollbackAvailable,
                learningFrozen,
                assessment == null ? 0.0 : assessment.rollingMae(),
                assessment == null ? 0.0 : assessment.rollingRewardMean(),
                assessment == null ? 0.0 : assessment.verdictPassRate());
    }

    public CompactEvidenceBundle latestEvidence() {
        return latestEvidence;
    }

    public CompactRuntimeStatusView latestStatus() {
        return latestStatus;
    }

    private CompactRuntimeStatusView buildStatus(AdaptiveScoreBreakdown breakdown,
                                                 String snapshotTag,
                                                 boolean rollbackAvailable,
                                                 boolean learningFrozen,
                                                 double rollingMae,
                                                 double rollingRewardMean,
                                                 double verdictPassRate) {
        if (breakdown == null) {
            return new CompactRuntimeStatusView(
                    "COMPACT",
                    "CLEAR_NORMAL",
                    List.of(),
                    latestEvidence.weightSnapshotAfter() == null
                            ? java.util.Map.of()
                            : latestEvidence.weightSnapshotAfter().dualPenalties(),
                    snapshotTag,
                    rollbackAvailable,
                    learningFrozen,
                    rollingMae,
                    rollingRewardMean,
                    verdictPassRate);
        }
        List<String> top = breakdown.featureContributions().entrySet().stream()
                .sorted(Comparator.comparingDouble(entry -> -Math.abs(entry.getValue())))
                .limit(3)
                .map(entry -> entry.getKey() + "=" + String.format("%.2f", entry.getValue()))
                .toList();
        return new CompactRuntimeStatusView(
                "COMPACT",
                breakdown.regimeKey().name(),
                top,
                breakdown.dualPenalties(),
                snapshotTag,
                rollbackAvailable,
                learningFrozen,
                rollingMae,
                rollingRewardMean,
                verdictPassRate);
    }
}
