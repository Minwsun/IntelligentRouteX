package com.routechain.api.controller;

import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.OpsArtifactService;
import com.routechain.infra.AdminQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/ops")
public class OpsController {
    private final OpsArtifactService opsArtifactService;
    private final ActorAccessGuard actorAccessGuard;

    public OpsController(OpsArtifactService opsArtifactService,
                         ActorAccessGuard actorAccessGuard) {
        this.opsArtifactService = opsArtifactService;
        this.actorAccessGuard = actorAccessGuard;
    }

    @GetMapping("/control-room/frame/latest")
    public Map<String, Object> latestFrame() {
        actorAccessGuard.requireOps();
        AdminQueryService.SystemAdminSnapshot snapshot = opsArtifactService.adminSnapshot();
        return Map.of(
                "adminSnapshot", snapshot,
                "controlRoomMarkdown", opsArtifactService.latestControlRoomMarkdown()
        );
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> run(@PathVariable String runId) {
        actorAccessGuard.requireOps();
        AdminQueryService.SystemAdminSnapshot snapshot = opsArtifactService.adminSnapshot();
        return Map.of(
                "requestedRunId", runId,
                "lastKnownRunId", snapshot.lastRunId(),
                "lastScenarioName", snapshot.lastScenarioName(),
                "lastReplayVerdict", snapshot.lastReplayVerdict()
        );
    }

    @GetMapping("/policy-arena/compare")
    public Map<String, Object> policyArena() {
        actorAccessGuard.requireOps();
        AdminQueryService.SystemAdminSnapshot snapshot = opsArtifactService.adminSnapshot();
        return Map.of(
                "lastReplayVerdict", snapshot.lastReplayVerdict(),
                "activeAlerts", snapshot.activeAlerts(),
                "topicVolumes", snapshot.topicVolumes()
        );
    }

    @GetMapping("/heatmap/h3")
    public List<Map<String, String>> heatmap(@RequestParam(defaultValue = "12") int limit) {
        actorAccessGuard.requireOps();
        return opsArtifactService.cityTwinRows(Math.max(1, Math.min(50, limit)));
    }

    @GetMapping("/modelops/promotions")
    public Map<String, Object> promotions() {
        actorAccessGuard.requireOps();
        return Map.of(
                "adminSnapshot", opsArtifactService.adminSnapshot(),
                "marketplaceEdges", opsArtifactService.marketplaceEdges(8)
        );
    }

    @GetMapping("/compact/runtime")
    public Map<String, Object> compactRuntime() {
        actorAccessGuard.requireOps();
        return Map.of(
                "mode", opsArtifactService.liveDispatchMode().name(),
                "compactStatus", opsArtifactService.compactRuntimeStatus(),
                "compactEvidence", opsArtifactService.compactEvidence()
        );
    }
}
