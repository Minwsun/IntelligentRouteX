package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Emits a final verdict for "does this system use real AI?" and
 * "is the route behavior intelligent?" using architecture audit plus benchmark evidence.
 */
public final class RouteIntelligenceVerdictRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path ROOT = Path.of("build", "routechain-apex", "benchmarks");
    private static final Path CERTIFICATION_DIR = ROOT.resolve("certification");
    private static final Path ABLATIONS_DIR = ROOT.resolve("ablations");
    private static final Path OMEGA_AGENT_SOURCE = Path.of(
            "src", "main", "java", "com", "routechain", "ai", "OmegaDispatchAgent.java");
    private static final List<String> REQUIRED_ABLATION_POLICIES = List.of(
            "NO_NEURAL_PRIOR",
            "NO_CONTINUATION",
            "NO_BATCH_VALUE",
            "NO_STRESS_AI_GATE",
            "NO_POSITIONING_MODEL"
    );

    private RouteIntelligenceVerdictRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "smoke";
        RouteAiCertificationSummary routeSummary = ensureRouteSummary();
        RepoIntelligenceCertificationSummary repoSummary = ensureRepoSummary(laneName);
        PublicResearchBenchmarkSummary publicResearchSummary = ensurePublicResearchSummary(laneName);
        BatchIntelligenceCertificationSummary batchSummary = ensureBatchIntelligenceSummary(laneName);
        BenchmarkAuthoritySnapshot authoritySnapshot = BenchmarkCertificationSupport.collectAuthoritySnapshot(laneName);
        List<AiComponentEvidence> architectureEvidence = auditOmegaArchitecture();
        List<PolicyAblationResult> ablationEvidence = readAblationEvidence(laneName);

        int requiredComponentCount = (int) architectureEvidence.stream()
                .filter(AiComponentEvidence::required)
                .count();
        int detectedRequiredComponentCount = (int) architectureEvidence.stream()
                .filter(entry -> entry.required() && entry.detected())
                .count();
        boolean architectureAuditPass = requiredComponentCount > 0
                && detectedRequiredComponentCount == requiredComponentCount;

        Map<String, PolicyAblationResult> ablationsByMode = ablationEvidence.stream()
                .collect(java.util.stream.Collectors.toMap(
                        PolicyAblationResult::baselinePolicy,
                        result -> result,
                        (left, right) -> left));
        int materialAblationCount = 0;
        boolean coreModelImpactDetected = false;
        List<String> blockers = new ArrayList<>();

        for (String requiredMode : REQUIRED_ABLATION_POLICIES) {
            PolicyAblationResult result = ablationsByMode.get(requiredMode);
            if (result == null) {
                blockers.add("missing ablation evidence for " + requiredMode);
                continue;
            }
            if (isMaterialImpact(result)) {
                materialAblationCount++;
                if ("NO_NEURAL_PRIOR".equals(requiredMode) || "NO_CONTINUATION".equals(requiredMode)) {
                    coreModelImpactDetected = true;
                }
            }
        }

        boolean ablationEvidencePass = ablationEvidence.size() >= REQUIRED_ABLATION_POLICIES.size()
                && blockers.stream().noneMatch(blocker -> blocker.startsWith("missing ablation evidence"));
        String aiVerdict = determineAiVerdict(
                architectureAuditPass,
                ablationEvidencePass,
                materialAblationCount,
                coreModelImpactDetected);
        String routingVerdict = determineRoutingVerdict(laneName, repoSummary, publicResearchSummary, batchSummary);
        String confidence = determineConfidence(
                laneName,
                architectureAuditPass,
                ablationEvidencePass,
                publicResearchSummary,
                batchSummary);
        String claimReadiness = determineClaimReadiness(
                aiVerdict,
                routingVerdict,
                confidence,
                repoSummary,
                publicResearchSummary,
                batchSummary);

        if (!architectureAuditPass) {
            blockers.add("architecture audit did not find every required live-path component");
        }
        if (!repoSummary.overallPass()) {
            blockers.add("repo certification lane did not pass all absolute gates");
        }
        if (isLegacyUnderperforming(repoSummary)) {
            blockers.add("Omega still underperforms Legacy on at least one major reference delta");
        }
        if (authoritySnapshot.authorityDirty()) {
            blockers.add("benchmark authority is dirty: tracked changes exist in benchmark-sensitive paths");
        }
        if (requiresDeepEvidence(laneName) && (publicResearchSummary == null || !publicResearchSummary.overallPass())) {
            blockers.add("public research benchmark evidence is missing or failing");
        }
        if (requiresDeepEvidence(laneName) && (batchSummary == null || !batchSummary.overallPass())) {
            blockers.add("batch intelligence certification is missing or failing");
        }
        if ("smoke".equals(laneName)) {
            blockers.add("only smoke evidence is present; certification-grade confidence needs the certification lane");
        }

        List<String> notes = new ArrayList<>();
        notes.add("route hot-path smoke verdict=" + (routeSummary.overallPass() ? "PASS" : "FAIL"));
        notes.add("repo lane verdict=" + repoSummary.overallVerdict());
        if (publicResearchSummary != null) {
            notes.add("public research benchmark overallPass=" + publicResearchSummary.overallPass());
        }
        if (batchSummary != null) {
            notes.add("batch intelligence overallPass=" + batchSummary.overallPass());
        }
        notes.add("benchmark authority dirty=" + authoritySnapshot.authorityDirty()
                + " trackedDirty=" + authoritySnapshot.workspaceDirty());
        notes.add("required components=" + detectedRequiredComponentCount + "/" + requiredComponentCount);
        notes.add("material ablations=" + materialAblationCount + "/" + REQUIRED_ABLATION_POLICIES.size());
        if (repoSummary.legacyReference() != null) {
            notes.addAll(repoSummary.legacyReference().notes());
        }

        RouteIntelligenceVerdictSummary summary = new RouteIntelligenceVerdictSummary(
                BenchmarkSchema.VERSION,
                laneName,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                System.getProperty("java.version", "unknown"),
                aiVerdict,
                routingVerdict,
                confidence,
                claimReadiness,
                architectureAuditPass,
                ablationEvidencePass,
                repoSummary.overallPass(),
                repoSummary.legacyReference() != null && repoSummary.legacyReference().warning(),
                detectedRequiredComponentCount,
                requiredComponentCount,
                materialAblationCount,
                REQUIRED_ABLATION_POLICIES.size(),
                routeSummary,
                repoSummary,
                publicResearchSummary,
                batchSummary,
                architectureEvidence,
                ablationEvidence,
                blockers,
                notes
        );

        BenchmarkArtifactWriter.writeBenchmarkAuthoritySnapshot(authoritySnapshot);
        BenchmarkArtifactWriter.writeRouteIntelligenceVerdictSummary(summary);
        System.out.println("[RouteIntelligenceVerdict] lane=" + laneName
                + " ai=" + aiVerdict
                + " routing=" + routingVerdict
                + " confidence=" + confidence
                + " claimReadiness=" + claimReadiness);
    }

    private static RouteAiCertificationSummary ensureRouteSummary() {
        Path path = CERTIFICATION_DIR.resolve("route-ai-certification-smoke.json");
        if (Files.notExists(path)) {
            try {
                RouteAiCertificationRunner.main(new String[]{"smoke"});
            } catch (Exception ignored) {
                // The runner writes the summary before throwing on a failed gate.
            }
        }
        return readRequiredJson(path, RouteAiCertificationSummary.class, "route AI smoke summary");
    }

    private static RepoIntelligenceCertificationSummary ensureRepoSummary(String laneName) {
        Path path = CERTIFICATION_DIR.resolve("repo-intelligence-" + laneName + ".json");
        if (Files.notExists(path)) {
            try {
                RepoIntelligenceCertificationRunner.main(new String[]{laneName});
            } catch (Exception ignored) {
                // The runner writes the summary before throwing on a failed gate.
            }
        }
        return readRequiredJson(path, RepoIntelligenceCertificationSummary.class, "repo intelligence summary");
    }

    private static PublicResearchBenchmarkSummary ensurePublicResearchSummary(String laneName) {
        if (!requiresDeepEvidence(laneName)) {
            return null;
        }
        Path path = CERTIFICATION_DIR.resolve("public-research-benchmark-" + laneName + ".json");
        if (Files.notExists(path)) {
            try {
                PublicResearchBenchmarkCertificationRunner.main(new String[]{laneName});
            } catch (Exception ignored) {
                // Summary is written before failure is thrown.
            }
        }
        return readRequiredJson(path, PublicResearchBenchmarkSummary.class, "public research benchmark summary");
    }

    private static BatchIntelligenceCertificationSummary ensureBatchIntelligenceSummary(String laneName) {
        if (!requiresDeepEvidence(laneName)) {
            return null;
        }
        Path path = CERTIFICATION_DIR.resolve("batch-intelligence-certification-" + laneName + ".json");
        if (Files.notExists(path)) {
            try {
                BatchIntelligenceCertificationRunner.main(new String[]{laneName});
            } catch (Exception ignored) {
                // Summary is written before failure is thrown.
            }
        }
        return readRequiredJson(path, BatchIntelligenceCertificationSummary.class, "batch intelligence summary");
    }

    private static List<AiComponentEvidence> auditOmegaArchitecture() {
        String source = readRequiredSource();
        return List.of(
                audit(source, "ETA model hot path", true, "etaModel.predict", "ETA prediction is applied per plan."),
                audit(source, "Late risk model", true, "lateRiskModel.predict", "Late-risk scoring affects on-time gating."),
                audit(source, "Cancel risk model", true, "cancelRiskModel.predict", "Cancellation risk is part of live plan scoring."),
                audit(source, "Route value model", true, "planRanker.rank(planFeatures)", "Plan utility is ranked by a learned route-value model."),
                audit(source, "Batch value model", true, "batchValueModel.predict", "Batch admission uses a learned value model instead of fixed bundle rules."),
                audit(source, "Continuation model", true, "double continuationValue = continuationValueModel", "Post-drop continuation value shapes final ranking."),
                audit(source, "Stress rescue model", true, "stressRescueModel.predict", "Stress fallback rescue is gated by a learned model."),
                audit(source, "Driver positioning model", true, "positioningValueModel.predict", "Idle driver landing and reposition value is predicted by a learned model."),
                audit(source, "Uncertainty model", true, "UncertaintyEstimator.Prediction pred = uncertaintyEstimator", "Uncertainty is consulted during robust scoring."),
                audit(source, "Graph affinity scoring", true, "getGraphAffinityScorer().scorePlan", "Graph shadow features are used on the live path."),
                audit(source, "Neural route prior", true, "neuralRoutePriorClient.resolve", "Neural prior is queried unless ablated."),
                audit(source, "Replay retraining hook", true, "replayTrainer.retrain", "Adaptive retraining hook exists in live engine ticks."),
                audit(source, "Ablation control NO_NEURAL_PRIOR", true, "AblationMode.NO_NEURAL_PRIOR", "Neural prior can be disabled for proof runs."),
                audit(source, "Ablation control NO_CONTINUATION", true, "AblationMode.NO_CONTINUATION", "Continuation value can be disabled for proof runs."),
                audit(source, "Ablation control NO_BATCH_VALUE", true, "AblationMode.NO_BATCH_VALUE", "Batch value modeling can be disabled for proof runs."),
                audit(source, "Ablation control NO_STRESS_AI_GATE", true, "AblationMode.NO_STRESS_AI_GATE", "Stress rescue AI gate can be disabled for proof runs."),
                audit(source, "Ablation control NO_POSITIONING_MODEL", true, "AblationMode.NO_POSITIONING_MODEL", "Driver positioning AI can be disabled for proof runs.")
        );
    }

    private static AiComponentEvidence audit(String source,
                                             String componentName,
                                             boolean required,
                                             String evidenceAnchor,
                                             String explanation) {
        return new AiComponentEvidence(
                componentName,
                required,
                source.contains(evidenceAnchor),
                evidenceAnchor,
                explanation
        );
    }

    private static List<PolicyAblationResult> readAblationEvidence(String laneName) {
        try {
            if (Files.notExists(ABLATIONS_DIR)) {
                return List.of();
            }
            List<PolicyAblationResult> results = new ArrayList<>();
            try (var stream = Files.list(ABLATIONS_DIR)) {
                for (Path path : stream.filter(file -> file.getFileName().toString().endsWith(".json")).toList()) {
                    PolicyAblationResult result = GSON.fromJson(
                            Files.readString(path, StandardCharsets.UTF_8),
                            PolicyAblationResult.class);
                    if (result != null
                            && result.ablationId() != null
                            && result.ablationId().startsWith("ai-influence-" + laneName + "-")) {
                        results.add(result);
                    }
                }
            }
            return results;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read AI influence ablation artifacts", e);
        }
    }

    private static boolean isMaterialImpact(PolicyAblationResult result) {
        if (result == null) {
            return false;
        }
        boolean gainImpact = result.overallGainPercent() >= 0.5;
        boolean completionImpact = result.completionDeltaSummary() != null
                && result.completionDeltaSummary().mean() >= 0.5;
        boolean deadheadImpact = result.deadheadDeltaSummary() != null
                && result.deadheadDeltaSummary().mean() <= -0.5;
        boolean additionalImpact = result.additionalSummaries().stream().anyMatch(summary ->
                ("routingScoreDelta".equals(summary.metricName()) && summary.mean() >= 0.02)
                        || ("networkScoreDelta".equals(summary.metricName()) && summary.mean() >= 0.02)
                        || ("postDropOrderHitRateDelta".equals(summary.metricName()) && summary.mean() >= 0.5));
        return gainImpact || completionImpact || deadheadImpact || additionalImpact;
    }

    private static String determineAiVerdict(boolean architectureAuditPass,
                                             boolean ablationEvidencePass,
                                             int materialAblationCount,
                                             boolean coreModelImpactDetected) {
        if (!architectureAuditPass) {
            return "NO";
        }
        if (!ablationEvidencePass) {
            return "PARTIAL";
        }
        if (materialAblationCount >= 2 && coreModelImpactDetected) {
            return "YES";
        }
        if (materialAblationCount >= 1) {
            return "PARTIAL";
        }
        return "NO";
    }

    private static String determineRoutingVerdict(String laneName,
                                                  RepoIntelligenceCertificationSummary repoSummary,
                                                  PublicResearchBenchmarkSummary publicResearchSummary,
                                                  BatchIntelligenceCertificationSummary batchSummary) {
        if (repoSummary == null
                || !repoSummary.overallPass()
                || repoSummary.routeQualityGate() == null
                || !repoSummary.routeQualityGate().pass()
                || repoSummary.continuityGate() == null
                || !repoSummary.continuityGate().pass()
                || repoSummary.stressSafetyGate() == null
                || !repoSummary.stressSafetyGate().pass()) {
            return "NO";
        }
        if (requiresDeepEvidence(laneName)
                && (publicResearchSummary == null || !publicResearchSummary.overallPass()
                || batchSummary == null || !batchSummary.overallPass())) {
            return "PARTIAL";
        }
        if ("smoke".equals(laneName) || isLegacyUnderperforming(repoSummary)) {
            return "PARTIAL";
        }
        return "YES";
    }

    private static String determineConfidence(String laneName,
                                              boolean architectureAuditPass,
                                              boolean ablationEvidencePass,
                                              PublicResearchBenchmarkSummary publicResearchSummary,
                                              BatchIntelligenceCertificationSummary batchSummary) {
        if (!architectureAuditPass) {
            return "LOW";
        }
        if ("nightly".equals(laneName) || "certification".equals(laneName)) {
            return ablationEvidencePass
                    && publicResearchSummary != null
                    && publicResearchSummary.overallPass()
                    && batchSummary != null
                    && batchSummary.overallPass()
                    ? "HIGH"
                    : "MEDIUM";
        }
        return ablationEvidencePass ? "MEDIUM" : "LOW";
    }

    private static String determineClaimReadiness(String aiVerdict,
                                                  String routingVerdict,
                                                  String confidence,
                                                  RepoIntelligenceCertificationSummary repoSummary,
                                                  PublicResearchBenchmarkSummary publicResearchSummary,
                                                  BatchIntelligenceCertificationSummary batchSummary) {
        if ("YES".equals(aiVerdict)
                && "YES".equals(routingVerdict)
                && "HIGH".equals(confidence)
                && publicResearchSummary != null
                && publicResearchSummary.overallPass()
                && batchSummary != null
                && batchSummary.overallPass()
                && !isLegacyUnderperforming(repoSummary)) {
            return "CUSTOMER_READY";
        }
        if (!"NO".equals(aiVerdict) && !"NO".equals(routingVerdict)) {
            return "INTERNAL_ONLY";
        }
        return "REVIEW_REQUIRED";
    }

    private static boolean requiresDeepEvidence(String laneName) {
        return "certification".equals(laneName) || "nightly".equals(laneName);
    }

    private static boolean isLegacyUnderperforming(RepoIntelligenceCertificationSummary repoSummary) {
        return repoSummary != null
                && repoSummary.legacyReference() != null
                && (repoSummary.legacyReference().latestOverallGainPercent() < 0.0
                || repoSummary.legacyReference().latestCompletionDelta() < 0.0
                || repoSummary.legacyReference().latestDeadheadDelta() > 0.0);
    }

    private static String readRequiredSource() {
        try {
            if (Files.notExists(OMEGA_AGENT_SOURCE)) {
                throw new IllegalStateException("Missing Omega source file at " + OMEGA_AGENT_SOURCE);
            }
            return Files.readString(OMEGA_AGENT_SOURCE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read OmegaDispatchAgent source", e);
        }
    }

    private static <T> T readRequiredJson(Path path, Class<T> type, String label) {
        try {
            if (Files.notExists(path)) {
                throw new IllegalStateException("Missing " + label + " at " + path);
            }
            return GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read " + label, e);
        }
    }
}
