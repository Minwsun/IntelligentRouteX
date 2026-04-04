package com.routechain.simulation;

import java.util.List;

/**
 * One top-level certification gate result.
 */
public record CertificationGateResult(
        String gateName,
        boolean pass,
        List<String> notes
) {
    public CertificationGateResult {
        gateName = gateName == null || gateName.isBlank() ? "unknown" : gateName;
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
