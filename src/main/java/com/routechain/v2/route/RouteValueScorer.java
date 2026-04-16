package com.routechain.v2.route;

import com.routechain.domain.Region;
import com.routechain.v2.DispatchV2PlanCandidate;
import com.routechain.v2.GlobalValue;

import java.util.List;
import java.util.Set;

public final class RouteValueScorer {

    public DispatchV2PlanCandidate score(RouteProposal proposal,
                                         List<Region> regions,
                                         Set<String> incumbentSignatures) {
        double routeModelValue = clamp01(
                0.40 * proposal.plan().getRouteValueScore()
                        + 0.30 * proposal.plan().getBundleEfficiency()
                        + 0.30 * proposal.plan().getOnTimeProbability());
        double zoneBalanceValue = zoneBalanceValue(proposal.plan().getEndZonePoint(), regions);
        String signature = signature(proposal);
        double incumbentBonus = incumbentSignatures.contains(signature) ? 1.0 : 0.0;
        GlobalValue globalValue = new GlobalValue(
                routeModelValue,
                proposal.robustUtility().worstCaseValue(),
                proposal.plan().getLastDropLandingScore(),
                zoneBalanceValue,
                incumbentBonus,
                clamp01(
                        0.50 * routeModelValue
                                + 0.20 * proposal.robustUtility().worstCaseValue()
                                + 0.15 * proposal.plan().getLastDropLandingScore()
                                + 0.10 * zoneBalanceValue
                                + 0.05 * incumbentBonus));
        proposal.plan().setGlobalValue(globalValue.totalValue());
        return new DispatchV2PlanCandidate(
                proposal.routeProposalId(),
                proposal.plan(),
                null,
                proposal.robustUtility(),
                globalValue,
                "selected " + proposal.plan().getBundle().bundleId()
                        + " via dispatch-v2 " + proposal.source()
                        + " for driver " + proposal.plan().getDriver().getId());
    }

    public String signature(RouteProposal proposal) {
        return proposal.plan().getDriver().getId() + "|" + proposal.plan().getOrders().stream()
                .map(com.routechain.domain.Order::getId)
                .sorted()
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private double zoneBalanceValue(com.routechain.domain.GeoPoint endZone, List<Region> regions) {
        if (regions == null || regions.isEmpty() || endZone == null) {
            return 0.5;
        }
        return regions.stream()
                .mapToDouble(region -> {
                    double proximity = 1.0 - Math.min(1.0, region.getCenter().distanceTo(endZone) / 5_000.0);
                    double demand = Math.max(region.getPredictedDemand5m(), region.getCurrentDemandPressure());
                    double supply = Math.max(0.1, region.getCurrentDriverSupply());
                    double balance = clamp01(demand / (demand + supply));
                    return 0.55 * proximity + 0.45 * balance;
                })
                .max()
                .orElse(0.5);
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
