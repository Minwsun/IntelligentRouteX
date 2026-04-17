package com.routechain.v2.selector;

import java.util.List;
import java.util.Optional;

record SelectorSolverResult(
        Optional<GlobalSelectionResult> selectionResult,
        List<String> degradeReasons) {
}
