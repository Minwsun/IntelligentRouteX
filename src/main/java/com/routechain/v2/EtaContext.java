package com.routechain.v2;

public record EtaContext(
        String schemaVersion,
        double etaMinutes,
        double etaUncertainty,
        boolean trafficBadSignal,
        boolean weatherBadSignal,
        String corridorId,
        String refineSource) implements SchemaVersioned {
}

