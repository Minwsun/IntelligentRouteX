package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ReuseStateWriteResult(
        String schemaVersion,
        String reuseStateId,
        boolean written,
        ReuseStateManifest manifest,
        DispatchRuntimeReuseState reuseState,
        List<String> degradeReasons) implements SchemaVersioned {
}
