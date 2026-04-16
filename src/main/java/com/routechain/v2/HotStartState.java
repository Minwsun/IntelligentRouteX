package com.routechain.v2;

public record HotStartState(
        String schemaVersion,
        boolean reusable) implements SchemaVersioned {
}
