package com.routechain.v2;

public record EtaContext(
        String schemaVersion,
        String traceId,
        int sampledLegCount,
        double averageEtaMinutes,
        double maxEtaMinutes,
        double averageUncertainty,
        boolean trafficBadSignal,
        boolean weatherBadSignal,
        String corridorId,
        String refineSource) implements SchemaVersioned {

    public static EtaContext empty(String traceId) {
        return new EtaContext(
                "dispatch-eta-context/v1",
                traceId,
                0,
                0.0,
                0.0,
                0.0,
                false,
                false,
                "unknown",
                "none");
    }
}

