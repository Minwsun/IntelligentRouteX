package com.routechain.simulation;

import com.routechain.infra.ArtifactPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Runs a triage experiment inside a detached git worktree and isolated artifact root.
 */
public final class BenchmarkExperimentRunner {
    private BenchmarkExperimentRunner() {}

    public static void main(String[] args) {
        String hypothesisId = args.length > 0 && !args[0].isBlank()
                ? args[0].trim()
                : "phase31-route-triage";
        String changedKnobGroup = args.length > 1 && !args[1].isBlank()
                ? args[1].trim()
                : "heavy-rain-reach";
        List<String> targetBuckets = BenchmarkGovernanceSupport.normalizeBuckets(args.length > 2 ? args[2] : "");

        BenchmarkBaselineRef baseline = BenchmarkGovernanceSupport.resolveBaselineForLane("certification");
        if (baseline == null) {
            throw new IllegalStateException("No clean canonical baseline is registered or available for certification");
        }

        String gitRevision = baseline.gitRevision();
        String experimentId = "route-exp-" + Instant.now().toEpochMilli();
        Path repoRoot = Path.of("").toAbsolutePath().normalize();
        Path experimentArtifactRoot = repoRoot.resolve("build")
                .resolve("routechain-apex")
                .resolve("experiments")
                .resolve(experimentId);
        Path worktreeRoot = repoRoot.resolve("build")
                .resolve("routechain-apex")
                .resolve("experiment-worktrees")
                .resolve(experimentId);

        List<String> specNotes = new ArrayList<>();
        specNotes.add("triage experiment artifacts are isolated from canonical benchmark artifacts");
        specNotes.add("source tree is a detached git worktree at baseline git revision " + gitRevision);
        BenchmarkExperimentSpec spec = new BenchmarkExperimentSpec(
                BenchmarkSchema.VERSION,
                experimentId,
                Instant.now(),
                hypothesisId,
                "triage",
                baseline.checkpointId(),
                baseline.laneName(),
                gitRevision,
                "CLEAN_CANONICAL_CHECKPOINT",
                changedKnobGroup,
                targetBuckets,
                "tuning",
                "detached-worktree",
                experimentArtifactRoot.toString(),
                specNotes
        );
        BenchmarkArtifactWriter.writeBenchmarkExperimentSpec(spec);

        boolean worktreeCreated = false;
        try {
            createDirectories(worktreeRoot.getParent());
            createDirectories(experimentArtifactRoot.getParent());

            BenchmarkGovernanceSupport.CommandResult addWorktree = BenchmarkGovernanceSupport.runCommand(
                    List.of("git", "worktree", "add", "--detach", worktreeRoot.toString(), gitRevision),
                    repoRoot);
            if (!addWorktree.success()) {
                throw new IllegalStateException("Unable to create detached worktree: " + addWorktree.output());
            }
            worktreeCreated = true;

            List<String> gradleCommand = List.of(
                    "gradlew.bat",
                    "--no-daemon",
                    "phase31RouteQualityTriageLane",
                    "benchmarkCheckpointCertificationSummary",
                    "-D" + ArtifactPaths.ARTIFACT_ROOT_PROPERTY + "=" + experimentArtifactRoot
            );
            BenchmarkGovernanceSupport.CommandResult gradleRun = BenchmarkGovernanceSupport.runCommand(
                    gradleCommand,
                    worktreeRoot);
            if (!gradleRun.success()) {
                throw new IllegalStateException("Experiment lane failed: " + gradleRun.output());
            }

            BenchmarkAuthoritySnapshot authoritySnapshot =
                    BenchmarkGovernanceSupport.loadAuthoritySnapshot(experimentArtifactRoot, "certification");
            BenchmarkCheckpointSummary checkpointSummary =
                    BenchmarkGovernanceSupport.loadCheckpointSummary(experimentArtifactRoot, "certification");
            RouteQualityBlockerSummary blockerSummary =
                    BenchmarkGovernanceSupport.loadBlockerSummary(experimentArtifactRoot, "certification");
            if (checkpointSummary == null) {
                throw new IllegalStateException("Experiment did not materialize benchmark checkpoint summary");
            }

            List<String> resultNotes = new ArrayList<>();
            resultNotes.add("baseline checkpoint=" + baseline.checkpointId());
            resultNotes.add("target buckets=" + String.join("|", targetBuckets));
            resultNotes.add("promotion requires canonical re-check; triage result alone cannot update baseline");
            if (authoritySnapshot != null) {
                resultNotes.add("authority status=" + BenchmarkGovernanceSupport.authorityStatus(authoritySnapshot));
            }
            if (checkpointSummary.degradedCheckpoint()) {
                resultNotes.add("checkpoint degraded=" + String.join("; ", checkpointSummary.degradedReasons()));
            }
            boolean promising = "CLEAN_CANONICAL_CHECKPOINT".equals(checkpointSummary.checkpointStatus())
                    && !checkpointSummary.degradedCheckpoint()
                    && checkpointSummary.repoSummaryPresent()
                    && blockerSummary != null;

            BenchmarkExperimentResult result = new BenchmarkExperimentResult(
                    BenchmarkSchema.VERSION,
                    experimentId,
                    Instant.now(),
                    hypothesisId,
                    "triage",
                    baseline.checkpointId(),
                    checkpointSummary.checkpointId(),
                    checkpointSummary.gitRevision(),
                    authoritySnapshot == null
                            ? "UNKNOWN"
                            : BenchmarkGovernanceSupport.authorityStatus(authoritySnapshot),
                    checkpointSummary.checkpointStatus(),
                    "tuning",
                    experimentArtifactRoot.toString(),
                    checkpointSummary.routeAiVerdict(),
                    checkpointSummary.repoVerdict(),
                    checkpointSummary.routingVerdict(),
                    blockerSummary != null,
                    promising,
                    resultNotes
            );
            BenchmarkArtifactWriter.writeBenchmarkExperimentResult(result);
            System.out.println("[BenchmarkExperiment] experimentId=" + experimentId
                    + " baseline=" + baseline.checkpointId()
                    + " candidate=" + checkpointSummary.checkpointId()
                    + " promising=" + promising
                    + " checkpointStatus=" + checkpointSummary.checkpointStatus());
        } finally {
            if (worktreeCreated) {
                BenchmarkGovernanceSupport.runCommand(
                        List.of("git", "worktree", "remove", "--force", worktreeRoot.toString()),
                        repoRoot);
            }
        }
    }

    private static void createDirectories(Path path) {
        try {
            if (path != null) {
                Files.createDirectories(path);
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Unable to create experiment directory " + path, e);
        }
    }
}
