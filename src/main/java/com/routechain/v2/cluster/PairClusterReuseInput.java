package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.feedback.DispatchRuntimeReuseState;

public record PairClusterReuseInput(
        String schemaVersion,
        DispatchRuntimeReuseState reuseState) implements SchemaVersioned {
}
