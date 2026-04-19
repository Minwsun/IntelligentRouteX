package com.routechain.v2.harvest.reconcile;

import com.routechain.v2.harvest.contracts.DispatchOutcomeIngestRecord;
import com.routechain.v2.harvest.writers.HarvestWriter;

import java.util.List;

public final class DispatchOutcomeReconciler {
    private final HarvestWriter harvestWriter;

    public DispatchOutcomeReconciler(HarvestWriter harvestWriter) {
        this.harvestWriter = harvestWriter;
    }

    public void reconcile(List<DispatchOutcomeIngestRecord> records) {
        for (DispatchOutcomeIngestRecord record : records) {
            harvestWriter.writeOutcome(record);
        }
    }
}
