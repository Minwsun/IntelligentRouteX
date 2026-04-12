package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.ArtifactPaths;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Builds a small checkpoint pack that says whether the current benchmark lane is
 * canonical-clean or only usable for triage.
 */
public final class BenchmarkCheckpointRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path CERTIFICATION_DIR = ArtifactPaths.benchmarksRoot().resolve("certification");

    private BenchmarkCheckpointRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "smoke";

        BenchmarkAuthoritySnapshot authority = readOptionalJson(
                CERTIFICATION_DIR.resolve("benchmark-authority-" + laneName + ".json"),
                BenchmarkAuthoritySnapshot.class);
        if (authority == null) {
            authority = BenchmarkCertificationSupport.collectAuthoritySnapshot(laneName);
            BenchmarkArtifactWriter.writeBenchmarkAuthoritySnapshot(authority);
        }
        RouteAiCertificationSummary routeAi = readOptionalJson(
                CERTIFICATION_DIR.resolve("route-ai-certification-" + laneName + ".json"),
                RouteAiCertificationSummary.class);
        boolean routeAiFallbackToSmoke = false;
        if (routeAi == null && !"smoke".equals(laneName)) {
            routeAi = readOptionalJson(
                    CERTIFICATION_DIR.resolve("route-ai-certification-smoke.json"),
                    RouteAiCertificationSummary.class);
            routeAiFallbackToSmoke = routeAi != null;
        }
        RepoIntelligenceCertificationSummary repo = readOptionalJson(
                CERTIFICATION_DIR.resolve("repo-intelligence-" + laneName + ".json"),
                RepoIntelligenceCertificationSummary.class);
        RouteIntelligenceVerdictSummary verdict = readOptionalJson(
                CERTIFICATION_DIR.resolve("route-intelligence-verdict-" + laneName + ".json"),
                RouteIntelligenceVerdictSummary.class);
        RouteQualityBlockerSummary blockers = readOptionalJson(
                CERTIFICATION_DIR.resolve("route-quality-blockers-" + laneName + ".json"),
                RouteQualityBlockerSummary.class);
        if (blockers == null && repo != null) {
            blockers = repo.routeQualityBlockerSummary();
        }

        String checkpointStatus = authority.authorityDetectionFailed()
                ? "AUTHORITY_CHECK_FAILED"
                : authority.authorityDirty()
                ? "DIRTY_TRIAGE_ONLY"
                : "CLEAN_CANONICAL_CHECKPOINT";
        Instant generatedAt = Instant.now();

        List<String> notes = new ArrayList<>();
        List<String> degradedReasons = new ArrayList<>();
        notes.add("authority checkpoint status=" + checkpointStatus.toLowerCase(Locale.ROOT));
        if (authority.authorityDetectionFailed()) {
            notes.add("git authority detection failed; do not treat this pack as a clean canonical checkpoint");
        } else if (authority.authorityDirty()) {
            notes.add("benchmark-sensitive tracked files are dirty; this pack is triage-only");
        } else if (authority.workspaceDirty()) {
            notes.add("tracked workspace is dirty outside benchmark-sensitive paths");
        } else {
            notes.add("tracked workspace is clean for the current canonical lane");
        }
        if (routeAi == null) {
            notes.add("missing route-ai-certification-" + laneName + " artifact");
            degradedReasons.add("missing route-ai summary");
        } else {
            notes.add("route-ai overallPass=" + routeAi.overallPass());
            if (routeAiFallbackToSmoke) {
                notes.add("route-ai checkpoint fell back to smoke hot-path summary for lane " + laneName);
                degradedReasons.add("route-ai summary fell back to smoke for non-smoke lane");
            }
        }
        if (repo == null) {
            notes.add("missing repo-intelligence-" + laneName + " artifact");
            degradedReasons.add("missing repo summary");
        } else {
            notes.add("repo summary verdict=" + repo.overallVerdict());
        }
        if (verdict == null) {
            notes.add("missing route-intelligence-verdict-" + laneName + " artifact");
            degradedReasons.add("missing route intelligence verdict summary");
        } else {
            notes.add("route verdict ai=" + verdict.aiVerdict() + " routing=" + verdict.routingVerdict());
        }
        if (blockers == null) {
            notes.add("route blocker summary missing or not materialized for this lane");
        } else {
            notes.add("route blocker summary present with " + blockers.bucketSummaries().size() + " bucket(s)");
        }
        boolean degradedCheckpoint = !degradedReasons.isEmpty();
        boolean promotionEligible = "CLEAN_CANONICAL_CHECKPOINT".equals(checkpointStatus) && !degradedCheckpoint;
        String gitRevision = BenchmarkCertificationSupport.resolveGitRevision();
        String checkpointId = BenchmarkGovernanceSupport.checkpointId(laneName, gitRevision, generatedAt);
        if (degradedCheckpoint) {
            notes.add("checkpoint degraded: " + String.join("; ", degradedReasons));
        }
        if (!promotionEligible) {
            notes.add("checkpoint is not promotion-eligible");
        }

        BenchmarkCheckpointSummary summary = new BenchmarkCheckpointSummary(
                BenchmarkSchema.VERSION,
                laneName,
                generatedAt,
                gitRevision,
                checkpointId,
                checkpointStatus,
                authority.cleanCheckpointEligible(),
                authority.triageOnly(),
                degradedCheckpoint,
                promotionEligible,
                routeAi != null,
                repo != null,
                verdict != null,
                blockers != null,
                routeAi == null ? "MISSING" : routeAi.overallPass() ? "PASS" : "FAIL",
                repo == null ? "MISSING" : repo.overallVerdict(),
                verdict == null ? "MISSING" : verdict.routingVerdict(),
                degradedReasons,
                notes
        );
        BenchmarkArtifactWriter.writeBenchmarkCheckpointSummary(summary);
        System.out.println("[BenchmarkCheckpoint] lane=" + laneName
                + " checkpointId=" + summary.checkpointId()
                + " status=" + summary.checkpointStatus()
                + " clean=" + summary.cleanCheckpoint()
                + " promotionEligible=" + summary.promotionEligible()
                + " routeAi=" + summary.routeAiVerdict()
                + " repo=" + summary.repoVerdict()
                + " routing=" + summary.routingVerdict());
    }

    private static <T> T readOptionalJson(Path path, Class<T> type) {
        try {
            if (Files.notExists(path)) {
                return null;
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read checkpoint input " + path, e);
        }
    }
}
