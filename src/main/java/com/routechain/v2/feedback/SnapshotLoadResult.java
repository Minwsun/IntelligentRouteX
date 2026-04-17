package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record SnapshotLoadResult(
        String schemaVersion,
        boolean loaded,
        SnapshotManifest manifest,
        DispatchRuntimeSnapshot snapshot,
        List<String> degradeReasons) implements SchemaVersioned {
}
