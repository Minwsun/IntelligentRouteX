package com.routechain.v2.cluster;

import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PairCompatibility(
        String schemaVersion,
        String leftOrderId,
        String rightOrderId,
        double score,
        boolean hardGatePassed,
        List<MlStageMetadata> mlStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
