package com.routechain.v2.selector;

import java.util.List;
import java.util.stream.Stream;

record SelectorDecisionTrace(
        List<SelectorTraceEvent> missingContextSkips,
        List<SelectorTraceEvent> conflictFilteredCandidates,
        List<SelectorRepairSwap> repairSwapReplacements) {

    static SelectorDecisionTrace empty() {
        return new SelectorDecisionTrace(List.of(), List.of(), List.of());
    }

    SelectorDecisionTrace merge(SelectorDecisionTrace other) {
        return new SelectorDecisionTrace(
                Stream.concat(missingContextSkips.stream(), other.missingContextSkips.stream()).toList(),
                Stream.concat(conflictFilteredCandidates.stream(), other.conflictFilteredCandidates.stream()).toList(),
                Stream.concat(repairSwapReplacements.stream(), other.repairSwapReplacements.stream()).toList());
    }
}
