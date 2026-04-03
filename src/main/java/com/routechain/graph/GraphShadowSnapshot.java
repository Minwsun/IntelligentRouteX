package com.routechain.graph;

import java.util.List;

/**
 * Materialized graph shadow built from the current event/state snapshot.
 */
public record GraphShadowSnapshot(
        String runId,
        String scenarioName,
        String serviceTier,
        String exportMode,
        List<GraphNodeRef> nodes,
        List<GraphAffinitySnapshot> affinities,
        List<FutureCellValue> futureCellValues
) {
    public GraphShadowSnapshot {
        runId = runId == null || runId.isBlank() ? "run-unset" : runId;
        scenarioName = scenarioName == null || scenarioName.isBlank() ? "unknown" : scenarioName;
        serviceTier = serviceTier == null || serviceTier.isBlank() ? "instant" : serviceTier;
        exportMode = exportMode == null || exportMode.isBlank() ? "in-memory-shadow" : exportMode;
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        affinities = affinities == null ? List.of() : List.copyOf(affinities);
        futureCellValues = futureCellValues == null ? List.of() : List.copyOf(futureCellValues);
    }
}
