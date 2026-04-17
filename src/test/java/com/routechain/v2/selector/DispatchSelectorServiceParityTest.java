package com.routechain.v2.selector;

import com.routechain.config.RouteChainDispatchV2Properties;
import com.routechain.v2.route.RouteTestFixtures;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DispatchSelectorServiceParityTest {

    @Test
    void ortoolsPathRemainsConflictFreeAndIsNotWorseThanGreedyForSameInput() {
        RouteChainDispatchV2Properties greedyProperties = RouteChainDispatchV2Properties.defaults();
        RouteChainDispatchV2Properties ortoolsProperties = RouteChainDispatchV2Properties.defaults();
        ortoolsProperties.setSelectorOrtoolsEnabled(true);
        ortoolsProperties.getSelector().getOrtools().setTimeout(Duration.ofSeconds(2));

        DispatchSelectorStage greedyStage = RouteTestFixtures.selectorStage(greedyProperties);
        DispatchSelectorStage ortoolsStage = RouteTestFixtures.selectorStage(ortoolsProperties);

        assertConflictFree(ortoolsStage);
        assertConflictFree(greedyStage);
        assertTrue(ortoolsStage.globalSelectionResult().objectiveValue() >= greedyStage.globalSelectionResult().objectiveValue());
    }

    private void assertConflictFree(DispatchSelectorStage stage) {
        Set<String> drivers = new HashSet<>();
        Set<String> orders = new HashSet<>();
        for (SelectedProposal selectedProposal : stage.globalSelectionResult().selectedProposals()) {
            SelectorCandidate candidate = stage.selectorCandidates().stream()
                    .filter(current -> current.proposalId().equals(selectedProposal.proposalId()))
                    .findFirst()
                    .orElseThrow();
            assertTrue(drivers.add(candidate.driverId()));
            assertTrue(candidate.orderIds().stream().allMatch(orders::add));
        }
    }
}
