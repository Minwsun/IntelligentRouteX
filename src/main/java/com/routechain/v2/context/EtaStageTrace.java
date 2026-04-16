package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record EtaStageTrace(
        String schemaVersion,
        double baselineMinutes,
        double trafficMultiplier,
        double weatherMultiplier,
        boolean liveRefineApplied,
        boolean mlResidualApplied,
        double uncertainty,
        List<String> degradeReasons) implements SchemaVersioned {

    public static EtaStageTrace empty() {
        return new EtaStageTrace(
                "eta-stage-trace/v1",
                0.0,
                1.0,
                1.0,
                false,
                false,
                0.0,
                List.of());
    }
}

