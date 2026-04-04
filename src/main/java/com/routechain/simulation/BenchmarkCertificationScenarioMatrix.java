package com.routechain.simulation;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fixed lane-to-scenario mapping used by smoke, certification, and nightly runs.
 */
public record BenchmarkCertificationScenarioMatrix(
        String schemaVersion,
        Map<String, LaneDefinition> lanes
) {
    public BenchmarkCertificationScenarioMatrix {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank()
                ? "v1"
                : schemaVersion;
        lanes = lanes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(lanes));
    }

    public LaneDefinition lane(String laneName) {
        if (laneName == null || laneName.isBlank()) {
            throw new IllegalArgumentException("Lane name is required");
        }
        LaneDefinition lane = lanes.get(laneName.toLowerCase());
        if (lane == null) {
            throw new IllegalArgumentException("Missing lane definition for " + laneName);
        }
        return lane;
    }

    public record LaneDefinition(
            String laneName,
            List<Long> seeds,
            List<ScenarioBucket> scenarioBuckets
    ) {
        public LaneDefinition {
            laneName = laneName == null || laneName.isBlank()
                    ? "unknown"
                    : laneName;
            seeds = seeds == null ? List.of() : List.copyOf(seeds);
            scenarioBuckets = scenarioBuckets == null ? List.of() : List.copyOf(scenarioBuckets);
        }
    }

    public record ScenarioBucket(
            String scenarioGroup,
            List<String> scenarioMatchers
    ) {
        public ScenarioBucket {
            scenarioGroup = scenarioGroup == null ? "" : scenarioGroup;
            scenarioMatchers = scenarioMatchers == null ? List.of() : List.copyOf(scenarioMatchers);
        }
    }
}
