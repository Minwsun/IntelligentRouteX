package com.routechain.v2.cluster;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record PairGateDecision(
        String schemaVersion,
        boolean passed,
        List<String> reasons) implements SchemaVersioned {
}

