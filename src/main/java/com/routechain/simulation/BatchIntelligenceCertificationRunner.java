package com.routechain.simulation;

import com.google.gson.Gson;
import com.routechain.infra.DispatchFactSink;
import com.routechain.infra.GsonSupport;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Verifies that selected batch-2 plans beat local comparators instead of inflating batch count.
 */
public final class BatchIntelligenceCertificationRunner {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Path FACTS_ROOT = Path.of("build", "routechain-apex", "facts");
    private static final Path CANDIDATE_FACTS = FACTS_ROOT.resolve("dispatch_candidate_facts.jsonl");
    private static final Path DECISION_FACTS = FACTS_ROOT.resolve("dispatch_decision_facts.jsonl");

    private BatchIntelligenceCertificationRunner() {}

    public static void main(String[] args) {
        String laneName = args.length > 0 && !args[0].isBlank()
                ? args[0].trim().toLowerCase(Locale.ROOT)
                : "certification";
        List<DispatchFactSink.CandidateFact> candidates = readJsonl(CANDIDATE_FACTS, DispatchFactSink.CandidateFact.class);
        List<DispatchFactSink.DecisionFact> decisions = readJsonl(DECISION_FACTS, DispatchFactSink.DecisionFact.class);
        Map<String, DispatchFactSink.DecisionFact> decisionsByTrace = new HashMap<>();
        for (DispatchFactSink.DecisionFact decision : decisions) {
            decisionsByTrace.put(decision.traceId(), decision);
        }

        Map<String, List<DispatchFactSink.CandidateFact>> byContext = new HashMap<>();
        for (DispatchFactSink.CandidateFact fact : candidates) {
            if (!BenchmarkCertificationSupport.isCurrentOmegaDescriptor(fact.runId())) {
                continue;
            }
            byContext.computeIfAbsent(contextKey(fact), ignored -> new ArrayList<>()).add(fact);
        }

        List<Double> utilityVsSingle = new ArrayList<>();
        List<Double> utilityVsExtension = new ArrayList<>();
        List<Double> marginalDeadhead = new ArrayList<>();
        List<Double> landingScores = new ArrayList<>();
        List<Double> postDropScores = new ArrayList<>();
        List<Double> expectedEmptyKm = new ArrayList<>();
        int sampleCount = 0;
        int singleComparatorCount = 0;
        int extensionComparatorCount = 0;
        int inflationCount = 0;

        for (List<DispatchFactSink.CandidateFact> contextCandidates : byContext.values()) {
            DispatchFactSink.CandidateFact selected = contextCandidates.stream()
                    .filter(DispatchFactSink.CandidateFact::selected)
                    .filter(BatchIntelligenceCertificationRunner::isMainlineBatch2)
                    .max(Comparator.comparingDouble(DispatchFactSink.CandidateFact::predictedUtility))
                    .orElse(null);
            if (selected == null) {
                continue;
            }
            sampleCount++;
            DispatchFactSink.CandidateFact bestSingle = contextCandidates.stream()
                    .filter(candidate -> !candidate.traceId().equals(selected.traceId()))
                    .filter(BatchIntelligenceCertificationRunner::isLocalSingleComparator)
                    .max(Comparator.comparingDouble(DispatchFactSink.CandidateFact::predictedUtility))
                    .orElse(null);
            DispatchFactSink.CandidateFact bestExtension = contextCandidates.stream()
                    .filter(candidate -> !candidate.traceId().equals(selected.traceId()))
                    .filter(BatchIntelligenceCertificationRunner::isLocalExtensionComparator)
                    .max(Comparator.comparingDouble(DispatchFactSink.CandidateFact::predictedUtility))
                    .orElse(null);
            if (bestSingle != null) {
                singleComparatorCount++;
                double delta = selected.predictedUtility() - bestSingle.predictedUtility();
                utilityVsSingle.add(delta);
                if (delta < 0.0) {
                    inflationCount++;
                }
            }
            if (bestExtension != null) {
                extensionComparatorCount++;
                utilityVsExtension.add(selected.predictedUtility() - bestExtension.predictedUtility());
            }

            DispatchFactSink.DecisionFact decision = decisionsByTrace.get(selected.traceId());
            marginalDeadhead.add(decision == null ? metric(selected.semanticPlanSummary(), "marginalDeadheadPerAddedOrder")
                    : decision.marginalDeadheadPerAddedOrder());
            landingScores.add(metric(selected.semanticPlanSummary(), "lastDropLandingScore"));
            postDropScores.add(metric(selected.semanticPlanSummary(), "postDropDemandProbability"));
            expectedEmptyKm.add(metric(selected.semanticPlanSummary(), "expectedPostCompletionEmptyKm"));
        }

        List<String> notes = new ArrayList<>();
        if (sampleCount == 0) {
            notes.add("missing selected batch-2 candidate contexts");
        }
        if (singleComparatorCount == 0) {
            notes.add("missing local single comparators for batch-2 contexts");
        }
        double utilityVsSingleMean = BenchmarkCertificationSupport.average(utilityVsSingle);
        double utilityVsExtensionMean = BenchmarkCertificationSupport.average(utilityVsExtension);
        double marginalDeadheadMean = BenchmarkCertificationSupport.average(marginalDeadhead);
        double landingMean = BenchmarkCertificationSupport.average(landingScores);
        double postDropMean = BenchmarkCertificationSupport.average(postDropScores);
        double expectedEmptyKmMean = BenchmarkCertificationSupport.average(expectedEmptyKm);
        double inflationRate = sampleCount <= 0 ? 100.0 : (inflationCount * 100.0) / sampleCount;
        boolean overallPass = sampleCount > 0
                && singleComparatorCount > 0
                && utilityVsSingleMean >= 0.0
                && (extensionComparatorCount == 0 || utilityVsExtensionMean >= -0.05)
                && marginalDeadheadMean <= 2.5
                && landingMean >= 0.55
                && postDropMean >= 0.50
                && expectedEmptyKmMean <= 2.2
                && inflationRate <= 20.0;
        if (utilityVsSingleMean < 0.0) {
            notes.add("selected batch-2 utility is losing to best local single on average");
        }
        if (extensionComparatorCount > 0 && utilityVsExtensionMean < -0.05) {
            notes.add("selected batch-2 is losing to alternative local extension candidates");
        }
        if (marginalDeadheadMean > 2.5) {
            notes.add("marginal deadhead per added order is above guardrail");
        }
        if (landingMean < 0.55) {
            notes.add("last-drop landing quality is below guardrail");
        }
        if (postDropMean < 0.50) {
            notes.add("post-drop demand probability is below guardrail");
        }
        if (expectedEmptyKmMean > 2.2) {
            notes.add("expected post-completion empty km is above guardrail");
        }
        if (inflationRate > 20.0) {
            notes.add("batch inflation rate is above guardrail");
        }

        BatchIntelligenceCertificationSummary summary = new BatchIntelligenceCertificationSummary(
                BenchmarkSchema.VERSION,
                "batch-intelligence-certification-" + laneName,
                Instant.now(),
                BenchmarkCertificationSupport.resolveGitRevision(),
                sampleCount,
                singleComparatorCount,
                extensionComparatorCount,
                utilityVsSingleMean,
                utilityVsExtensionMean,
                marginalDeadheadMean,
                landingMean,
                postDropMean,
                expectedEmptyKmMean,
                inflationRate,
                overallPass,
                notes
        );
        BenchmarkArtifactWriter.writeBatchIntelligenceCertificationSummary(summary);
        System.out.println("[BatchIntelligence] lane=" + laneName
                + " overallPass=" + summary.overallPass()
                + " batch2Samples=" + summary.batch2SampleCount()
                + " inflationRate=" + String.format("%.1f", summary.inflationRate()));
        if (!summary.overallPass()) {
            throw new IllegalStateException("Batch intelligence certification failed for lane " + laneName);
        }
    }

