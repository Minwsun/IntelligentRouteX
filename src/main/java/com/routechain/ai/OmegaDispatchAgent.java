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
 * Driver-centric learned pipeline.
 *
 * Pipeline (per tick):
 *   1. SpatiotemporalField.update()           → city brain
 *   2. PolicySelector.select(context)          → active policy
 *   3. NearbyOrderIndexer.rebuild()            → spatial order index
 *   4. For each eligible driver:
 *        DriverContextBuilder.build()          → local world snapshot
 *        DriverPlanGenerator.generate()        → candidate plans
 *        scoreAndValidatePlan() per plan:
 *          ETAModel, LateRiskModel, CancelRiskModel → predictions
 *          ContinuationValueModel → end-state value
 *          PlanRanker + HorizonPlanner → robust utility
 *          UncertaintyEstimator → confidence
 *   5. AssignmentSolver.solve()                → conflict-free matching
 *   6. Fallback nearest-driver for unassigned  → coverage guarantee
 *   7. Reposition idle drivers                 → field-attraction based
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

    // ── Centralized scoring + constraints (Phase 3) ─────────────────────
    private final PlanUtilityScorer utilityScorer;
    private final ConstraintEngine constraintEngine;

    // ── Existing components (reused) ────────────────────────────────────
    private final List<Region> regions;

    // ── Driver-centric components (NEW) ─────────────────────────────────
    private final NearbyOrderIndexer orderIndex;
    private final DriverContextBuilder contextBuilder;
    private final DriverPlanGenerator planGenerator;

    // ── Replay trainer ──────────────────────────────────────────────────
    private final ReplayTrainer replayTrainer;

    // ── Config ──────────────────────────────────────────────────────────
    private static final int FALLBACK_DIRECT_COUNT = 25;
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

        // Phase 3: centralized scorer + constraint engine
        this.utilityScorer = new PlanUtilityScorer();
        this.constraintEngine = new ConstraintEngine();

        // Driver-centric components
        this.orderIndex = new NearbyOrderIndexer();
        this.contextBuilder = new DriverContextBuilder(orderIndex, field);
        this.planGenerator = new DriverPlanGenerator();
    }

    // ── Main dispatch entry point ───────────────────────────────────────

    /**
     * Driver-centric learned dispatch pipeline.
     *
     * Flow: driver → local world → candidate plans → AI scoring → global matching
     */
    public DispatchResult dispatch(
            List<Order> pendingOrders, List<Driver> availableDrivers,
            List<Driver> allDrivers, List<Order> allOrders,
            int simulatedHour, double trafficIntensity, WeatherProfile weather) {

        long tick = System.nanoTime();

        // ── Step 1: Update city brain (UNCHANGED) ────────────────────────
        field.update(allOrders, allDrivers, simulatedHour, trafficIntensity, weather);

        // ── Step 2: Select policy (UNCHANGED) ───────────────────────────
        double shortage = computeShortage(pendingOrders, availableDrivers);
        double avgWait = computeAvgWaitSimulated(pendingOrders);
        double surge = computeSurgeLevel(pendingOrders);
        double[] contextFeatures = featureExtractor.contextFeatures(
                trafficIntensity, weather, simulatedHour,
                shortage, avgWait, pendingOrders.size(),
                availableDrivers.size(), surge);

        PolicyProfile activePolicy = policySelector.select(contextFeatures);

        // ── Step 3: Build spatial order index (NEW) ─────────────────────
        contextBuilder.rebuildIndex(pendingOrders);

        // ── Step 4: Driver-centric plan generation (NEW) ────────────────
        // For each eligible driver: build local context → generate plans → score
        List<DispatchPlan> allPlans = new ArrayList<>();
        int rejectedLate = 0, rejectedProfit = 0, rejectedDead = 0, rejectedDetour = 0;
        int totalCandidates = 0;

        for (Driver driver : availableDrivers) {
            // Build driver's local world snapshot
            DriverDecisionContext ctx = contextBuilder.build(
                    driver, trafficIntensity, weather, simulatedHour);

            // Generate candidate plans (single, bundle, hold, reposition)
            List<DispatchPlan> driverPlans = planGenerator.generate(
                    ctx, trafficIntensity, weather, simulatedHour);

            totalCandidates += driverPlans.size();

            // Score each plan with full AI model stack
            for (DispatchPlan plan : driverPlans) {
                // Skip hold/reposition plans from AI scoring (keep prelim score)
                if (plan.getBundle().size() == 0) {
                    allPlans.add(plan);
                    continue;
                }

                int[] rejectReasons = new int[4];
                boolean valid = scoreAndValidatePlan(
                        plan, simulatedHour, trafficIntensity,
                        weather, activePolicy, rejectReasons);

                if (valid) {
                    allPlans.add(plan);
                } else {
                    rejectedLate += rejectReasons[0];
                    rejectedProfit += rejectReasons[1];
                    rejectedDead += rejectReasons[2];
                    rejectedDetour += rejectReasons[3];
                }
            }
        }

        // ── Step 5: Conflict-free assignment ────────────────────────────
        AssignmentSolver solver = new AssignmentSolver();
        List<DispatchPlan> selectedPlans = solver.solve(allPlans);

        // ── Step 6: FALLBACK — nearest-driver for uncovered orders ──────
        if (selectedPlans.size() < 5 && !availableDrivers.isEmpty()) {
            SequenceOptimizer seqOptimizer = new SequenceOptimizer(
                    trafficIntensity, weather);
            applyFallbackAssignment(selectedPlans, pendingOrders,
                    availableDrivers, seqOptimizer, trafficIntensity);
        }

        // ── Step 7: Reposition idle drivers ─────────────────────────────
        List<RepositionAgent.RepositionDecision> repositions = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            if (driver.getState() == DriverState.ONLINE_IDLE
                    && driver.getCurrentOrderCount() == 0) {
                GeoPoint bestZone = findBestAttractionPoint(
                        driver.getCurrentLocation());
                if (bestZone != null) {
                    double attraction = field.getAttractionAt(bestZone);
                    double currentAttraction = field.getAttractionAt(
                            driver.getCurrentLocation());
                    double dist = driver.getCurrentLocation()
                            .distanceTo(bestZone) / 1000.0;
                    if (attraction > currentAttraction * 1.3
                            && dist < 1.5 && dist > 0.1) {
                        repositions.add(
                                new RepositionAgent.RepositionDecision(
                                        RepositionAgent.RepositionAction
                                                .REPOSITION_SHORT,
                                        bestZone, dist,
                                        attraction - currentAttraction,
                                        attraction));
                        driver.setTargetLocation(bestZone);
                    }
                }
            }
        }

        // ── Step 8: Log decisions (UNCHANGED) ──────────────────────────
        for (DispatchPlan plan : selectedPlans) {
            if (plan.getBundle().size() > 0) {
                double[] pf = featureExtractor.planFeatures(
                        plan, field, trafficIntensity, weather);
                decisionLog.log(tick, contextFeatures, pf,
                        plan.getTotalScore(), activePolicy.name(),
                        plan.getTraceId());
            }
        }

        // Diagnostic log
        System.out.printf(
                "[Omega-DC] Policy:%s Drivers:%d Pending:%d " +
                        "Candidates:%d Plans:%d→%d " +
                        "(Rej: late=%d profit=%d dead=%d detour=%d)%n",
                activePolicy.name(), availableDrivers.size(),
                pendingOrders.size(), totalCandidates,
                allPlans.size(), selectedPlans.size(),
                rejectedLate, rejectedProfit, rejectedDead, rejectedDetour);

        return new DispatchResult(selectedPlans, repositions,
                activePolicy.name(), pendingOrders.size(),
                availableDrivers.size());
    }

    // ── Plan scoring (reuses all existing AI models) ────────────────────

    /**
     * Score and validate a plan generated by DriverPlanGenerator.
     *
     * Uses the same AI models as before (ETAModel, LateRiskModel, etc.)
     * but now operates on driver-centric plans instead of bundle-first plans.
     *
     * @return true if plan passes hard constraints, false to reject
     */
    private boolean scoreAndValidatePlan(
            DispatchPlan plan, int hour, double traffic,
            WeatherProfile weather, PolicyProfile policy,
            int[] rejectReasons) {

        Driver driver = plan.getDriver();
        DispatchPlan.Bundle bundle = plan.getBundle();
        List<DispatchPlan.Stop> seq = plan.getSequence();

        if (seq.isEmpty()) return false;

        // Deadhead (may already be set by PlanGenerator, recompute for accuracy)
        double deadheadKm = driver.getCurrentLocation().distanceTo(
                seq.get(0).location()) / 1000.0;
        plan.setPredictedDeadheadKm(deadheadKm);

        // ETA prediction
        double totalDist = computeRouteDistanceKm(
                driver.getCurrentLocation(), seq);
        double[] etaFeatures = featureExtractor.etaFeatures(
                totalDist, traffic, weather, hour,
                bundle.size(), traffic * 0.8);
        double predictedETA = etaModel.predict(etaFeatures);
        plan.setPredictedTotalMinutes(predictedETA);

        // Late risk
        double avgSlaSlack = bundle.orders().stream()
                .mapToDouble(o -> o.getPromisedEtaMinutes() - predictedETA)
                .average().orElse(10);
        double[] lateFeatures = featureExtractor.lateRiskFeatures(
                totalDist, traffic, weather, hour,
                bundle.size(), avgSlaSlack, 2.0);
        double lateRisk = lateRiskModel.predict(lateFeatures);
        plan.setLateRisk(lateRisk);
        plan.setOnTimeProbability(1.0 - lateRisk);

        // Cancel risk
        double avgFee = bundle.orders().stream()
                .mapToDouble(Order::getQuotedFee).average().orElse(25000);
        double[] cancelFeatures = featureExtractor.cancelFeatures(
                3.0, lateRisk, avgFee, weather, bundle.size());
        double cancelRisk = cancelRiskModel.predict(cancelFeatures);
        plan.setCancellationRisk(cancelRisk);

        // Driver profit
        double totalFee = bundle.orders().stream()
                .mapToDouble(Order::getQuotedFee).sum();
        double fuelCost = totalDist * 2500;
        double profit = totalFee - fuelCost;
        plan.setDriverProfit(profit);
        plan.setCustomerFee(avgFee);

        // Bundle efficiency (keep existing if already set)
        if (plan.getBundleEfficiency() == 0) {
            double standaloneDist = bundle.orders().stream()
                    .mapToDouble(o -> o.getPickupPoint()
                            .distanceTo(o.getDropoffPoint()) / 1000.0)
                    .sum();
            double efficiency = standaloneDist > 0
                    ? Math.max(0, 1.0 - (totalDist / (standaloneDist * 1.5)))
                    : 0.5;
            plan.setBundleEfficiency(efficiency);
        }

        // Continuation value — END-STATE as primary objective component
        GeoPoint endPoint = plan.getEndZonePoint();
        int estimatedEndHour = (hour + (int) (predictedETA / 60)) % 24;
        double[] endFeatures = featureExtractor.endStateFeatures(
                endPoint, estimatedEndHour, field, weather, driver);
        double continuationValue = continuationValueModel
                .predictNormalized(endFeatures);
        plan.setEndZoneOpportunity(continuationValue);
        plan.setNextOrderAcquisitionScore(continuationValue * 0.8);

        plan.setCongestionPenalty(traffic * 0.6);
        plan.setRepositionPenalty(deadheadKm > 3.0 ? 0.3 : 0.1);

        // ── Hard constraints (delegated to ConstraintEngine) ──────────
        // Use a dynamic batch cap of 5 (default max) — the real cap was already
        // applied during plan generation in DriverPlanGenerator
        int dynamicBatchCap = 5;
        if (!constraintEngine.validate(plan, dynamicBatchCap, rejectReasons)) {
            return false;
        }

        // ── Robust scoring (HorizonPlanner + Ranker for base) ──────────
        double[] planFeatures = featureExtractor.planFeatures(
                plan, field, traffic, weather);

        double robustScore = horizonPlanner.evaluateRobust(
                planFeatures, planRanker, uncertaintyEstimator,
                traffic, weather);

        UncertaintyEstimator.Prediction pred = uncertaintyEstimator
                .predict(planFeatures);

        // ── Final scoring via PlanUtilityScorer (Phase 3) ───────────────
        // Blend robust score with centralized utility for final rank
        double utilityScore = utilityScorer.score(plan);
        double finalScore = robustScore * 0.6 + utilityScore * 0.4;

        plan.setTotalScore(finalScore);
        plan.setConfidence(pred.confidence());
        plan.setTraceId("OMEGA-DC-" + System.nanoTime()
                + "-" + driver.getId());

        return true;
    }

    /**
     * Fallback assignment for uncovered orders using nearest-driver.
     * Guarantees coverage even when AI pipeline produces few valid plans.
     */
    private void applyFallbackAssignment(
            List<DispatchPlan> selectedPlans, List<Order> pendingOrders,
            List<Driver> availableDrivers, SequenceOptimizer seqOptimizer,
            double trafficIntensity) {

        Set<String> assignedDriverIds = new HashSet<>();
        Set<String> assignedOrderIds = new HashSet<>();
        for (DispatchPlan p : selectedPlans) {
            assignedDriverIds.add(p.getDriver().getId());
            for (Order o : p.getOrders()) {
                assignedOrderIds.add(o.getId());
            }
        }

        List<Order> unassigned = pendingOrders.stream()
                .filter(o -> !assignedOrderIds.contains(o.getId()))
                .sorted(Comparator.comparing(Order::getCreatedAt))
                .limit(FALLBACK_DIRECT_COUNT)
                .toList();

        List<Driver> freeDrivers = new ArrayList<>(
                availableDrivers.stream()
                        .filter(d -> !assignedDriverIds.contains(d.getId()))
                        .toList());

        for (Order order : unassigned) {
            if (freeDrivers.isEmpty()) break;

            Driver nearest = freeDrivers.stream()
                    .min(Comparator.comparingDouble(d ->
                            d.getCurrentLocation().distanceTo(
                                    order.getPickupPoint())))
                    .orElse(null);
            if (nearest == null) break;

            double distKm = nearest.getCurrentLocation().distanceTo(
                    order.getPickupPoint()) / 1000.0;
            if (distKm > 12.0) continue;

            DispatchPlan.Bundle singleBundle = new DispatchPlan.Bundle(
                    "FBACK-" + order.getId(), List.of(order), 0, 1);

            List<List<DispatchPlan.Stop>> seqs =
                    seqOptimizer.generateFeasibleSequences(
                            nearest, singleBundle, 1);
            if (seqs.isEmpty() || seqs.get(0).isEmpty()) continue;

            DispatchPlan fallbackPlan = new DispatchPlan(
                    nearest, singleBundle, seqs.get(0));
            fallbackPlan.setTraceId("FBACK-" + nearest.getId());
            fallbackPlan.setPredictedDeadheadKm(distKm);

            double delivDist = order.getPickupPoint().distanceTo(
                    order.getDropoffPoint()) / 1000.0;
            double speed = Math.max(8, 25 * (1 - trafficIntensity * 0.5));
            double eta = ((distKm + delivDist) / speed) * 60;
            fallbackPlan.setPredictedTotalMinutes(eta);
            fallbackPlan.setDriverProfit(
                    order.getQuotedFee() - delivDist * 2500);
            fallbackPlan.setLateRisk(0.2);
            fallbackPlan.setOnTimeProbability(0.8);
            fallbackPlan.setCancellationRisk(0.1);
            fallbackPlan.setCustomerFee(order.getQuotedFee());
            fallbackPlan.setBundleEfficiency(1.0);
            fallbackPlan.setEndZoneOpportunity(0.5);
            fallbackPlan.setNextOrderAcquisitionScore(0.4);
            fallbackPlan.setCongestionPenalty(trafficIntensity * 0.4);
            fallbackPlan.setRepositionPenalty(0.1);
            fallbackPlan.setTotalScore(0.5);
            fallbackPlan.setConfidence(0.5);

            selectedPlans.add(fallbackPlan);
            freeDrivers.remove(nearest);
            assignedDriverIds.add(nearest.getId());
            assignedOrderIds.add(order.getId());
        }
    }

    // ── Learning callbacks ──────────────────────────────────────────────

    public void onOrderDelivered(Order order, Driver driver,
                                  double actualETAMinutes, boolean wasLate,
                                  double actualProfit, double traffic,
                                  WeatherProfile weather, int hour, long currentTick) {

        double distKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;

        double[] etaF = featureExtractor.etaFeatures(distKm, traffic, weather, hour, 1, traffic * 0.8);
        etaModel.update(etaF, actualETAMinutes);

        double slaSlack = order.getPromisedEtaMinutes() - actualETAMinutes;
        double[] lateF = featureExtractor.lateRiskFeatures(distKm, traffic, weather, hour, 1, slaSlack, 2.0);
        lateRiskModel.update(lateF, wasLate ? 1.0 : 0.0);

        double[] cancelF = featureExtractor.cancelFeatures(3.0, wasLate ? 0.5 : 0.1,
                order.getQuotedFee(), weather, 1);
        cancelRiskModel.update(cancelF, 0.0);

        double[] endF = featureExtractor.endStateFeatures(
                driver.getCurrentLocation(), hour, field, weather, driver);
        continuationValueModel.recordCompletion(
                driver.getId(), currentTick, endF, driver.getAvgEarningPerHour());

        double actualUtility = PlanRanker.computeActualUtility(
                !wasLate, actualProfit, 0.5, 0.5, 0, false);

        double[] ctx = featureExtractor.contextFeatures(traffic, weather, hour,
                0.3, 3.0, 10, 5, 0.3);
        policySelector.recordReward(policySelector.getLastSelectedPolicy(), ctx, actualUtility);
    }

    public void onOrderCancelled(Order order, double traffic,
                                  WeatherProfile weather, int hour) {
        double[] cancelF = featureExtractor.cancelFeatures(
                5.0, 0.5, order.getQuotedFee(), weather, 1);
        cancelRiskModel.update(cancelF, 1.0);
    }

    public void onTick(long currentTick, java.util.function.Function<String, Double> earningLookup) {
        continuationValueModel.tickAndTrain(currentTick, earningLookup);

        if (currentTick - lastRetrainTick >= RETRAIN_INTERVAL_TICKS) {
            lastRetrainTick = currentTick;
            replayTrainer.retrain(decisionLog, planRanker, uncertaintyEstimator, policySelector);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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
        return Math.max(0, Math.min(1.0,
                (double) (pending.size() - available.size()) / Math.max(1, pending.size())));
    }

    private double computeAvgWaitSimulated(List<Order> pending) {
        if (pending.isEmpty()) return 0;
        Instant now = Instant.now();
        return pending.stream()
                .mapToDouble(o -> {
                    long secs = Duration.between(o.getCreatedAt(), now).getSeconds();
                    return Math.max(0, secs / 60.0);
                })
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
            int pendingOrderCount,
            int availableDriverCount) {}

    public record ModelDiagnostics(
            long etaSamples, boolean etaWarmedUp,
            boolean lateWarmedUp, boolean cancelWarmedUp,
            boolean continuationWarmedUp, boolean rankerWarmedUp,
            int continuationPending,
            int logSize, int logCompleted,
            Map<String, Integer> policySelections) {}
}
