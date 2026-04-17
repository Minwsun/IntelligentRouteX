package com.routechain.v2.selector;

import java.util.List;

record SelectorCandidateBuildResult(
        List<SelectorCandidateEnvelope> candidateEnvelopes,
        List<String> degradeReasons,
        SelectorDecisionTrace decisionTrace) {
}
