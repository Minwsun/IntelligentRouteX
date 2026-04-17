package com.routechain.v2.selector;

import com.routechain.v2.SchemaVersioned;

import java.util.List;

public record SelectedProposal(
        String schemaVersion,
        String proposalId,
        int selectionRank,
        double selectionScore,
        List<String> reasons) implements SchemaVersioned {
}
