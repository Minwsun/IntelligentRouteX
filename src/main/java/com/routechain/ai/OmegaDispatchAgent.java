package com.routechain.ai;

import com.routechain.ai.model.*;
import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.simulation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * OmegaDispatchAgent — AI-Native Urban Dispatch Brain.
 * Full learned pipeline replacing heuristic DispatchAgent.
 *
 * Pipeline (per tick):
 *   1. SpatiotemporalField.update()           → city brain
 *   2. PolicySelector.select(context)          → active policy
 *   3. BundleGraph.build() + searchBundles()   → candidate bundles
 *   4. For each bundle × top-K drivers:
 *        SequenceOptimizer → feasible sequences
 *        ETAModel, LateRiskModel, CancelRiskModel → predictions
 *        ContinuationValueModel → end-state value
 *        PlanRanker → utility
 *        UncertaintyEstimator → confidence
 *   5. HorizonPlanner.evaluateRobust()         → robust score
 *   6. AssignmentSolver.solve()                → conflict-free matching
 *   7. RepositionAgent.plan()                  → idle driver positioning
 *   8. DecisionLog.log()                       → record for learning
 */
public class OmegaDispatchAgent {

    // ── AI components ───────────────────────────────────────────────────
    private final SpatiotemporalField field;
    private final FeatureExtractor featureExtractor;
    private final DecisionLog decisionLog;
    private final PolicySelector policySelector;
    private final HorizonPlanner horizonPlanner;

    // ── Prediction models ───────────────────────────────────────────────
    private final ETAModel etaModel;
    private final LateRiskModel lateRiskModel;
    private final CancelRiskModel cancelRiskModel;
    private final PickupDelayModel pickupDelayModel;
    private final ContinuationValueModel continuationValueModel;
    private final PlanRanker planRanker;
    private final UncertaintyEstimator uncertaintyEstimator;

    // ── Existing components (reused) ────────────────────────────────────
    private final List<Region> regions;

    // ── Replay trainer ──────────────────────────────────────────────────
    private final ReplayTrainer replayTrainer;

    // ── Config ──────────────────────────────────────────────────────────
    private static final int TOP_K_DRIVERS = 5;
    private static final int MAX_BUNDLE_SIZE = 5;
    private static final int RETRAIN_INTERVAL_TICKS = 300;
    private long lastRetrainTick = 0;

    public OmegaDispatchAgent(List<Region> regions) {
        this.regions = regions;
        this.field = new SpatiotemporalField();
        this.featureExtractor = new FeatureExtractor();
        this.decisionLog = new DecisionLog();
        this.policySelector = new PolicySelector();
        this.horizonPlanner = new HorizonPlanner();

        this.etaModel = new ETAModel();
        this.lateRiskModel = new LateRiskModel();
        this.cancelRiskModel = new CancelRiskModel();
        this.pickupDelayModel = new PickupDelayModel();
        this.continuationValueModel = new ContinuationValueModel();
        this.planRanker = new PlanRanker();
        this.uncertaintyEstimator = new UncertaintyEstimator();

        this.replayTrainer = new ReplayTrainer();
    }

    // ── Main dispatch entry point ───────────────────────────────────────

