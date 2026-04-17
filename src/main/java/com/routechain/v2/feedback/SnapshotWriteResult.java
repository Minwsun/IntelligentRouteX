package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record SnapshotWriteResult(
        String schemaVersion,
        String snapshotId,
        boolean written,
        SnapshotManifest manifest,
        DispatchRuntimeSnapshot snapshot,
        List<String> degradeReasons) implements SchemaVersioned {
}
