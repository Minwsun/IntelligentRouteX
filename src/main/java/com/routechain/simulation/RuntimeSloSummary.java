package com.routechain.simulation;

import java.util.List;

/**
 * Runtime SLO summary for one benchmark profile.
 */
public record RuntimeSloSummary(
        String profileName,
        double dispatchP95Ms,
        double dispatchP99Ms,
        boolean dispatchP95Pass,
        boolean dispatchP99Pass,
        boolean measurementValid,
        boolean neuralPriorSafe,
        List<String> warnings
) {
    public RuntimeSloSummary {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