    /**
     * Full 8-step learned dispatch pipeline.
     */
    public DispatchResult dispatch(
            List<Order> pendingOrders, List<Driver> availableDrivers,
            List<Driver> allDrivers, List<Order> allOrders,
            int simulatedHour, double trafficIntensity, WeatherProfile weather) {

        long tick = System.nanoTime(); // for trace IDs

        // ── Step 1: Update city brain ───────────────────────────────────
        field.update(allOrders, allDrivers, simulatedHour, trafficIntensity, weather);

        // ── Step 2: Select policy ──────────────────────────────────────
        double shortage = computeShortage(pendingOrders, availableDrivers);
        double avgWait = computeAvgWait(pendingOrders);
        double surge = computeSurgeLevel(pendingOrders);
        double[] contextFeatures = featureExtractor.contextFeatures(
                trafficIntensity, weather, simulatedHour,
                shortage, avgWait, pendingOrders.size(),
                availableDrivers.size(), surge);

        PolicyProfile activePolicy = policySelector.select(contextFeatures);

        // ── Step 3: Build bundle graph + search ────────────────────────
        BundleGraph graph = new BundleGraph(pendingOrders, field,
                trafficIntensity, weather);
        List<DispatchPlan.Bundle> bundles = graph.searchBundles(MAX_BUNDLE_SIZE);

        // ── Step 4-5: Score all bundle × driver combinations ───────────
        List<DispatchPlan> allPlans = new ArrayList<>();

        // Create sequence optimizer with current conditions
        SequenceOptimizer seqOptimizer = new SequenceOptimizer(trafficIntensity, weather);

        for (DispatchPlan.Bundle bundle : bundles) {
            // Find top-K nearest drivers to bundle centroid
            GeoPoint centroid = computeBundleCentroid(bundle);
            List<Driver> topDrivers = availableDrivers.stream()
                    .sorted(Comparator.comparingDouble(d ->
                            d.getCurrentLocation().distanceTo(centroid)))
                    .limit(TOP_K_DRIVERS)
                    .toList();

            for (Driver driver : topDrivers) {
                DispatchPlan plan = buildAndScorePlan(
                        driver, bundle, simulatedHour, trafficIntensity,
                        weather, activePolicy, seqOptimizer);

                if (plan != null) {
                    allPlans.add(plan);
                }
            }
        }

        // ── Step 6: Conflict-free assignment ───────────────────────────
        AssignmentSolver solver = new AssignmentSolver();
        List<DispatchPlan> selectedPlans = solver.solve(allPlans);

        // ── Step 7: Reposition idle drivers ────────────────────────────
        List<RepositionAgent.RepositionDecision> repositions = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            if (driver.getState() == DriverState.ONLINE_IDLE
                    && driver.getCurrentOrderCount() == 0) {
                // Use field-based reposition
                GeoPoint bestZone = findBestAttractionPoint(driver.getCurrentLocation());
                if (bestZone != null) {
                    double attraction = field.getAttractionAt(bestZone);
                    double currentAttraction = field.getAttractionAt(driver.getCurrentLocation());
                    double dist = driver.getCurrentLocation().distanceTo(bestZone) / 1000.0;
                    if (attraction > currentAttraction * 1.3 && dist < 1.5 && dist > 0.1) {
                        repositions.add(new RepositionAgent.RepositionDecision(
                                RepositionAgent.RepositionAction.REPOSITION_SHORT,
                                bestZone, dist,
                                attraction - currentAttraction,
                                attraction));
                        driver.setTargetLocation(bestZone);
                    }
                }
            }
        }

        // ── Step 8: Log decisions ──────────────────────────────────────
        for (DispatchPlan plan : selectedPlans) {
            double[] pf = featureExtractor.planFeatures(
                    plan, field, trafficIntensity, weather);
            decisionLog.log(tick, contextFeatures, pf,
                    plan.getTotalScore(), activePolicy.name(), plan.getTraceId());
        }

