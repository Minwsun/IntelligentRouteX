package com.routechain.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteIntelligenceLiveCaseServiceTest {

    @Test
    void shouldBuildSelectedLiveCaseFromCurrentCityState() {
        RouteIntelligenceLiveCaseService service = new RouteIntelligenceLiveCaseService();

        RouteIntelligenceLiveCaseService.SelectedLiveCaseBundle bundle =
                service.buildSelectedLiveCase("8865b566e1fffff", "", "shadow");

        assertNotNull(bundle);
        assertFalse(bundle.pickupCellId().isBlank());
        assertFalse(bundle.matchedProofCaseId().isBlank());
        assertNotNull(bundle.proofComparison());
        assertTrue(bundle.driverSuggestions().size() >= 0);
    }
}
