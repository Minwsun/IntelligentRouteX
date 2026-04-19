package com.routechain.v2.harvest.writers;

import com.routechain.v2.harvest.contracts.BronzeRecord;
import com.routechain.v2.harvest.contracts.DispatchOutcomeIngestRecord;

public final class NoOpHarvestWriter implements HarvestWriter {
    @Override
    public void write(BronzeRecord record) {
    }

    @Override
    public void writeOutcome(DispatchOutcomeIngestRecord record) {
    }
}