        return new DispatchResult(selectedPlans, repositions,
                activePolicy.name(), graph.getNodeCount(), graph.getEdgeCount());
    }

    // ── Plan building & scoring ─────────────────────────────────────────

    private DispatchPlan buildAndScorePlan(
            Driver driver, DispatchPlan.Bundle bundle,
            int hour, double traffic, WeatherProfile weather,
            PolicyProfile policy, SequenceOptimizer seqOptimizer) {

        // Generate best sequences for this bundle
        List<List<DispatchPlan.Stop>> sequences = seqOptimizer.generateFeasibleSequences(
                driver, bundle, 3);

        if (sequences.isEmpty()) return null;

        // Use the best (shortest route time) sequence
        List<DispatchPlan.Stop> bestSeq = sequences.get(0);
        if (bestSeq.isEmpty()) return null;

        DispatchPlan plan = new DispatchPlan(driver, bundle, bestSeq);
        plan.setTraceId("OMEGA-" + System.nanoTime() + "-" + driver.getId());

        // Deadhead
        double deadheadKm = driver.getCurrentLocation().distanceTo(
                bestSeq.get(0).location()) / 1000.0;
        plan.setPredictedDeadheadKm(deadheadKm);

        // ETA prediction (model or heuristic)
        double totalDist = computeRouteDistanceKm(driver.getCurrentLocation(), bestSeq);
        double[] etaFeatures = featureExtractor.etaFeatures(
                totalDist, traffic, weather, hour, bundle.size(), traffic * 0.8);
        double predictedETA = etaModel.predict(etaFeatures);
        plan.setPredictedTotalMinutes(predictedETA);

        // Late risk prediction
        double avgSlaSlack = bundle.orders().stream()
                .mapToDouble(o -> o.getPromisedEtaMinutes() - predictedETA)
                .average().orElse(10);
        double[] lateFeatures = featureExtractor.lateRiskFeatures(
                totalDist, traffic, weather, hour, bundle.size(), avgSlaSlack, 2.0);
        double lateRisk = lateRiskModel.predict(lateFeatures);
        plan.setLateRisk(lateRisk);
        plan.setOnTimeProbability(1.0 - lateRisk);

        // Cancel risk prediction
        double avgWait = bundle.orders().stream()
                .mapToDouble(o -> Duration.between(o.getCreatedAt(), Instant.now()).toMinutes())
                .average().orElse(3);
        double avgFee = bundle.orders().stream()
                .mapToDouble(Order::getQuotedFee).average().orElse(25000);
        double[] cancelFeatures = featureExtractor.cancelFeatures(
                avgWait, lateRisk, avgFee, weather, bundle.size());
        double cancelRisk = cancelRiskModel.predict(cancelFeatures);
        plan.setCancellationRisk(cancelRisk);

        // Driver profit
        double totalFee = bundle.orders().stream().mapToDouble(Order::getQuotedFee).sum();
        double fuelCost = totalDist * 2500; // ~2500 VND/km
        double profit = totalFee - fuelCost;
        plan.setDriverProfit(profit);
        plan.setCustomerFee(avgFee);

        // Bundle efficiency
        double standaloneDist = bundle.orders().stream()
                .mapToDouble(o -> o.getPickupPoint().distanceTo(o.getDropoffPoint()) / 1000.0)
                .sum();
        double efficiency = standaloneDist > 0
                ? Math.max(0, 1.0 - (totalDist / (standaloneDist * 1.5))) : 0.5;
        plan.setBundleEfficiency(efficiency);

        // Continuation value (end-state)
        GeoPoint endPoint = plan.getEndZonePoint();
        int estimatedEndHour = (hour + (int) (predictedETA / 60)) % 24;
        double[] endFeatures = featureExtractor.endStateFeatures(
                endPoint, estimatedEndHour, field, weather, driver);
        double continuationValue = continuationValueModel.predictNormalized(endFeatures);
        plan.setEndZoneOpportunity(continuationValue);
        plan.setNextOrderAcquisitionScore(continuationValue * 0.8);

        // Congestion and reposition penalties
        plan.setCongestionPenalty(traffic * 0.6);
        plan.setRepositionPenalty(deadheadKm > 3.0 ? 0.3 : 0.1);

        // Hard constraints check
        if (lateRisk > 0.40) return null;   // too risky
        if (profit < 2000) return null;      // not profitable enough
        if (deadheadKm > 6.0) return null;   // too far
        double detourRatio = standaloneDist > 0 ? totalDist / standaloneDist : 1.5;
        if (detourRatio > 2.0) return null;  // too much detour

        // ── Robust scoring with learned ranker ─────────────────────────
        double[] planFeatures = featureExtractor.planFeatures(
                plan, field, traffic, weather);

        // HorizonPlanner: evaluate under 5 scenarios
        double robustScore = horizonPlanner.evaluateRobust(
                planFeatures, planRanker, uncertaintyEstimator,
                traffic, weather);

        // Uncertainty confidence
        UncertaintyEstimator.Prediction pred = uncertaintyEstimator.predict(planFeatures);

        plan.setTotalScore(robustScore);
        plan.setConfidence(pred.confidence());

        return plan;
    }

    // ── Learning callbacks ──────────────────────────────────────────────

    /**
     * Called when an order is delivered. Updates all relevant models.
     */
    public void onOrderDelivered(Order order, Driver driver,
                                  double actualETAMinutes, boolean wasLate,
                                  double actualProfit, double traffic,
                                  WeatherProfile weather, int hour, long currentTick) {

        double distKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;

        // Update ETA model
        double[] etaF = featureExtractor.etaFeatures(distKm, traffic, weather, hour, 1, traffic * 0.8);
        etaModel.update(etaF, actualETAMinutes);

        // Update late risk model
        double slaSlack = order.getPromisedEtaMinutes() - actualETAMinutes;
        double[] lateF = featureExtractor.lateRiskFeatures(distKm, traffic, weather, hour, 1, slaSlack, 2.0);
        lateRiskModel.update(lateF, wasLate ? 1.0 : 0.0);

        // Update cancel risk model (not cancelled since delivered)
        double[] cancelF = featureExtractor.cancelFeatures(3.0, wasLate ? 0.5 : 0.1,
                order.getQuotedFee(), weather, 1);
        cancelRiskModel.update(cancelF, 0.0);

        // Record continuation value completion
        double[] endF = featureExtractor.endStateFeatures(
                driver.getCurrentLocation(), hour, field, weather, driver);
        continuationValueModel.recordCompletion(
                driver.getId(), currentTick, endF, driver.getAvgEarningPerHour());

        // Record reward for plan ranker training
        double actualUtility = PlanRanker.computeActualUtility(
                !wasLate, actualProfit, 0.5, 0.5, 0, false);

        // Record policy reward
        double[] ctx = featureExtractor.contextFeatures(traffic, weather, hour,
                0.3, 3.0, 10, 5, 0.3);
        policySelector.recordReward(policySelector.getLastSelectedPolicy(), ctx, actualUtility);
    }

    /**
     * Called when an order is cancelled. Updates cancel risk model.
     */
    public void onOrderCancelled(Order order, double traffic,
                                  WeatherProfile weather, int hour) {
        double[] cancelF = featureExtractor.cancelFeatures(
                5.0, 0.5, order.getQuotedFee(), weather, 1);
        cancelRiskModel.update(cancelF, 1.0);
    }

    /**
     * Periodic maintenance: tick continuation value model, trigger retrain.
     */
    public void onTick(long currentTick, java.util.function.Function<String, Double> earningLookup) {
        // Tick continuation value deferred training
        continuationValueModel.tickAndTrain(currentTick, earningLookup);

        // Periodic batch retrain
        if (currentTick - lastRetrainTick >= RETRAIN_INTERVAL_TICKS) {
            lastRetrainTick = currentTick;
            replayTrainer.retrain(decisionLog, planRanker, uncertaintyEstimator, policySelector);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private GeoPoint computeBundleCentroid(DispatchPlan.Bundle bundle) {
        double lat = 0, lng = 0;
        for (Order o : bundle.orders()) {
            lat += o.getPickupPoint().lat();
            lng += o.getPickupPoint().lng();
        }
        return new GeoPoint(lat / bundle.orders().size(), lng / bundle.orders().size());
    }

    private double computeRouteDistanceKm(GeoPoint start, List<DispatchPlan.Stop> seq) {
        double dist = 0;
        GeoPoint prev = start;
        for (DispatchPlan.Stop stop : seq) {
            dist += prev.distanceTo(stop.location());
            prev = stop.location();
        }
        return dist / 1000.0;
    }

    private double computeShortage(List<Order> pending, List<Driver> available) {
        if (pending.isEmpty()) return 0;
        return Math.min(1.0, (double) (pending.size() - available.size()) / pending.size());
    }

    private double computeAvgWait(List<Order> pending) {
        if (pending.isEmpty()) return 0;
        return pending.stream()
                .mapToDouble(o -> Duration.between(o.getCreatedAt(), Instant.now()).toMinutes())
                .average().orElse(3.0);
    }

    private double computeSurgeLevel(List<Order> pending) {
        return Math.min(1.0, pending.size() / 20.0);
    }

    private GeoPoint findBestAttractionPoint(GeoPoint currentPos) {
        double bestScore = 0;
        GeoPoint bestPoint = null;
        for (int r = 0; r < SpatiotemporalField.ROWS; r++) {
            for (int c = 0; c < SpatiotemporalField.COLS; c++) {
                GeoPoint center = field.cellCenter(r, c);
                double dist = currentPos.distanceTo(center) / 1000.0;
                if (dist > 1.5) continue;

                double score = field.getAttractionAt(center);
                if (score > bestScore) {
                    bestScore = score;
                    bestPoint = center;
                }
            }
        }
        return bestPoint;
    }

    // ── Accessors for diagnostics ───────────────────────────────────────

    public SpatiotemporalField getField() { return field; }
    public DecisionLog getDecisionLog() { return decisionLog; }
    public PolicySelector getPolicySelector() { return policySelector; }
    public String getActivePolicy() { return policySelector.getLastSelectedPolicy(); }
    public boolean isModelsWarmedUp() { return planRanker.isWarmedUp(); }

    public ModelDiagnostics getDiagnostics() {
        return new ModelDiagnostics(
                etaModel.getSampleCount(), etaModel.isWarmedUp(),
                lateRiskModel.isWarmedUp(), cancelRiskModel.isWarmedUp(),
                continuationValueModel.isWarmedUp(), planRanker.isWarmedUp(),
                continuationValueModel.getPendingCount(),
                decisionLog.size(), decisionLog.completedCount(),
                policySelector.getSelectionCounts());
    }

    public void reset() {
        field.reset();
        decisionLog.clear();
        policySelector.reset();
        etaModel.reset();
        lateRiskModel.reset();
        cancelRiskModel.reset();
        pickupDelayModel.reset();
        continuationValueModel.reset();
        planRanker.reset();
        uncertaintyEstimator.reset();
        lastRetrainTick = 0;
    }

    // ── Result records ──────────────────────────────────────────────────

    public record DispatchResult(
            List<DispatchPlan> plans,
            List<RepositionAgent.RepositionDecision> repositions,
            String policyUsed,
            int graphNodes,
            int graphEdges) {}

    public record ModelDiagnostics(
            long etaSamples, boolean etaWarmedUp,
            boolean lateWarmedUp, boolean cancelWarmedUp,
            boolean continuationWarmedUp, boolean rankerWarmedUp,
            int continuationPending,
            int logSize, int logCompleted,
            Map<String, Integer> policySelections) {}
}
