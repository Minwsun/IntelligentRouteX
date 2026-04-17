package com.routechain.v2.route;

import com.routechain.v2.SchemaVersioned;
import com.routechain.v2.feedback.DispatchRuntimeReuseState;

public record RouteProposalReuseInput(
        String schemaVersion,
        DispatchRuntimeReuseState reuseState) implements SchemaVersioned {
}
