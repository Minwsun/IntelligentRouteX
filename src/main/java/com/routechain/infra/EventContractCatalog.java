package com.routechain.infra;

import java.util.Map;

/**
 * Canonical event contract identifiers for RouteChain production-small data plane.
 */
public final class EventContractCatalog {
    public static final String DISPATCH_CANDIDATE_V1 = "dispatch.candidate.v1";
    public static final String DISPATCH_DECISION_V2 = "dispatch.decision.v2";
    public static final String DISPATCH_OUTCOME_V2 = "dispatch.outcome.v2";
    public static final String FEATURE_SNAPSHOT_V2 = "feature.snapshot.v2";
    public static final String ZONE_LIVE_STATE_V1 = "zone.live_state.v1";
    public static final String CORRIDOR_LIVE_STATE_V1 = "corridor.live_state.v1";
    public static final String OPPORTUNITY_MAP_V1 = "opportunity.map.v1";
    public static final String MODEL_INFERENCE_V1 = "model.inference.v1";
    public static final String BENCHMARK_MANIFEST_V2 = "benchmark.manifest.v2";

    private static final Map<String, String> SCHEMA_BY_TOPIC = Map.of(
            DISPATCH_CANDIDATE_V1, "v1",
            DISPATCH_DECISION_V2, "v2",
            DISPATCH_OUTCOME_V2, "v2",
            FEATURE_SNAPSHOT_V2, "v2",
            ZONE_LIVE_STATE_V1, "v1",
            CORRIDOR_LIVE_STATE_V1, "v1",
            OPPORTUNITY_MAP_V1, "v1",
            MODEL_INFERENCE_V1, "v1",
            BENCHMARK_MANIFEST_V2, "v2"
    );

    private EventContractCatalog() {}

    public static String schemaVersionForTopic(String topic) {
        if (topic == null || topic.isBlank()) {
            return "v0";
        }
        return SCHEMA_BY_TOPIC.getOrDefault(topic, "v0");
    }
}
