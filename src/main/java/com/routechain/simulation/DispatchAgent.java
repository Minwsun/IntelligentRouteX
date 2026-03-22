package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;

import java.util.*;

/**
 * DispatchAgent — Full 7-layer orchestrator.
 * Replaces DispatchScorer with Demand-Aware Multi-Objective Bundle Dispatch.
 *
 * Decision flow:
 * 1. State Ingestion (open orders, drivers, traffic, weather, zone demand)
 * 2. Prediction (demand forecast, zone opportunity)
 * 3. Candidate Generation (beam search bundles, top-K drivers, sequence candidates)
 * 4. Constraint Filtering (SLA, profit floor, deadhead, detour)
 * 5. Multi-Objective Scoring (12-term score)
 * 6. Assignment Optimization (greedy conflict-free matching)
 * 7. Reposition (post-delivery end-zone movement)
 */
public class DispatchAgent {

    private static final int TOP_K_DRIVERS = 8;
    private static final int MAX_SEQUENCE_CANDIDATES = 6;

    private final DemandForecastService demandForecast;
    private final ZoneOpportunityScorer opportunityScorer;
    private final AssignmentSolver assignmentSolver;
    private final RepositionAgent repositionAgent;
    private final List<Region> zones;

    private int totalBundledOrders = 0;
    private int totalDispatchCycles = 0;

    public DispatchAgent(List<Region> zones) {
        this.zones = zones;
        this.demandForecast = new DemandForecastService();
        this.opportunityScorer = new ZoneOpportunityScorer();
        this.assignmentSolver = new AssignmentSolver();
        this.repositionAgent = new RepositionAgent(opportunityScorer, zones);
    }

    /**
     * Run the full dispatch pipeline.
     * Returns list of selected plans to execute.
     */
    public DispatchResult dispatch(
            List<Order> openOrders,
            List<Driver> availableDrivers,
            List<Driver> allDrivers,
            List<Order> allActiveOrders,
            int simulatedHour,
            double trafficIntensity,
            WeatherProfile weather) {

        totalDispatchCycles++;

        if (openOrders.isEmpty() || availableDrivers.isEmpty()) {
            return new DispatchResult(List.of(), List.of());
        }

        // ── Layer 1: State Ingestion (data already passed in) ───────────

        // ── Layer 2: Prediction ─────────────────────────────────────────
        demandForecast.updateForecasts(zones, allActiveOrders, allDrivers,
                simulatedHour, trafficIntensity, weather);
        opportunityScorer.scoreAllZones(zones);

        // ── Layer 3: Candidate Generation ───────────────────────────────
        BeamBundleGenerator bundleGen = new BeamBundleGenerator(
                trafficIntensity, weather, zones);
        List<DispatchPlan.Bundle> bundles = bundleGen.generate(openOrders);

        SequenceOptimizer seqOptimizer = new SequenceOptimizer(trafficIntensity, weather);
        PlanScoreCalculator scorer = new PlanScoreCalculator(
                opportunityScorer, zones, trafficIntensity, weather);

        List<DispatchPlan> allPlans = new ArrayList<>();

        for (DispatchPlan.Bundle bundle : bundles) {
            // Select top-K closest drivers for this bundle
            GeoPoint bundlePickupCenter = computePickupCenter(bundle);
            List<Driver> candidateDrivers = selectTopKDrivers(
                    bundlePickupCenter, availableDrivers, TOP_K_DRIVERS);

            for (Driver driver : candidateDrivers) {
                // Generate feasible stop sequences
                List<List<DispatchPlan.Stop>> sequences = seqOptimizer
                        .generateFeasibleSequences(driver, bundle, MAX_SEQUENCE_CANDIDATES);

                for (List<DispatchPlan.Stop> seq : sequences) {
                    DispatchPlan plan = new DispatchPlan(driver, bundle, seq);
                    plan.setTraceId("DA-" + totalDispatchCycles + "-"
                            + bundle.bundleId().substring(0, Math.min(6, bundle.bundleId().length())));

                    // ── Layer 4+5: Constraint Filtering + Scoring ───────
                    boolean valid = scorer.scoreAndPredict(plan);
                    if (valid) {
                        allPlans.add(plan);
                    }
                }
            }
        }

        // ── Layer 6: Assignment Optimization ────────────────────────────
        List<DispatchPlan> bestPlans = assignmentSolver.solve(allPlans);

        // If no confident plans found, use fallback
        if (bestPlans.isEmpty() && !openOrders.isEmpty()) {
            bestPlans = assignmentSolver.fallback(openOrders, availableDrivers);
        }

        // Track bundle stats
        for (DispatchPlan plan : bestPlans) {
            if (plan.getBundleSize() > 1) {
                totalBundledOrders += plan.getBundleSize();
            }
        }

        // ── Layer 7: Reposition decisions ───────────────────────────────
        List<RepositionAgent.RepositionDecision> repositions = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            if (driver.getState() == DriverState.ONLINE_IDLE
                    && driver.getCurrentOrderCount() == 0) {
                RepositionAgent.RepositionDecision decision = repositionAgent.plan(driver);
                if (decision.action() == RepositionAgent.RepositionAction.REPOSITION_SHORT) {
                    repositions.add(decision);
                    // Move driver towards target
                    driver.setTargetLocation(decision.targetPosition());
                }
            }
        }

        return new DispatchResult(bestPlans, repositions);
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private GeoPoint computePickupCenter(DispatchPlan.Bundle bundle) {
        double lat = 0, lon = 0;
        for (Order o : bundle.orders()) {
            lat += o.getPickupPoint().lat();
            lon += o.getPickupPoint().lng();
        }
        return new GeoPoint(lat / bundle.orders().size(), lon / bundle.orders().size());
    }

    private List<Driver> selectTopKDrivers(GeoPoint center, List<Driver> drivers, int k) {
        return drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE)
                .filter(d -> d.getCurrentOrderCount() < 5)
                .sorted(Comparator.comparingDouble(
                        d -> d.getCurrentLocation().distanceTo(center)))
                .limit(k)
                .toList();
    }

    public int getTotalBundledOrders() { return totalBundledOrders; }
    public int getTotalDispatchCycles() { return totalDispatchCycles; }
    public DemandForecastService getDemandForecastService() { return demandForecast; }
    public ZoneOpportunityScorer getOpportunityScorer() { return opportunityScorer; }
    public List<Region> getHotZones() { return demandForecast.getHotZones(zones, 5); }

    public void reset() {
        totalBundledOrders = 0;
        totalDispatchCycles = 0;
    }

    // ── Result ──────────────────────────────────────────────────────────

    public record DispatchResult(
            List<DispatchPlan> plans,
            List<RepositionAgent.RepositionDecision> repositions) {}
}
