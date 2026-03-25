package com.routechain.infra;

import com.routechain.ai.AgentToolDescriptor;
import com.routechain.simulation.ReplayCompareResult;
import com.routechain.simulation.RunReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory control-plane store used by the local operator console.
 */
public final class LocalAdminQueryService implements AdminQueryService {
    private final Map<String, Long> topicVolumes = new ConcurrentHashMap<>();
    private final List<AlertSummary> activeAlerts = new ArrayList<>();
    private volatile Instant updatedAt = Instant.EPOCH;
    private volatile String dispatchBrainId = "unregistered";
    private volatile List<AgentToolDescriptor> tools = List.of();
    private volatile String executionProfile = "MAINLINE_REALISTIC";
    private volatile String llmMode = "OFF";
    private volatile LlmRuntimeStatus llmRuntimeStatus = LlmRuntimeStatus.offlineDefault("OFFLINE");
    private volatile String lastRunId = "";
    private volatile String lastScenarioName = "";
    private volatile String lastReplayVerdict = "";

    @Override
    public synchronized void registerDispatchBrain(String agentId, List<AgentToolDescriptor> tools) {
        this.dispatchBrainId = agentId;
        this.tools = List.copyOf(tools);
        this.updatedAt = Instant.now();
    }

    @Override
    public synchronized void updateExecutionProfile(String executionProfile) {
        this.executionProfile = executionProfile == null ? "MAINLINE_REALISTIC" : executionProfile;
        this.updatedAt = Instant.now();
    }

    @Override
    public synchronized void updateLlmMode(String llmMode) {
        this.llmMode = llmMode == null ? "OFF" : llmMode;
        this.updatedAt = Instant.now();
    }

    @Override
    public synchronized void updateLlmRuntime(LlmRuntimeStatus llmRuntimeStatus) {
        this.llmRuntimeStatus = llmRuntimeStatus == null
                ? LlmRuntimeStatus.offlineDefault(llmMode)
                : llmRuntimeStatus;
        this.updatedAt = Instant.now();
    }

    @Override
    public void onCanonicalEvent(String topic) {
        topicVolumes.merge(topic, 1L, Long::sum);
        updatedAt = Instant.now();
    }

    @Override
    public synchronized void onAlert(String alertId, String title, String regionId, Instant timestamp) {
        activeAlerts.add(new AlertSummary(alertId, title, regionId, timestamp));
        if (activeAlerts.size() > 25) {
            activeAlerts.remove(0);
        }
        updatedAt = Instant.now();
    }

    @Override
    public synchronized void onRunReport(RunReport report) {
        lastRunId = report.runId();
        lastScenarioName = report.scenarioName();
        updatedAt = Instant.now();
    }

    @Override
    public synchronized void onReplayCompare(ReplayCompareResult compare) {
        lastReplayVerdict = compare.verdict();
        updatedAt = Instant.now();
    }

    @Override
    public synchronized SystemAdminSnapshot snapshot() {
        return new SystemAdminSnapshot(
                updatedAt,
                dispatchBrainId,
                List.copyOf(tools),
                Map.copyOf(new LinkedHashMap<>(topicVolumes)),
                List.copyOf(activeAlerts),
                executionProfile,
                llmMode,
                llmRuntimeStatus,
                lastRunId,
                lastScenarioName,
                lastReplayVerdict
        );
    }
}
