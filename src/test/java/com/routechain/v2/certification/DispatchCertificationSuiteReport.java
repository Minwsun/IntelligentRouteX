package com.routechain.v2.certification;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record DispatchCertificationSuiteReport(
        String schemaVersion,
        String suiteName,
        int scenarioCount,
        int passedScenarioCount,
        int failedScenarioCount,
        List<DispatchCertificationScenarioReport> scenarioReports,
        List<String> failureSummaries) implements SchemaVersioned {
}
