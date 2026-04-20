package com.routechain.v2.decision;

import java.util.List;
import java.util.Map;

public final class ContextToolRegistry {
    private static final List<String> DEFAULT_TOOLS = List.of(
            "get_bundle_details",
            "get_driver_details",
            "get_route_matrix",
            "get_route_vector_summary",
            "get_conflict_summary",
            "get_scenario_breakdown");

    public Map<String, Object> toolManifest() {
        return Map.of(
                "tools", DEFAULT_TOOLS,
                "parallelToolCalls", false);
    }
}
