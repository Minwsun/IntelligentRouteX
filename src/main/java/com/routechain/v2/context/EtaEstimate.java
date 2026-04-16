package com.routechain.v2.context;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record EtaEstimate(
        String schemaVersion,
        String traceId,
        double etaMinutes,
        double etaUncertainty,
        double trafficMultiplier,
        double weatherMultiplier,
        boolean trafficBadSignal,
        boolean weatherBadSignal,
        String corridorId,
        String refineSource,
        long trafficSourceAgeMs,
        long weatherSourceAgeMs,
        List<String> degradeReasons) implements SchemaVersioned {
}

