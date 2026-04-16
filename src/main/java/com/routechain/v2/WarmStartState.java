package com.routechain.v2;

public record WarmStartState(
        String schemaVersion,
        boolean ready) implements SchemaVersioned {
}

