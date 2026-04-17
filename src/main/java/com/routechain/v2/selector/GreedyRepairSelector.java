package com.routechain.v2.selector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GreedyRepairSelector {

    public SelectorSelectionOutcome select(List<SelectorCandidateEnvelope> candidateEnvelopes,
                                           ConflictGraph conflictGraph,
                                           SelectionSolverMode solverMode,
                                           boolean repairEnabled) {
        Map<String, Set<String>> adjacency = adjacency(conflictGraph);
        List<SelectorCandidateEnvelope> rankedCandidates = candidateEnvelopes.stream()
                .filter(envelope -> envelope.candidate().feasible())
                .sorted(SelectorCandidateRanking.comparator())
                .toList();

        List<SelectorCandidateEnvelope> selected = new ArrayList<>();
        List<SelectorCandidateEnvelope> skippedByConflict = new ArrayList<>();
        Map<String, List<String>> selectionReasons = new HashMap<>();
        List<SelectorTraceEvent> conflictFilteredCandidates = new ArrayList<>();
        List<SelectorRepairSwap> repairSwapReplacements = new ArrayList<>();

        for (SelectorCandidateEnvelope candidateEnvelope : rankedCandidates) {
            List<String> conflictingSelected = conflictsWithSelected(candidateEnvelope, selected, adjacency);
            if (conflictingSelected.isEmpty()) {
                selected.add(candidateEnvelope);
                selectionReasons.put(candidateEnvelope.candidate().proposalId(), List.of("selected-by-greedy-pass"));
                continue;
            }
            skippedByConflict.add(candidateEnvelope);
            conflictFilteredCandidates.add(new SelectorTraceEvent(
                    candidateEnvelope.candidate().proposalId(),
                    "conflicts-with=" + String.join(",", conflictingSelected)));
        }

        if (repairEnabled) {
            for (SelectorCandidateEnvelope candidateEnvelope : skippedByConflict) {
                List<SelectorCandidateEnvelope> conflictingSelected = selected.stream()
                        .filter(selectedEnvelope -> conflicts(candidateEnvelope.candidate().proposalId(), selectedEnvelope.candidate().proposalId(), adjacency))
                        .toList();
                if (conflictingSelected.size() != 1) {
                    continue;
                }
                SelectorCandidateEnvelope displaced = conflictingSelected.get(0);
                if (candidateEnvelope.candidate().selectionScore() <= displaced.candidate().selectionScore()) {
                    continue;
                }
                boolean conflictsAfterSwap = selected.stream()
                        .filter(selectedEnvelope -> !selectedEnvelope.candidate().proposalId().equals(displaced.candidate().proposalId()))
                        .anyMatch(selectedEnvelope -> conflicts(candidateEnvelope.candidate().proposalId(), selectedEnvelope.candidate().proposalId(), adjacency));
                if (conflictsAfterSwap) {
                    continue;
                }
                selected.removeIf(selectedEnvelope -> selectedEnvelope.candidate().proposalId().equals(displaced.candidate().proposalId()));
                selected.add(candidateEnvelope);
                selectionReasons.remove(displaced.candidate().proposalId());
                selectionReasons.put(candidateEnvelope.candidate().proposalId(), List.of("selected-by-repair-swap"));
                repairSwapReplacements.add(new SelectorRepairSwap(
                        displaced.candidate().proposalId(),
                        candidateEnvelope.candidate().proposalId(),
                        candidateEnvelope.candidate().selectionScore() - displaced.candidate().selectionScore()));
            }
        }

        List<SelectorCandidateEnvelope> rankedSelection = selected.stream()
                .sorted(SelectorCandidateRanking.comparator())
                .toList();
        List<SelectedProposal> selectedProposals = SelectorCandidateRanking.toSelectedProposals(
                rankedSelection,
                proposalId -> selectionReasons.getOrDefault(proposalId, List.of("selected")));
        double objectiveValue = SelectorCandidateRanking.objectiveValue(rankedSelection);
        return new SelectorSelectionOutcome(
                new GlobalSelectionResult(
                        "global-selection-result/v1",
                        List.copyOf(selectedProposals),
                        candidateEnvelopes.size(),
                        selectedProposals.size(),
                        solverMode,
                        objectiveValue,
                        List.of()),
                new SelectorDecisionTrace(List.of(), List.copyOf(conflictFilteredCandidates), List.copyOf(repairSwapReplacements)));
    }

    private List<String> conflictsWithSelected(SelectorCandidateEnvelope candidateEnvelope,
                                               List<SelectorCandidateEnvelope> selected,
                                               Map<String, Set<String>> adjacency) {
        return selected.stream()
                .map(SelectorCandidateEnvelope::candidate)
                .map(SelectorCandidate::proposalId)
                .filter(selectedProposalId -> conflicts(candidateEnvelope.candidate().proposalId(), selectedProposalId, adjacency))
                .sorted()
                .toList();
    }

    private boolean conflicts(String leftProposalId, String rightProposalId, Map<String, Set<String>> adjacency) {
        return adjacency.getOrDefault(leftProposalId, Set.of()).contains(rightProposalId);
    }

    private Map<String, Set<String>> adjacency(ConflictGraph conflictGraph) {
        Map<String, Set<String>> adjacency = new HashMap<>();
        for (ConflictEdge edge : conflictGraph.edges()) {
            adjacency.computeIfAbsent(edge.leftProposalId(), ignored -> new HashSet<>()).add(edge.rightProposalId());
            adjacency.computeIfAbsent(edge.rightProposalId(), ignored -> new HashSet<>()).add(edge.leftProposalId());
        }
        return adjacency;
    }
}
