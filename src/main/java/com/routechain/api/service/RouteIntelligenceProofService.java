package com.routechain.api.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.routechain.ai.BanditPosteriorSnapshot;
import com.routechain.infra.GsonSupport;
import com.routechain.infra.PlatformRuntimeBootstrap;
import com.routechain.simulation.RouteIntelligenceDemoProofRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RouteIntelligenceProofService {
    private static final Gson GSON = GsonSupport.pretty();
    private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() { }.getType();
    private static final Path CERTIFICATION_ROOT = Path.of("build", "routechain-apex", "benchmarks", "certification");
    private static final Path CONTROL_ROOM_JSON =
            Path.of("build", "routechain-apex", "benchmarks", "control-room", "control_room_latest.json");
    private static final Path DEMO_PROOF_JSON = CERTIFICATION_ROOT.resolve("route-intelligence-demo-proof-demo.json");

    public List<RouteIntelligenceDemoProofRunner.ProofCaseDescriptor> listProofCases() {
        return RouteIntelligenceDemoProofRunner.listProofCases();
    }

    public RouteIntelligenceDemoProofRunner.ProofCaseExecution runProofCase(String caseId, String mode) {
        return RouteIntelligenceDemoProofRunner.runProofCase(caseId, mode);
    }

    public Map<String, Object> comparePolicies(String caseId, String mode, List<String> requestedPolicies) {
        RouteIntelligenceDemoProofRunner.ProofCaseExecution execution =
                RouteIntelligenceDemoProofRunner.runProofCase(caseId, mode);
        List<String> policyFilter = normalizePolicies(requestedPolicies);
        List<RouteIntelligenceDemoProofRunner.PolicyRunSummary> filteredPolicies = execution.policies().stream()
                .filter(policy -> policyFilter.isEmpty() || policyFilter.contains(policy.policyKey()))
                .toList();
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("caseId", execution.caseId());
        response.put("mode", execution.mode());
        response.put("regimeName", execution.regimeName());
        response.put("scenarioName", execution.scenarioName());
        response.put("adaptiveVsLegacy", execution.adaptiveVsLegacy());
        response.put("adaptiveVsStatic", execution.adaptiveVsStatic());
        response.put("oracleSubset", execution.oracleSubset());
        response.put("adaptiveLearning", execution.adaptiveLearning());
        response.put("policies", filteredPolicies);
        response.put("explanationSummary", execution.explanationSummary());
        response.put("notes", execution.notes());
        return response;
    }

    public Map<String, Object> controlSurface() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("cases", listProofCases());
        response.put("cityState", readJsonMap(CONTROL_ROOM_JSON));
        response.put("latestDemoProof", readJsonMap(DEMO_PROOF_JSON));
        response.put("adaptiveWeightSnapshot", currentBanditSnapshot());
        response.put("currentLearningDelta", latestLearningDelta());
        return response;
    }

    private Map<String, Object> readJsonMap(Path path) {
        try {
            if (Files.notExists(path)) {
                return Map.of("status", "missing", "path", path.toString());
            }
            Map<String, Object> parsed = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), MAP_TYPE);
            return parsed == null ? Map.of("status", "empty", "path", path.toString()) : parsed;
        } catch (IOException e) {
            return Map.of("status", "error", "path", path.toString(), "message", e.getMessage());
        }
    }

    private BanditPosteriorSnapshot currentBanditSnapshot() {
        BanditPosteriorSnapshot snapshot = PlatformRuntimeBootstrap.getModelArtifactProvider()
                .banditPosteriorSnapshot("route-utility-bandit");
        if (snapshot != null) {
            return snapshot;
        }
        RouteIntelligenceDemoProofRunner.ProofCaseExecution fallbackExecution =
                RouteIntelligenceDemoProofRunner.runProofCase("clear-smart-batch-win", "shadow");
        return fallbackExecution.adaptiveLearning().after();
    }

    private RouteIntelligenceDemoProofRunner.AdaptiveLearningDelta latestLearningDelta() {
        return RouteIntelligenceDemoProofRunner.runProofCase("clear-smart-batch-win", "shadow")
                .adaptiveLearning()
                .delta();
    }

    private List<String> normalizePolicies(List<String> requestedPolicies) {
        if (requestedPolicies == null || requestedPolicies.isEmpty()) {
            return List.of();
        }
        return requestedPolicies.stream()
                .filter(policy -> policy != null && !policy.isBlank())
                .map(policy -> policy.trim().toLowerCase(Locale.ROOT))
                .flatMap(policy -> splitPolicyTokens(policy).stream())
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<String> splitPolicyTokens(String raw) {
        if (!raw.contains(",")) {
            return List.of(raw);
        }
        return List.of(raw.split(",")).stream()
                .map(token -> token.trim().toLowerCase(Locale.ROOT))
                .filter(token -> !token.isBlank())
                .toList();
    }
}
