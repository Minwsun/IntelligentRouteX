package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Final human-readable verdict for whether the system has real AI influence
 * and whether the routing behavior is intelligent enough under benchmark.
 */
public record RouteIntelligenceVerdictSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        String javaVersion,
        String aiVerdict,
        String routingVerdict,
        String confidence,
        String claimReadiness,
        boolean architectureAuditPass,
        boolean ablationEvidencePass,
        boolean repoCertificationPass,
        boolean legacyReferenceWarning,
        int detectedRequiredComponentCount,
        int requiredComponentCount,
        int materialAblationCount,
        int requiredAblationCount,
        RouteAiCertificationSummary routeHotPathSummary,
        RepoIntelligenceCertificationSummary repoCertificationSummary,
        PublicResearchBenchmarkSummary publicResearchSummary,
        BatchIntelligenceCertificationSummary batchIntelligenceSummary,
        List<AiComponentEvidence> architectureEvidence,
        List<PolicyAblationResult> ablationEvidence,
        List<String> blockers,
        List<String> notes
) {
    public RouteIntelligenceVerdictSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? BenchmarkSchema.VERSION
                : schemaVersion;
        laneName = laneName == null || laneName.isBlank() ? "smoke" : laneName;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        javaVersion = javaVersion == null || javaVersion.isBlank() ? "unknown" : javaVersion;
        aiVerdict = aiVerdict == null || aiVerdict.isBlank() ? "UNASSESSED" : aiVerdict;
        routingVerdict = routingVerdict == null || routingVerdict.isBlank() ? "UNASSESSED" : routingVerdict;
        confidence = confidence == null || confidence.isBlank() ? "LOW" : confidence;
        claimReadiness = claimReadiness == null || claimReadiness.isBlank()
                ? "REVIEW_REQUIRED"
                : claimReadiness;
        architectureEvidence = architectureEvidence == null ? List.of() : List.copyOf(architectureEvidence);
        ablationEvidence = ablationEvidence == null ? List.of() : List.copyOf(ablationEvidence);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