    private static boolean isMainlineBatch2(DispatchFactSink.CandidateFact fact) {
        if (fact.bundleSize() != 2) {
            return false;
        }
        String bucket = normalizeBucket(fact.selectionBucket());
        return !bucket.equals("fallback_local_low_deadhead")
                && !bucket.equals("borrowed_coverage")
                && !bucket.equals("emergency_coverage")
                && !bucket.equals("hold_wait3");
    }

    private static boolean isLocalSingleComparator(DispatchFactSink.CandidateFact fact) {
        return fact.bundleSize() <= 1 && normalizeBucket(fact.selectionBucket()).equals("single_local");
    }

    private static boolean isLocalExtensionComparator(DispatchFactSink.CandidateFact fact) {
        return fact.bundleSize() == 2 && normalizeBucket(fact.selectionBucket()).equals("extension_local");
    }

    private static String normalizeBucket(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String contextKey(DispatchFactSink.CandidateFact fact) {
        return fact.runId() + "|" + fact.tick() + "|" + fact.driverId();
    }

    private static double metric(Map<String, Object> summary, String key) {
        if (summary == null) {
            return 0.0;
        }
        Object value = summary.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Double.parseDouble(text);
            } catch (NumberFormatException ignored) {
                return 0.0;
            }
        }
        return 0.0;
    }

    private static <T> List<T> readJsonl(Path path, Class<T> type) {
        try {
            if (Files.notExists(path)) {
                return List.of();
            }
            List<T> values = new ArrayList<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                values.add(GSON.fromJson(line, type));
            }
            return values;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read JSONL artifact " + path, e);
        }
    }
}
