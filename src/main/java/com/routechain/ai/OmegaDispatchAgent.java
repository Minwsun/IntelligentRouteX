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

    public enum AblationMode {
        FULL,
        NO_HOLD,
        NO_REPOSITION,
        NO_CONTINUATION,
        NO_FALLBACK_TUNING,
        SMALL_BATCH_ONLY
    }

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
    private final Map<String, PendingTraceOutcome> pendingTraceOutcomes = new HashMap<>();
    private boolean diagnosticLoggingEnabled = true;
    private AblationMode ablationMode = AblationMode.FULL;

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
        applyAblationConfig();
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
            int simulatedHour, double trafficIntensity, WeatherProfile weather,
            Instant currentTime) {

        long tick = currentTime.toEpochMilli();

        // ── Step 1: Update city brain (UNCHANGED) ────────────────────────
        field.update(allOrders, allDrivers, simulatedHour, trafficIntensity, weather);

        // ── Step 2: Select policy (UNCHANGED) ───────────────────────────
        double shortage = computeShortage(pendingOrders, availableDrivers);
        double avgWait = computeAvgWaitSimulated(pendingOrders, currentTime);
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
                    driver, trafficIntensity, weather, simulatedHour, currentTime);

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
                        weather, activePolicy, currentTime, rejectReasons);

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
        List<DispatchPlan> selectedPlans = new ArrayList<>(solver.solve(allPlans));

        // ── Step 6: FALLBACK — nearest-driver for uncovered orders ──────
        if (selectedPlans.size() < 5 && !availableDrivers.isEmpty()) {
            SequenceOptimizer seqOptimizer = new SequenceOptimizer(
                    trafficIntensity, weather);
            applyFallbackAssignment(selectedPlans, pendingOrders,
                    availableDrivers, seqOptimizer, trafficIntensity, weather, currentTime);
        }

        // ── Step 7: Reposition idle drivers ─────────────────────────────
        List<RepositionAgent.RepositionDecision> repositions = new ArrayList<>();
        for (Driver driver : availableDrivers) {
            if (driver.getState() == DriverState.ONLINE_IDLE
                    && driver.getCurrentOrderCount() == 0) {
                GeoPoint bestZone = findBestAttractionPoint(
                        driver.getCurrentLocation());
                if (bestZone != null) {
                    double attraction = field.getRiskAdjustedAttractionAt(bestZone);
                    double currentAttraction = field.getRiskAdjustedAttractionAt(
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
                registerPendingOutcome(plan);
            }
        }

        // Diagnostic log
        if (diagnosticLoggingEnabled) {
            System.out.printf(
                    "[Omega-DC] Policy:%s Drivers:%d Pending:%d " +
                            "Candidates:%d Plans:%d→%d " +
                            "(Rej: late=%d profit=%d dead=%d detour=%d)%n",
                    activePolicy.name(), availableDrivers.size(),
                    pendingOrders.size(), totalCandidates,
                    allPlans.size(), selectedPlans.size(),
                    rejectedLate, rejectedProfit, rejectedDead, rejectedDetour);
        }

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
            Instant currentTime,
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
        double pickupWeatherExposure = field.getWeatherExposureAt(seq.get(0).location());
        double pickupCongestionExposure = field.getCongestionExposureAt(seq.get(0).location());
        double operationalDrag = switch (weather) {
            case CLEAR -> pickupCongestionExposure * 0.02;
            case LIGHT_RAIN -> pickupCongestionExposure * 0.03 + pickupWeatherExposure * 0.02;
            case HEAVY_RAIN -> pickupCongestionExposure * 0.07 + pickupWeatherExposure * 0.06 + 0.03;
            case STORM -> pickupCongestionExposure * 0.10 + pickupWeatherExposure * 0.08 + 0.06;
        };
        predictedETA *= (1.0 + operationalDrag);
        final double adjustedPredictedETA = predictedETA;
        plan.setPredictedTotalMinutes(adjustedPredictedETA);

        // Late risk
        double avgSlaSlack = bundle.orders().stream()
                .mapToDouble(o -> {
                    double elapsedMinutes = Duration.between(
                            o.getCreatedAt(), currentTime).toSeconds() / 60.0;
                    return o.getPromisedEtaMinutes() - elapsedMinutes - adjustedPredictedETA;
                })
                .average().orElse(10);
        double[] lateFeatures = featureExtractor.lateRiskFeatures(
                totalDist, traffic, weather, hour,
                bundle.size(), avgSlaSlack, 2.0);
        double lateRisk = lateRiskModel.predict(lateFeatures);
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            lateRisk = Math.min(0.95, lateRisk
                    + operationalDrag * 0.35
                    + Math.max(0.0, deadheadKm - 2.5) * 0.025
                    + Math.max(0, bundle.size() - 2) * pickupWeatherExposure * 0.025);
        }
        plan.setLateRisk(lateRisk);
        plan.setOnTimeProbability(1.0 - lateRisk);

        // Cancel risk
        double avgFee = bundle.orders().stream()
                .mapToDouble(Order::getQuotedFee).average().orElse(25000);
        double[] cancelFeatures = featureExtractor.cancelFeatures(
                3.0, lateRisk, avgFee, weather, bundle.size());
        double cancelRisk = cancelRiskModel.predict(cancelFeatures);
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            cancelRisk = Math.min(0.90, cancelRisk
                    + operationalDrag * 0.16
                    + Math.max(0.0, deadheadKm - 3.0) * 0.02);
        }
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
        double endWeatherExposure = field.getWeatherExposureAt(endPoint);
        double endCongestionExposure = field.getCongestionExposureAt(endPoint);
        double endStateRiskPenalty = endCongestionExposure * 0.22
                + endWeatherExposure * 0.18;
        double adjustedContinuation = Math.max(0.05,
                continuationValue * (1.0 - endStateRiskPenalty));
        double futureOrderSignal = field.getForecastDemandAt(endPoint, 10) * 0.45
                + field.getForecastDemandAt(endPoint, 15) * 0.35
                + field.getForecastDemandAt(endPoint, 30) * 0.20;
        double nextOrderScore = Math.max(0.05, Math.min(1.0,
                futureOrderSignal * (1.0 - endCongestionExposure * 0.25 - endWeatherExposure * 0.20)));

        if (ablationMode == AblationMode.NO_CONTINUATION) {
            adjustedContinuation = 0.0;
            nextOrderScore = 0.0;
        }

        plan.setEndZoneOpportunity(adjustedContinuation);
        plan.setNextOrderAcquisitionScore(nextOrderScore);

        double congestionPenalty = Math.min(1.0,
                traffic * 0.35
                        + endCongestionExposure * 0.45
                        + endWeatherExposure * 0.20);
        plan.setCongestionPenalty(congestionPenalty);
        plan.setRepositionPenalty(deadheadKm > 3.0 ? 0.3 + endWeatherExposure * 0.08 : 0.1);

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
            double trafficIntensity, WeatherProfile weather, Instant currentTime) {

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
            boolean legacyFallbackTuning = ablationMode == AblationMode.NO_FALLBACK_TUNING;
            double maxFallbackDeadheadKm = legacyFallbackTuning
                    ? 6.0 - trafficIntensity * 1.5
                    : switch (weather) {
                        case CLEAR -> 7.5 - trafficIntensity * 2.0;
                        case LIGHT_RAIN -> 6.5 - trafficIntensity * 2.0;
                        case HEAVY_RAIN -> 2.8 - trafficIntensity * 1.0;
                        case STORM -> 2.0 - trafficIntensity * 0.5;
                    };
            double minFallbackDeadhead = legacyFallbackTuning ? 3.0
                    : (weather == WeatherProfile.HEAVY_RAIN
                    || weather == WeatherProfile.STORM) ? 1.8 : 3.0;
            if (distKm > Math.max(minFallbackDeadhead, maxFallbackDeadheadKm)) continue;

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
            speed *= switch (weather) {
                case CLEAR -> 1.0;
                case LIGHT_RAIN -> 0.9;
                case HEAVY_RAIN -> 0.72;
                case STORM -> 0.55;
            };
            speed = Math.max(8, speed);
            double eta = ((distKm + delivDist) / speed) * 60;
            double elapsedMinutes = Duration.between(
                    order.getCreatedAt(), currentTime).toSeconds() / 60.0;
            double lateSlack = order.getPromisedEtaMinutes() - elapsedMinutes - eta;
            double weatherLatePenalty = switch (weather) {
                case CLEAR -> 0.02;
                case LIGHT_RAIN -> 0.06;
                case HEAVY_RAIN -> 0.15;
                case STORM -> 0.25;
            };
            double lateRisk = legacyFallbackTuning
                    ? Math.max(0.10, Math.min(0.70,
                    0.18 + Math.max(0.0, -lateSlack) / Math.max(8.0, order.getPromisedEtaMinutes())
                            + trafficIntensity * 0.10))
                    : Math.max(0.08, Math.min(0.75,
                    0.12
                            + Math.max(0.0, -lateSlack) / Math.max(6.0, order.getPromisedEtaMinutes())
                            + trafficIntensity * 0.12
                            + weatherLatePenalty
                            + Math.max(0.0, distKm - 3.0) * 0.04));
            double onTimeProbability = Math.max(0.20, 1.0 - lateRisk);
            double cancelRisk = legacyFallbackTuning
                    ? Math.max(0.05, Math.min(0.45,
                    order.getCancellationRisk() * 0.85
                            + trafficIntensity * 0.06))
                    : Math.max(0.05, Math.min(0.45,
                    order.getCancellationRisk() * 0.8
                            + trafficIntensity * 0.08
                            + weatherLatePenalty * 0.6));
            double totalFuelProxy = (distKm + delivDist) * 2200;
            double bundleEfficiency = 1.0 / (1.0 + distKm / Math.max(0.5, delivDist));
            double score = legacyFallbackTuning
                    ? Math.max(0.08,
                    onTimeProbability * 0.35
                            + bundleEfficiency * 0.15
                            + Math.max(0.0, order.getQuotedFee() - totalFuelProxy) / 40000.0 * 0.25
                            - distKm / 8.0 * 0.10
                            - cancelRisk * 0.10)
                    : Math.max(0.08,
                    onTimeProbability * 0.40
                            + bundleEfficiency * 0.20
                            + Math.max(0.0, order.getQuotedFee() - totalFuelProxy) / 40000.0 * 0.20
                            - distKm / 8.0 * 0.15
                            - cancelRisk * 0.10);

            fallbackPlan.setPredictedTotalMinutes(eta);
            fallbackPlan.setDriverProfit(order.getQuotedFee() - totalFuelProxy);
            fallbackPlan.setLateRisk(lateRisk);
            fallbackPlan.setOnTimeProbability(onTimeProbability);
            fallbackPlan.setCancellationRisk(cancelRisk);
            fallbackPlan.setCustomerFee(order.getQuotedFee());
            fallbackPlan.setBundleEfficiency(bundleEfficiency);
            fallbackPlan.setEndZoneOpportunity(0.5);
            fallbackPlan.setNextOrderAcquisitionScore(0.4);
            fallbackPlan.setCongestionPenalty(trafficIntensity * 0.4);
            fallbackPlan.setRepositionPenalty(0.1);
            fallbackPlan.setTotalScore(score);
            fallbackPlan.setConfidence(Math.max(0.25, 0.75 - lateRisk - cancelRisk * 0.4));

            if (!legacyFallbackTuning
                    && (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                    && (fallbackPlan.getOnTimeProbability() < 0.48
                    || fallbackPlan.getPredictedDeadheadKm() > 2.3
                    || fallbackPlan.getLateRisk() > 0.52)) {
                continue;
            }

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
                                 WeatherProfile weather, int hour,
                                 long currentTick, Instant currentTime) {

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

        recordPlanOutcome(order, driver, currentTick, actualProfit, wasLate, false);
    }

    public void onOrderCancelled(Order order, double traffic,
                                 WeatherProfile weather, int hour,
                                 long currentTick, Instant currentTime) {
        double[] cancelF = featureExtractor.cancelFeatures(
                5.0, 0.5, order.getQuotedFee(), weather, 1);
        cancelRiskModel.update(cancelF, 1.0);
        recordPlanOutcome(order, null, currentTick, 0.0, false, true);
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

    private double computeAvgWaitSimulated(List<Order> pending, Instant currentTime) {
        if (pending.isEmpty()) return 0;
        return pending.stream()
                .mapToDouble(o -> {
                    long secs = Duration.between(o.getCreatedAt(), currentTime).getSeconds();
                    return Math.max(0, secs / 60.0);
                })
                .average().orElse(3.0);
    }

    private double computeSurgeLevel(List<Order> pending) {
        return Math.min(1.0, pending.size() / 20.0);
    }

    private void registerPendingOutcome(DispatchPlan plan) {
        List<String> orderIds = plan.getOrders().stream()
                .map(Order::getId)
                .toList();
        pendingTraceOutcomes.put(plan.getTraceId(), new PendingTraceOutcome(
                new HashSet<>(orderIds),
                new HashSet<>(),
                plan.getPredictedDeadheadKm(),
                plan.getBundleEfficiency()));
    }

    private void recordPlanOutcome(Order order, Driver driver, long currentTick,
                                   double actualProfit, boolean wasLate,
                                   boolean wasCancelled) {
        String traceId = order.getDecisionTraceId();
        if (traceId == null || traceId.isBlank()) return;

        PendingTraceOutcome pending = pendingTraceOutcomes.get(traceId);
        if (pending == null) {
            double continuationActualNorm = driver != null
                    ? Math.min(1.0, driver.getAvgEarningPerHour() / 50000.0)
                    : 0.0;
            double actualUtility = PlanRanker.computeActualUtility(
                    !wasLate, actualProfit,
                    Math.max(0.1, order.getPredictedBundleFit()),
                    continuationActualNorm,
                    0.0, wasCancelled);
            decisionLog.recordOutcome(traceId, actualUtility, currentTick);
            return;
        }

        if (!pending.terminalOrderIds.add(order.getId())) {
            return;
        }

        pending.realizedProfit += actualProfit;
        pending.anyLate |= wasLate;
        pending.cancelled |= wasCancelled;
        if (driver != null) {
            pending.continuationActualNorm = Math.max(
                    pending.continuationActualNorm,
                    Math.min(1.0, driver.getAvgEarningPerHour() / 50000.0));
        }

        if (pending.terminalOrderIds.containsAll(pending.orderIds)) {
            double actualUtility = PlanRanker.computeActualUtility(
                    !pending.anyLate,
                    pending.realizedProfit,
                    Math.max(0.1, pending.bundleEfficiency),
                    pending.continuationActualNorm,
                    pending.predictedDeadheadKm,
                    pending.cancelled);
            decisionLog.recordOutcome(traceId, actualUtility, currentTick);
            pendingTraceOutcomes.remove(traceId);
        }
    }

    private GeoPoint findBestAttractionPoint(GeoPoint currentPos) {
        double bestScore = Double.NEGATIVE_INFINITY;
        GeoPoint bestPoint = null;
        for (int r = 0; r < SpatiotemporalField.ROWS; r++) {
            for (int c = 0; c < SpatiotemporalField.COLS; c++) {
                GeoPoint center = field.cellCenter(r, c);
                double dist = currentPos.distanceTo(center) / 1000.0;
                if (dist > 1.5) continue;

                double futureValue = field.getForecastDemandAt(center, 10) * 0.35
                        + field.getForecastDemandAt(center, 15) * 0.30
                        + field.getForecastDemandAt(center, 30) * 0.20
                        + field.getSpikeAt(center) * 0.15;
                double fuelPenalty = dist * 0.12;
                double congestionPenalty = field.getCongestionExposureAt(center) * 0.18
                        + field.getDriverDensityAt(center) * 0.03;
                double weatherPenalty = field.getWeatherExposureAt(center) * 0.14;
                double riskAdjustedAttraction = field.getRiskAdjustedAttractionAt(center) * 0.10;
                double score = futureValue + riskAdjustedAttraction
                        - fuelPenalty - congestionPenalty - weatherPenalty;
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
        pendingTraceOutcomes.clear();
        applyAblationConfig();
    }

    public boolean isDiagnosticLoggingEnabled() {
        return diagnosticLoggingEnabled;
    }

    public void setDiagnosticLoggingEnabled(boolean diagnosticLoggingEnabled) {
        this.diagnosticLoggingEnabled = diagnosticLoggingEnabled;
    }

    public AblationMode getAblationMode() {
        return ablationMode;
    }

    public void setAblationMode(AblationMode ablationMode) {
        this.ablationMode = ablationMode == null ? AblationMode.FULL : ablationMode;
        applyAblationConfig();
    }

    private void applyAblationConfig() {
        planGenerator.setHoldPlansEnabled(ablationMode != AblationMode.NO_HOLD);
        planGenerator.setRepositionPlansEnabled(ablationMode != AblationMode.NO_REPOSITION);
        planGenerator.setSmallBatchOnly(ablationMode == AblationMode.SMALL_BATCH_ONLY);
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

    private static final class PendingTraceOutcome {
        private final Set<String> orderIds;
        private final Set<String> terminalOrderIds;
        private final double predictedDeadheadKm;
        private final double bundleEfficiency;
        private double realizedProfit;
        private double continuationActualNorm;
        private boolean anyLate;
        private boolean cancelled;

        private PendingTraceOutcome(Set<String> orderIds,
                                    Set<String> terminalOrderIds,
                                    double predictedDeadheadKm,
                                    double bundleEfficiency) {
            this.orderIds = orderIds;
            this.terminalOrderIds = terminalOrderIds;
            this.predictedDeadheadKm = predictedDeadheadKm;
            this.bundleEfficiency = bundleEfficiency;
        }
    }
}
