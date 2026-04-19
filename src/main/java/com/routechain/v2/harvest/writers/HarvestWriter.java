package com.routechain.v2.harvest.writers;

import com.routechain.v2.harvest.contracts.BronzeRecord;
import com.routechain.v2.harvest.contracts.DispatchOutcomeIngestRecord;

public interface HarvestWriter {
    void write(BronzeRecord record);

    void writeOutcome(DispatchOutcomeIngestRecord record);
}
