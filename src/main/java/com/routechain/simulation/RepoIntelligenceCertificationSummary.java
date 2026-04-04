package com.routechain.simulation;

import java.time.Instant;
import java.util.List;

/**
 * Final pass/fail summary for one repo-level intelligence lane.
 */
public record RepoIntelligenceCertificationSummary(
        String schemaVersion,
        String laneName,
        Instant generatedAt,
        String gitRevision,
        String javaVersion,
        String environmentProfile,
        List<Long> seedSet,
        List<String> scenarioSet,
        CertificationGateResult correctnessGate,
        CertificationGateResult latencyGate,
        CertificationGateResult routeQualityGate,
        CertificationGateResult continuityGate,
        CertificationGateResult stressSafetyGate,
        CertificationGateResult auxiliaryGate,
        LegacyReferenceResult legacyReference,
        List<ScenarioGroupCertificationResult> scenarioGroups,
        boolean overallPass,
        String overallVerdict,
        List<String> notes
) {
    public RepoIntelligenceCertificationSummary {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? BenchmarkSchema.VERSION : schemaVersion;
        generatedAt = generatedAt == null ? Instant.now() : generatedAt;
        gitRevision = gitRevision == null || gitRevision.isBlank() ? "unknown" : gitRevision;
        javaVersion = javaVersion == null || javaVersion.isBlank() ? "unknown" : javaVersion;
        environmentProfile = environmentProfile == null || environmentProfile.isBlank()
                ? "unknown"
                : environmentProfile;
        seedSet = seedSet == null ? List.of() : List.copyOf(seedSet);
        scenarioSet = scenarioSet == null ? List.of() : List.copyOf(scenarioSet);
        scenarioGroups = scenarioGroups == null ? List.of() : List.copyOf(scenarioGroups);
        notes = notes == null ? List.of() : List.copyOf(notes);
        overallVerdict = overallVerdict == null || overallVerdict.isBlank()
                ? (overallPass ? "PASS" : "FAIL")
                : overallVerdict;
    }
}
