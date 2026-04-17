package com.routechain.v2.context;

import com.routechain.v2.EtaContext;
import com.routechain.v2.LiveStageMetadata;
import com.routechain.v2.MlStageMetadata;
import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchEtaContextStage(
        String schemaVersion,
        EtaContext etaContext,
        EtaStageTrace etaStageTrace,
        FreshnessMetadata freshnessMetadata,
        List<MlStageMetadata> mlStageMetadata,
        List<LiveStageMetadata> liveStageMetadata,
        List<String> degradeReasons) implements SchemaVersioned {
}
