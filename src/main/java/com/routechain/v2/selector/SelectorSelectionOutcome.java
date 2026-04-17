package com.routechain.v2.selector;

record SelectorSelectionOutcome(
        GlobalSelectionResult selectionResult,
        SelectorDecisionTrace decisionTrace) {
}
