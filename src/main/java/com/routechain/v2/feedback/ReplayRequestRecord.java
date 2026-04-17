package com.routechain.v2.feedback;

import com.routechain.v2.DispatchV2Request;
import com.routechain.v2.SchemaVersioned;

public record ReplayRequestRecord(
        String schemaVersion,
        String traceId,
        DispatchV2Request request) implements SchemaVersioned {
}
