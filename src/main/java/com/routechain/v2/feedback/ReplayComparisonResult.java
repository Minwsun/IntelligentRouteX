package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record ReplayComparisonResult(
        String schemaVersion,
        boolean matched,
        List<String> mismatchReasons) implements SchemaVersioned {
}
