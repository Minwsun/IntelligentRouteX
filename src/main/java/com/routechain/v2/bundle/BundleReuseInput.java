package com.routechain.v2.bundle;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.feedback.DispatchRuntimeReuseState;

public record BundleReuseInput(
        String schemaVersion,
        DispatchRuntimeReuseState reuseState) implements SchemaVersioned {
}
