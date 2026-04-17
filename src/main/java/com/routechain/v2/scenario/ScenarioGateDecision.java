package com.routechain.v2.scenario;

import java.util.List;

record ScenarioGateDecision(
        ScenarioType scenario,
        boolean applied,
        List<String> reasons,
        List<String> degradeReasons) {
}
