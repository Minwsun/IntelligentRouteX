package com.routechain.v2.feedback;

import com.routechain.v2.SchemaVersioned;

public record DecisionLogRecord(
        String schemaVersion,
        String traceId) implements SchemaVersioned {
}
