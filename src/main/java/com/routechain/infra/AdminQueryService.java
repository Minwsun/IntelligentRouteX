package com.routechain.infra;

import com.routechain.ai.AgentToolDescriptor;
import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Minimal control-plane query boundary for operator/admin views.
 */
public interface AdminQueryService {
    void registerDispatchBrain(String agentId, List<AgentToolDescriptor> tools);
    void updateExecutionProfile(String executionProfile);
    void updateLlmMode(String llmMode);
    void updateLlmRuntime(LlmRuntimeStatus llmRuntimeStatus);
    void onCanonicalEvent(String topic);
    void onAlert(String alertId, String title, String regionId, Instant timestamp);
    void onRunReport(RunReport report);
    void onReplayCompare(ReplayCompareResult compare);
    SystemAdminSnapshot snapshot();

    record AlertSummary(String id, String title, String regionId, Instant timestamp) {}

    record LlmRuntimeStatus(
            String provider,
            String mode,
            String selectedModel,
            String routingClass,
            String fallbackReason,
            long totalRequests,
            long onlineRequests,
            long offlineFallbackCount,
            long quotaRejectCount,
            long schemaRejectCount,
            long timeoutCount,
            double offlineFallbackRate,
            double p50LatencyMs,
            double p95LatencyMs,
            boolean circuitOpen
    ) {
        public static LlmRuntimeStatus offlineDefault(String mode) {
            return new LlmRuntimeStatus(
                    "offline",
                    mode == null || mode.isBlank() ? "OFFLINE" : mode,
                    "offline-shadow",
                    "",
                    "",
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0.0,
                    0.0,
                    0.0,
                    false
            );
        }
    }

    record SystemAdminSnapshot(
            Instant updatedAt,
            String dispatchBrainId,
            List<AgentToolDescriptor> tools,
            Map<String, Long> topicVolumes,
            List<AlertSummary> activeAlerts,
            String executionProfile,
            String llmMode,
            LlmRuntimeStatus llmRuntimeStatus,
            String lastRunId,
            String lastScenarioName,
            String lastReplayVerdict
    ) {}
}
