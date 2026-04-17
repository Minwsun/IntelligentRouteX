package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ReuseStateLoadResult(
        String schemaVersion,
        boolean loaded,
        ReuseStateManifest manifest,
        DispatchRuntimeReuseState reuseState,
        List<String> degradeReasons) implements SchemaVersioned {
}
