package com.routechain.api.controller;

import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.OpsArtifactService;
import com.routechain.api.service.RouteIntelligenceLiveCaseService;
import com.routechain.api.service.RouteIntelligenceProofService;
import com.routechain.infra.AdminQueryService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/ops")
public class OpsController {
    private final OpsArtifactService opsArtifactService;
    private final RouteIntelligenceLiveCaseService routeIntelligenceLiveCaseService;
    private final RouteIntelligenceProofService routeIntelligenceProofService;
    private final ActorAccessGuard actorAccessGuard;

    public OpsController(OpsArtifactService opsArtifactService,
                         RouteIntelligenceLiveCaseService routeIntelligenceLiveCaseService,
                         RouteIntelligenceProofService routeIntelligenceProofService,
                         ActorAccessGuard actorAccessGuard) {
        this.opsArtifactService = opsArtifactService;
        this.routeIntelligenceLiveCaseService = routeIntelligenceLiveCaseService;
        this.routeIntelligenceProofService = routeIntelligenceProofService;
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

    @GetMapping("/proof/cases")
    public List<?> proofCases() {
        actorAccessGuard.requireOps();
        return routeIntelligenceProofService.listProofCases();
    }

    @PostMapping("/proof/cases/{caseId}/run")
    public Object runProofCase(@PathVariable String caseId,
                               @RequestParam(defaultValue = "shadow") String mode) {
        actorAccessGuard.requireOps();
        return routeIntelligenceProofService.runProofCase(caseId, mode);
    }

    @GetMapping("/proof/cases/{caseId}/compare")
    public Map<String, Object> compareProofCase(@PathVariable String caseId,
                                                @RequestParam(defaultValue = "shadow") String mode,
                                                @RequestParam(required = false) List<String> policies) {
        actorAccessGuard.requireOps();
        return routeIntelligenceProofService.comparePolicies(caseId, mode, policies);
    }

    @GetMapping("/proof/control-surface")
    public Map<String, Object> proofControlSurface() {
        actorAccessGuard.requireOps();
        return routeIntelligenceProofService.controlSurface();
    }

    @GetMapping("/proof/live-case")
    public Object buildSelectedLiveCase(@RequestParam(required = false) String pickupCellId,
                                        @RequestParam(required = false) String driverId,
                                        @RequestParam(defaultValue = "shadow") String mode) {
        actorAccessGuard.requireOps();
        return routeIntelligenceLiveCaseService.buildSelectedLiveCase(pickupCellId, driverId, mode);
    }

    @GetMapping(value = "/proof/shell", produces = MediaType.TEXT_HTML_VALUE)
    public String proofShell() {
        actorAccessGuard.requireOps();
        return routeIntelligenceProofService.proofShellHtml();
    }
}
