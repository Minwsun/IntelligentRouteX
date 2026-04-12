package com.routechain.simulation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Makes promotion decisions explicit instead of hiding them inside triage or checkpoint runners.
 */
public final class BenchmarkPromotionRunner {
    private BenchmarkPromotionRunner() {}

    public static void main(String[] args) {
        String mode = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "checkpoint";
        if ("experiment".equals(mode)) {
            if (args.length < 2 || args[1].isBlank()) {
                throw new IllegalArgumentException("Experiment mode requires an experiment id");
            }
            decideForExperiment(args[1].trim());
            return;
        }

        String laneName = args.length > 1 && !args[1].isBlank()
                ? args[1].trim().toLowerCase(Locale.ROOT)
                : "certification";
        decideForCheckpoint(laneName);
    }

    private static void decideForCheckpoint(String laneName) {
        BenchmarkCheckpointSummary checkpoint = BenchmarkGovernanceSupport.loadCheckpointSummary(laneName);
        if (checkpoint == null) {
            throw new IllegalStateException("Missing benchmark checkpoint summary for lane " + laneName);
        }

        BenchmarkBaselineRef previousBaseline = BenchmarkGovernanceSupport.loadCurrentBaseline();
        List<String> notes = new ArrayList<>();
        notes.add("checkpoint status=" + checkpoint.checkpointStatus());
        if (!checkpoint.degradedReasons().isEmpty()) {
            notes.add("degraded reasons=" + String.join("; ", checkpoint.degradedReasons()));
        }

        boolean approved = BenchmarkGovernanceSupport.promotionEligible(checkpoint);
        BenchmarkPromotionDecision decision = new BenchmarkPromotionDecision(
                BenchmarkSchema.VERSION,
                "decision-" + laneName + "-" + Instant.now().toEpochMilli(),
                Instant.now(),
                "checkpoint",
                "",
                previousBaseline == null ? "" : previousBaseline.checkpointId(),
                checkpoint.checkpointId(),
                laneName,
                approved ? "APPROVED_BASELINE" : "REJECTED_BASELINE",
                false,
                false,
                notes
        );
        BenchmarkArtifactWriter.writeBenchmarkPromotionDecision(decision);

        if (!approved) {
            System.out.println("[BenchmarkPromotion] lane=" + laneName
                    + " decision=" + decision.decision()
                    + " candidate=" + checkpoint.checkpointId());
            return;
        }

        BenchmarkBaselineRef baseline = new BenchmarkBaselineRef(
                BenchmarkSchema.VERSION,
                "baseline-" + laneName + "-" + Instant.now().toEpochMilli(),
                checkpoint.checkpointId(),
                laneName,
                Instant.now(),
                checkpoint.gitRevision(),
                checkpoint.checkpointStatus(),
                decision.decisionId(),
                previousBaseline == null ? "" : previousBaseline.baselineId(),
                true,
                List.of(
                        "promoted from clean canonical checkpoint",
                        "route/result verdicts remain whatever the checkpoint measured; promotion only certifies baseline cleanliness"
                )
        );
        BenchmarkArtifactWriter.writeBenchmarkBaselineRef(baseline);
        System.out.println("[BenchmarkPromotion] lane=" + laneName
                + " decision=" + decision.decision()
                + " baselineId=" + baseline.baselineId()
                + " checkpointId=" + baseline.checkpointId());
    }

    private static void decideForExperiment(String experimentId) {
        java.nio.file.Path resultPath = BenchmarkGovernanceSupport.governanceDir()
                .resolve("experiments")
                .resolve(experimentId + "-result.json");
        BenchmarkExperimentResult result = readExperimentResult(resultPath);
        BenchmarkBaselineRef baseline = BenchmarkGovernanceSupport.loadCurrentBaseline();
        List<String> notes = new ArrayList<>();
        notes.add("triage experiments cannot be promoted directly");
        notes.add("run canonical checkpoint again on the candidate code before promotion");
        if (result != null) {
            notes.add("experiment promising=" + result.promising());
            notes.add("experiment checkpoint status=" + result.checkpointStatus());
        }
        BenchmarkPromotionDecision decision = new BenchmarkPromotionDecision(
                BenchmarkSchema.VERSION,
                "decision-experiment-" + experimentId + "-" + Instant.now().toEpochMilli(),
                Instant.now(),
                "experiment",
                experimentId,
                baseline == null ? "" : baseline.checkpointId(),
                result == null ? "" : result.candidateCheckpointId(),
                result == null ? "triage" : result.laneType(),
                "REQUIRES_CANONICAL_RECHECK",
                true,
                true,
                notes
        );
        BenchmarkArtifactWriter.writeBenchmarkPromotionDecision(decision);
        System.out.println("[BenchmarkPromotion] experimentId=" + experimentId
                + " decision=" + decision.decision());
    }

    private static BenchmarkExperimentResult readExperimentResult(java.nio.file.Path path) {
        try {
            if (java.nio.file.Files.notExists(path)) {
                throw new IllegalStateException("Missing experiment result at " + path);
            }
            return com.routechain.infra.GsonSupport.pretty().fromJson(
                    java.nio.file.Files.readString(path),
                    BenchmarkExperimentResult.class
            );
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to read experiment result " + path, e);
        }
    }
}
