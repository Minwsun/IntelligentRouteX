package com.routechain.ai;

import com.routechain.ai.model.*;
import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.graph.GraphExplanationTrace;
import com.routechain.graph.GraphFeatureNamespaces;
import com.routechain.graph.GraphShadowSnapshot;
import com.routechain.infra.DispatchFactSink;
import com.routechain.infra.EventContractCatalog;
import com.routechain.infra.FeatureStore;
import com.routechain.infra.PlatformRuntimeBootstrap;
import com.routechain.simulation.*;

import java.nio.file.Files;
import java.nio.file.Path;
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
public class OmegaDispatchAgent implements DispatchBrainAgent {

    public enum AblationMode {
        FULL,
        NO_HOLD,
        NO_REPOSITION,
        NO_CONTINUATION,
        NO_NEURAL_PRIOR,
        NO_FALLBACK_TUNING,
        SMALL_BATCH_ONLY
    }

    public enum ExecutionProfile {
        MAINLINE_REALISTIC,
        SHOWCASE_PICKUP_WAVE_8
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
    private final DispatchAgent legacyCoverageGuardrail;

    // ── Driver-centric components (NEW) ─────────────────────────────────
    private final NearbyOrderIndexer orderIndex;
    private final DriverContextBuilder contextBuilder;
    private final DriverPlanGenerator planGenerator;

    // ── Replay trainer ──────────────────────────────────────────────────
    private final ReplayTrainer replayTrainer;
    private final ModelArtifactProvider modelArtifactProvider;
    private final FeatureStore featureStore;
    private final DispatchFactSink dispatchFactSink;
    private final NeuralRoutePriorClient neuralRoutePriorClient;
    private final LLMAdvisorClient llmAdvisorClient;
    private final LLMEscalationGate llmEscalationGate;

    // ── Config ──────────────────────────────────────────────────────────
    private static final int FALLBACK_DIRECT_COUNT = 25;
    private static final int RETRAIN_INTERVAL_TICKS = 300;
    private static final int GRAPH_SHADOW_CACHE_MAX_DISPATCH_AGE = 6;
    private long lastRetrainTick = 0;
    private long dispatchSequence = 0;
    private long latestReplayRetrainLatencyMs = 0;
    private final Map<String, PendingTraceOutcome> pendingTraceOutcomes = new HashMap<>();
    private final Map<String, Integer> holdTtlByDriver = new HashMap<>();
    private boolean diagnosticLoggingEnabled = true;
    private AblationMode ablationMode = AblationMode.FULL;
    private ExecutionProfile executionProfile = ExecutionProfile.MAINLINE_REALISTIC;
    private String activeRunId = "run-unset";
    private String activeRouteLatencyMode = SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name();
    private GraphShadowSnapshot activeGraphShadowSnapshot = new GraphShadowSnapshot(
            "run-unset",
            "dispatch-live",
            "instant",
            "in-memory-shadow",
            List.of(),
            List.of(),
            List.of());
    private GraphShadowCacheEntry graphShadowCacheEntry = GraphShadowCacheEntry.empty();
    private static final double EMERGENCY_DEADHEAD_CAP_KM = 4.5;

    public OmegaDispatchAgent(List<Region> regions) {
        this.regions = regions;
        this.field = new SpatiotemporalField();
        this.featureExtractor = new FeatureExtractor();
        this.decisionLog = new DecisionLog();
        this.policySelector = new PolicySelector();
        this.horizonPlanner = new HorizonPlanner();
        this.modelArtifactProvider = PlatformRuntimeBootstrap.getModelArtifactProvider();
        this.featureStore = PlatformRuntimeBootstrap.getFeatureStore();
        this.dispatchFactSink = PlatformRuntimeBootstrap.getDispatchFactSink();
        this.neuralRoutePriorClient = new NeuralRoutePriorClient();
        this.llmAdvisorClient = PlatformRuntimeBootstrap.getLlmAdvisorClient();
        this.llmEscalationGate = PlatformRuntimeBootstrap.getLlmEscalationGate();

        this.etaModel = modelArtifactProvider.getOrDefault("eta-model", ETAModel::new);
        this.lateRiskModel = modelArtifactProvider.getOrDefault("late-risk-model", LateRiskModel::new);
        this.cancelRiskModel = modelArtifactProvider.getOrDefault("cancel-risk-model", CancelRiskModel::new);
        this.pickupDelayModel = modelArtifactProvider.getOrDefault("pickup-delay-model", PickupDelayModel::new);
        this.continuationValueModel = modelArtifactProvider.getOrDefault("continuation-value-model", ContinuationValueModel::new);
        this.planRanker = modelArtifactProvider.getOrDefault("plan-ranker-model", PlanRanker::new);
        this.uncertaintyEstimator = modelArtifactProvider.getOrDefault("uncertainty-model", UncertaintyEstimator::new);

        this.replayTrainer = new ReplayTrainer();

        // Phase 3: centralized scorer + constraint engine
        this.utilityScorer = new PlanUtilityScorer();
        this.constraintEngine = new ConstraintEngine();
        this.legacyCoverageGuardrail = new DispatchAgent(regions);

        // Driver-centric components
        this.orderIndex = new NearbyOrderIndexer();
        this.contextBuilder = new DriverContextBuilder(orderIndex, field);
        this.planGenerator = new DriverPlanGenerator();
        applyGeneratorConfig();
        PlatformRuntimeBootstrap.registerDispatchBrain(this);
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
        return dispatch(
                pendingOrders,
                availableDrivers,
                allDrivers,
                allOrders,
                simulatedHour,
                trafficIntensity,
                weather,
                currentTime,
                activeRunId);
    }

    public DispatchResult dispatch(
            List<Order> pendingOrders, List<Driver> availableDrivers,
            List<Driver> allDrivers, List<Order> allOrders,
            int simulatedHour, double trafficIntensity, WeatherProfile weather,
            Instant currentTime, String runId) {
        long dispatchStartedNanos = System.nanoTime();
        activeRunId = (runId == null || runId.isBlank()) ? "run-unset" : runId;
        dispatchSequence++;

        long tick = currentTime.toEpochMilli();
        StageTimingAccumulator stageAccumulator = new StageTimingAccumulator();

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
        String dominantServiceTier = DeliveryServiceTier
                .dominantForOrders(pendingOrders, executionProfile.name())
                .wireValue();
        if (shouldUseLegacyDispatchGuardrail(dominantServiceTier, weather)) {
            long legacyDispatchStartedNanos = System.nanoTime();
            DispatchAgent.DispatchResult legacyResult = legacyCoverageGuardrail.dispatch(
                    new ArrayList<>(pendingOrders),
                    new ArrayList<>(availableDrivers),
                    allDrivers,
                    allOrders,
                    simulatedHour,
                    trafficIntensity,
                    weather);
            for (DispatchPlan plan : legacyResult.plans()) {
                plan.setLegacyGuardrailPlan(true);
            }
            stageAccumulator.candidateGenerationMs = nanosToMillis(legacyDispatchStartedNanos);
            stageAccumulator.generatedCandidateCount = legacyResult.plans().size();
            stageAccumulator.fullyScoredCandidateCount = legacyResult.plans().size();
            return new DispatchResult(
                    legacyResult.plans(),
                    legacyResult.repositions(),
                    activePolicy.name(),
                    pendingOrders.size(),
                    availableDrivers.size(),
                    DispatchRecoveryDecomposition.empty(),
                    (System.nanoTime() - dispatchStartedNanos) / 1_000_000L,
                    List.of(),
                    List.of(),
                    stageAccumulator.toImmutable(availableDrivers.size()));
        }
        long graphShadowStartedNanos = System.nanoTime();
        GraphShadowResolution graphShadowResolution = resolveGraphShadowSnapshot(
                dominantServiceTier,
                allDrivers,
                pendingOrders);
        activeGraphShadowSnapshot = graphShadowResolution.snapshot();
        stageAccumulator.graphShadowProjectionMs = nanosToMillis(graphShadowStartedNanos);
        stageAccumulator.graphShadowCacheHit = graphShadowResolution.cacheHit();

        // ── Step 3: Build spatial order index (NEW) ─────────────────────
        long candidateGenerationStartedNanos = System.nanoTime();
        contextBuilder.rebuildIndex(pendingOrders);
        List<Driver> planningDrivers = selectPlanningDrivers(
                availableDrivers,
                pendingOrders,
                trafficIntensity,
                weather,
                currentTime);

        // ── Step 4: Driver-centric plan generation (NEW) ────────────────
        // For each eligible driver: build local context → generate plans → score
        List<DispatchPlan> allPlans = new ArrayList<>();
        Map<String, DriverDecisionContext> contextsByDriver = new HashMap<>();
        Map<String, List<DispatchPlan>> candidatePlansByDriver = new HashMap<>();
        int rejectedLate = 0, rejectedProfit = 0, rejectedDead = 0, rejectedDetour = 0;
        int rejectedMerchant = 0, rejectedLoad = 0;
        int totalCandidates = 0;
        MutableDispatchRecoveryStats recovery = new MutableDispatchRecoveryStats();
        List<Long> modelLatencySamples = new ArrayList<>();
        List<Long> neuralLatencySamples = new ArrayList<>();

        for (Driver driver : planningDrivers) {
            // Build driver's local world snapshot
            DriverDecisionContext ctx = contextBuilder.build(
                    driver, trafficIntensity, weather, simulatedHour, currentTime);
            contextsByDriver.put(driver.getId(), ctx);

            // Generate candidate plans (single, bundle, hold, reposition)
            List<DispatchPlan> generatedPlans = planGenerator.generate(
                    ctx, trafficIntensity, weather, simulatedHour);
            recovery.recordGenerated(generatedPlans);

            totalCandidates += generatedPlans.size();
            stageAccumulator.generatedCandidateCount += generatedPlans.size();
            List<DispatchPlan> driverPlans = new ArrayList<>();
            List<DispatchPlan> shortlistedForScoring = shortlistPlansForFullScoring(
                    ctx,
                    generatedPlans,
                    trafficIntensity,
                    weather);

            // Score each plan with full AI model stack
            for (DispatchPlan plan : shortlistedForScoring) {
                // Skip hold/reposition plans from AI scoring (keep prelim score)
                if (plan.getBundle().size() == 0) {
                    driverPlans.add(plan);
                    continue;
                }

                stageAccumulator.fullyScoredCandidateCount++;
                int[] rejectReasons = new int[6];
                boolean valid = scoreAndValidatePlan(
                        plan, simulatedHour, trafficIntensity,
                        weather, activePolicy, currentTime, ctx, rejectReasons,
                        stageAccumulator);
                if (plan.getModelInferenceLatencyMs() > 0L) {
                    modelLatencySamples.add(plan.getModelInferenceLatencyMs());
                }
                if (plan.getNeuralPriorLatencyMs() > 0L) {
                    neuralLatencySamples.add(plan.getNeuralPriorLatencyMs());
                }

                if (valid) {
                    driverPlans.add(plan);
                } else {
                    if (plan.getBundleSize() >= 3) {
                        recovery.waveRejectedByConstraintCount++;
                        if (rejectReasons[2] > 0) {
                            recovery.waveRejectedByDeadheadCount++;
                        }
                        if (rejectReasons[0] > 0) {
                            recovery.waveRejectedBySlaCount++;
                        }
                    }
                    rejectedLate += rejectReasons[0];
                    rejectedProfit += rejectReasons[1];
                    rejectedDead += rejectReasons[2];
                    rejectedDetour += rejectReasons[3];
                    rejectedMerchant += rejectReasons[4];
                    rejectedLoad += rejectReasons[5];
                }
            }

            List<DispatchPlan> gatedPlans = applyWaveAssemblyGate(ctx, driverPlans, recovery);
            trackHoldLifecycle(driver, gatedPlans, recovery);
            recovery.recordShortlisted(gatedPlans);
            candidatePlansByDriver.put(driver.getId(), List.copyOf(gatedPlans));
            allPlans.addAll(gatedPlans);
        }
        stageAccumulator.candidateGenerationMs = nanosToMillis(candidateGenerationStartedNanos);

        // ── Step 5: Conflict-free assignment ────────────────────────────
        DispatchOptimizer optimizer = PlatformRuntimeBootstrap.getDispatchOptimizer();
        long optimizerSolveStartedNanos = System.nanoTime();
        List<DispatchPlan> selectedPlans = new ArrayList<>(optimizer.solve(allPlans, activeRunId, tick));
        stageAccumulator.optimizerSolveMs = nanosToMillis(optimizerSolveStartedNanos);
        recovery.recordSolverSelection(selectedPlans);

        // ── Step 6: FALLBACK — nearest-driver for uncovered orders ──────
        long fallbackInjectionStartedNanos = System.nanoTime();
        int minFallbackPlans = minimumCoverageTarget(
                shortage,
                trafficIntensity,
                weather,
                pendingOrders.size(),
                availableDrivers.size());
        minFallbackPlans = applyFallbackSaturationGuard(
                selectedPlans,
                minFallbackPlans,
                trafficIntensity,
                weather);
        if (countRealAssignedPlans(selectedPlans) < minFallbackPlans
                && !availableDrivers.isEmpty()
                && allowFallbackCoverage(shortage, trafficIntensity, weather, pendingOrders.size(), availableDrivers.size())) {
            int injected = applyFallbackAssignment(selectedPlans, pendingOrders,
                    availableDrivers, trafficIntensity, weather, currentTime, minFallbackPlans);
            recovery.fallbackInjectedCount += injected;
        }
        if (shouldApplyLegacyCoverageBackfill(
                dominantServiceTier,
                weather,
                pendingOrders,
                availableDrivers,
                selectedPlans)) {
            int injected = applyFallbackAssignment(
                    selectedPlans,
                    pendingOrders,
                    availableDrivers,
                    trafficIntensity,
                    weather,
                    currentTime,
                    Math.min(pendingOrders.size(), availableDrivers.size()));
            recovery.fallbackInjectedCount += injected;
        }
        stageAccumulator.fallbackInjectionMs = nanosToMillis(fallbackInjectionStartedNanos);

        // ── Step 7: Reposition idle drivers ─────────────────────────────
        List<RepositionAgent.RepositionDecision> repositions = new ArrayList<>();
        long repositionSelectionStartedNanos = System.nanoTime();
        boolean allowGlobalReposition = ablationMode != AblationMode.NO_REPOSITION
                && !isGlobalStress(shortage, trafficIntensity, weather, pendingOrders.size(), availableDrivers.size());
        for (Driver driver : availableDrivers) {
            if (allowGlobalReposition
                    && driver.getState() == DriverState.ONLINE_IDLE
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
        stageAccumulator.repositionSelectionMs = nanosToMillis(repositionSelectionStartedNanos);
        for (DispatchPlan plan : selectedPlans) {
            recovery.recordExecuted(plan);
            if (plan.getBundle().size() > 0) {
                double[] pf = featureExtractor.planFeatures(
                        plan, field, trafficIntensity, weather);
                decisionLog.log(tick, contextFeatures, pf,
                        plan.getTotalScore(), activePolicy.name(),
                        plan.getTraceId(),
                        buildDecisionReason(plan));
                emitDecisionArtifacts(
                        tick,
                        contextFeatures,
                        pf,
                        activePolicy.name(),
                        plan,
                        contextsByDriver.get(plan.getDriver().getId()),
                        candidatePlansByDriver.get(plan.getDriver().getId()),
                        (System.nanoTime() - dispatchStartedNanos) / 1_000_000L,
                        buildDecisionReason(plan));
                registerPendingOutcome(plan);
            } else if (plan.isWaitingForThirdOrder()) {
                decisionLog.log(tick, contextFeatures, new double[15],
                        plan.getTotalScore(), activePolicy.name(),
                        plan.getTraceId(),
                        buildDecisionReason(plan));
                emitDecisionArtifacts(
                        tick,
                        contextFeatures,
                        new double[15],
                        activePolicy.name(),
                        plan,
                        contextsByDriver.get(plan.getDriver().getId()),
                        candidatePlansByDriver.get(plan.getDriver().getId()),
                        (System.nanoTime() - dispatchStartedNanos) / 1_000_000L,
                        buildDecisionReason(plan));
            }
        }

        // Diagnostic log
        if (diagnosticLoggingEnabled) {
            System.out.printf(
                    "[Omega-DC] Policy:%s Drivers:%d Pending:%d " +
                            "Candidates:%d Plans:%d→%d " +
                            "(Rej: late=%d profit=%d dead=%d detour=%d merchant=%d load=%d)%n",
                    activePolicy.name(), availableDrivers.size(),
                    pendingOrders.size(), totalCandidates,
                    allPlans.size(), selectedPlans.size(),
                    rejectedLate, rejectedProfit, rejectedDead, rejectedDetour,
                    rejectedMerchant, rejectedLoad);
            printDecisionHighlights(selectedPlans);
        }

        return new DispatchResult(selectedPlans, repositions,
                activePolicy.name(), pendingOrders.size(),
                availableDrivers.size(),
                recovery.toImmutable(),
                (System.nanoTime() - dispatchStartedNanos) / 1_000_000L,
                List.copyOf(modelLatencySamples),
                List.copyOf(neuralLatencySamples),
                stageAccumulator.toImmutable(planningDrivers.size()));
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
            DriverDecisionContext ctx,
            int[] rejectReasons,
            StageTimingAccumulator stageAccumulator) {

        Driver driver = plan.getDriver();
        DispatchPlan.Bundle bundle = plan.getBundle();
        List<DispatchPlan.Stop> seq = plan.getSequence();
        plan.setRunId(activeRunId);
        long inferenceStartedNanos = System.nanoTime();

        if (seq.isEmpty()) {
            plan.setModelInferenceLatencyMs(0L);
            return false;
        }

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
        DeliveryServiceTier serviceTier = DeliveryServiceTier.dominantForOrders(
                plan.getOrders(),
                executionProfile.name());
        plan.setServiceTier(serviceTier.wireValue());
        GeoPoint pickupPoint = seq.get(0).location();
        double merchantPrepForecastMinutes = field.getMerchantPrepForecastMinutesAt(pickupPoint, 10);
        double merchantPrepRisk = computeMerchantPrepRisk(plan, merchantPrepForecastMinutes, currentTime);
        double borrowSuccessProbability = field.getBorrowSuccessProbabilityAt(pickupPoint, 10);
        plan.setMerchantPrepRiskScore(merchantPrepRisk);
        plan.setBorrowSuccessProbability(borrowSuccessProbability);
        double endWeatherExposure = field.getWeatherExposureAt(endPoint);
        double endCongestionExposure = field.getCongestionExposureAt(endPoint);
        StressRegime effectiveRegime = elevateStressRegime(
                ctx.stressRegime(),
                traffic,
                weather,
                pickupWeatherExposure,
                pickupCongestionExposure,
                endWeatherExposure,
                endCongestionExposure,
                Math.max(field.getShortageAt(seq.get(0).location()), field.getShortageAt(endPoint)));
        syncThreeOrderPolicyFlags(plan, ctx, effectiveRegime);
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
        plan.setContinuationValueScore(adjustedContinuation);
        plan.setEndZoneOpportunityScore(nextOrderScore);
        plan.setTrafficExposureScore(endCongestionExposure);
        plan.setWeatherExposureScore(endWeatherExposure);
        plan.setPostDropDemandProbability(field.getPostDropOpportunityAt(endPoint, 10));
        plan.setTrafficForecastAbsError(ctx == null
                ? 0.0
                : Math.abs(ctx.localTrafficForecast10m() - traffic));
        plan.setWeatherForecastHitRate(ctx == null
                ? 1.0
                : clamp01(1.0 - Math.abs(ctx.localWeatherForecast10m() - weatherSeverity(weather))));

        double congestionPenalty = Math.min(1.0,
                traffic * 0.35
                        + endCongestionExposure * 0.45
                        + endWeatherExposure * 0.20);
        plan.setCongestionPenalty(congestionPenalty);
        plan.setRepositionPenalty(deadheadKm > 3.0 ? 0.3 + endWeatherExposure * 0.08 : 0.1);
        applySoftLandingAdjustments(plan, ctx, endPoint,
                adjustedContinuation, nextOrderScore,
                endWeatherExposure, endCongestionExposure);

        // ── Hard constraints (delegated to ConstraintEngine) ──────────
        // Use a dynamic batch cap of 5 (default max) — the real cap was already
        // applied during plan generation in DriverPlanGenerator
        int dynamicBatchCap = executionProfile == ExecutionProfile.SHOWCASE_PICKUP_WAVE_8 ? 8 : 4;
        if (!constraintEngine.validate(plan, dynamicBatchCap, rejectReasons, effectiveRegime)) {
            plan.setModelInferenceLatencyMs((System.nanoTime() - inferenceStartedNanos) / 1_000_000L);
            return false;
        }
        long graphAffinityStartedNanos = System.nanoTime();
        GraphExplanationTrace graphTrace = PlatformRuntimeBootstrap.getGraphAffinityScorer().scorePlan(
                activeRunId,
                activeGraphShadowSnapshot,
                ctx,
                plan,
                field,
                weather,
                traffic);
        if (stageAccumulator != null) {
            stageAccumulator.graphAffinityScoringNanos += Math.max(0L, System.nanoTime() - graphAffinityStartedNanos);
        }
        plan.setGraphAffinityScore(graphTrace.graphAffinityScore());
        plan.setGraphExplanationTrace(graphTrace);
        featureStore.put(
                GraphFeatureNamespaces.GRAPH_FEATURES,
                "run:" + activeRunId + ":driver:" + driver.getId() + ":bundle:" + bundle.bundleId(),
                Map.of(
                        "serviceTier", plan.getServiceTier(),
                        "graphAffinityScore", graphTrace.graphAffinityScore(),
                        "topologyScore", graphTrace.topologyScore(),
                        "bundleCompatibilityScore", graphTrace.bundleCompatibilityScore(),
                        "futureCellScore", graphTrace.futureCellScore(),
                        "congestionPropagationScore", graphTrace.congestionPropagationScore(),
                        "sourceCellId", graphTrace.sourceCellId(),
                        "targetCellId", graphTrace.targetCellId()));
        double executionScore = computeExecutionScore(plan, effectiveRegime, weather);
        double continuationScore = computeContinuationScore(plan, effectiveRegime);
        double coverageScore = computeCoverageScore(plan, ctx, effectiveRegime);
        double futureScore = clamp01(continuationScore * 0.72 + coverageScore * 0.28);
        plan.setExecutionScore(executionScore);
        plan.setContinuationScore(continuationScore);
        plan.setCoverageScore(coverageScore);
        plan.setFutureScore(futureScore);
        NeuralRoutePrior neuralPrior = ablationMode == AblationMode.NO_NEURAL_PRIOR
                ? NeuralRoutePrior.fallback(plan.getDriver() == null ? "unknown" : plan.getDriver().getId(),
                "ablation-no-neural-prior")
                : neuralRoutePriorClient.resolve(
                        activeRunId,
                        executionProfile.name(),
                        ctx,
                        plan,
                        weather,
                        traffic);
        plan.setNeuralPriorScore(neuralPrior.priorScore());
        plan.setNeuralPriorVersion(neuralPrior.modelVersion());
        plan.setNeuralPriorFreshnessMs(neuralPrior.freshnessMs());
        plan.setNeuralPriorUsed(neuralPrior.used());
        plan.setNeuralPriorLatencyMs(neuralPrior.latencyMs());
        plan.setNeuralPriorBackend(neuralPrior.backend());
        plan.setNeuralPriorFallbackReason(neuralPrior.fallbackReason());
        plan.setRoutePriorScore(neuralPrior.priorScore());
        if (!passesExecutionGate(plan, effectiveRegime, weather)) {
            if (plan.getPredictedDeadheadKm() > deadheadBudgetFor(plan, effectiveRegime, weather)) {
                rejectReasons[2]++;
            }
            if ((weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)
                    && plan.getOnTimeProbability() < minOnTimeForGate(plan, effectiveRegime, weather)) {
                rejectReasons[0]++;
            }
            plan.setModelInferenceLatencyMs((System.nanoTime() - inferenceStartedNanos) / 1_000_000L);
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
        double utilityScore = utilityScorer.score(plan, effectiveRegime);
        double[] blend = adaptiveBlendWeights(plan, effectiveRegime, weather);
        double neuralPriorComponent = plan.isNeuralPriorUsed()
                ? clamp01(plan.getNeuralPriorScore())
                * (0.04 + 0.04 * clamp01(neuralPrior.confidence()))
                : 0.0;
        double landingContinuityBonus = plan.getLastDropLandingScore() * 0.05
                + plan.getPostDropDemandProbability() * 0.04
                + Math.max(0.0, 1.0 - plan.getExpectedPostCompletionEmptyKm() / 3.0) * 0.03;
        double continuityRiskPenalty = plan.getBorrowedDependencyScore() * 0.08
                + plan.getEmptyRiskAfter() * 0.06
                + Math.max(0.0, plan.getExpectedPostCompletionEmptyKm() - 1.5) * 0.03;
        double finalScore = robustScore * blend[0]
                + executionScore * blend[1]
                + continuationScore * blend[2]
                + coverageScore * 0.04
                + utilityScore * blend[3]
                + neuralPriorComponent
                + plan.getGraphAffinityScore() * 0.05
                + landingContinuityBonus
                + resilientExecutionBonus(plan, effectiveRegime)
                - continuityRiskPenalty
                - executionStabilityPenalty(plan, effectiveRegime, weather, pred.confidence());

        populateSelectionSignals(plan, ctx, effectiveRegime);
        plan.setTotalScore(finalScore);
        plan.setConfidence(pred.confidence());
        plan.setModelInferenceLatencyMs((System.nanoTime() - inferenceStartedNanos) / 1_000_000L);
        plan.setTraceId("OMEGA-DC-" + System.nanoTime()
                + "-" + driver.getId());

        return true;
    }

    /**
     * Fallback assignment for uncovered orders using nearest-driver.
     * Guarantees coverage even when AI pipeline produces few valid plans.
     */
    private int applyFallbackAssignment(
            List<DispatchPlan> selectedPlans, List<Order> pendingOrders,
            List<Driver> availableDrivers,
            double trafficIntensity, WeatherProfile weather, Instant currentTime,
            int targetCoverageUnits) {

        Set<String> assignedDriverIds = new HashSet<>();
        Set<String> assignedOrderIds = new HashSet<>();
        for (DispatchPlan p : selectedPlans) {
            if (!p.getOrders().isEmpty()) {
                assignedDriverIds.add(p.getDriver().getId());
            }
            for (Order o : p.getOrders()) {
                assignedOrderIds.add(o.getId());
            }
        }

        int neededCoverageUnits = Math.max(0, targetCoverageUnits - countRealAssignedPlans(selectedPlans));
        if (neededCoverageUnits <= 0) {
            return 0;
        }
        Comparator<Order> fallbackComparator = Comparator
                .comparingDouble((Order o) -> computeFallbackRecoveryPriority(o, currentTime, weather))
                .reversed()
                .thenComparing(Order::getCreatedAt);
        int fallbackOrderLimit = adaptiveFallbackOrderLimit(
                weather,
                trafficIntensity,
                availableDrivers.size(),
                neededCoverageUnits);

        List<Order> unassigned = pendingOrders.stream()
                .filter(o -> !assignedOrderIds.contains(o.getId()))
                .sorted(fallbackComparator)
                .limit(fallbackOrderLimit)
                .toList();

        List<Driver> freeDrivers = new ArrayList<>(
                availableDrivers.stream()
                        .filter(d -> !assignedDriverIds.contains(d.getId()))
                        .toList());
        int injectCap = Math.min(
                fallbackOrderLimit,
                weather == WeatherProfile.STORM
                        ? neededCoverageUnits + 1
                        : neededCoverageUnits);
        int injectedCount = 0;

        for (Order order : unassigned) {
            if (freeDrivers.isEmpty() || injectedCount >= injectCap) {
                break;
            }
            DeliveryServiceTier serviceTier = DeliveryServiceTier.classify(order);
            boolean urgentOrder = isUrgentFallbackOrder(order, currentTime, weather);
            double pickupReadySlackMinutes = order.getPredictedReadyAt() != null
                    ? Duration.between(currentTime, order.getPredictedReadyAt()).toSeconds() / 60.0
                    : 0.0;
            double optimisticLocalCap = switch (weather) {
                case CLEAR -> adjustDeadheadBudgetForServiceTier(serviceTier.wireValue(), 2.7);
                case LIGHT_RAIN -> adjustDeadheadBudgetForServiceTier(serviceTier.wireValue(), 2.5);
                case HEAVY_RAIN -> adjustDeadheadBudgetForServiceTier(serviceTier.wireValue(), 2.8);
                case STORM -> adjustDeadheadBudgetForServiceTier(serviceTier.wireValue(), 2.2);
            };
            Driver preferredLocalDriver = selectLocalSameZoneFallbackDriver(
                    order,
                    freeDrivers,
                    optimisticLocalCap);
            Driver nearest = preferredLocalDriver != null
                    ? preferredLocalDriver
                    : selectCoverageAwareFallbackDriver(
                    order,
                    freeDrivers,
                    trafficIntensity,
                    weather);
            if (nearest == null) {
                continue;
            }

            double distKm = nearest.getCurrentLocation().distanceTo(
                    order.getPickupPoint()) / 1000.0;
            boolean sameZoneDriver = nearest.getRegionId().equals(order.getPickupRegionId());
            boolean minimumCoverageFallback = shouldForceMinimumFallbackCoverage(
                    selectedPlans,
                    pendingOrders,
                    freeDrivers,
                    order,
                    serviceTier,
                    distKm,
                    weather,
                    pickupReadySlackMinutes);
            boolean blockBorrowedFallback = executionProfile == ExecutionProfile.MAINLINE_REALISTIC
                    && (weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)
                    && !sameZoneDriver
                    && !urgentOrder
                    && !minimumCoverageFallback
                    && pendingOrders.size() <= Math.max(8, availableDrivers.size() + 1);
            if (blockBorrowedFallback) {
                continue;
            }
            if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                    && !urgentOrder
                    && !minimumCoverageFallback) {
                continue;
            }
            boolean legacyFallbackTuning = legacyFallbackMode(trafficIntensity, weather);
            double baseFallbackDeadheadKm = legacyFallbackTuning
                    ? 6.0 - trafficIntensity * 1.5
                    : switch (weather) {
                        case CLEAR -> 3.2 - trafficIntensity * 0.8;
                        case LIGHT_RAIN -> 3.0 - trafficIntensity * 0.7;
                        case HEAVY_RAIN -> 2.8 - trafficIntensity * 0.5;
                        case STORM -> 2.3 - trafficIntensity * 0.3;
                    };
            double maxFallbackDeadheadKm = adjustDeadheadBudgetForServiceTier(
                    serviceTier.wireValue(),
                    baseFallbackDeadheadKm - (sameZoneDriver ? 0.0 : 0.2));
            double cleanLocalCap = switch (weather) {
                case CLEAR -> adjustDeadheadBudgetForServiceTier(
                        serviceTier.wireValue(),
                        sameZoneDriver ? 2.7 : 2.3);
                case LIGHT_RAIN -> adjustDeadheadBudgetForServiceTier(
                        serviceTier.wireValue(),
                        sameZoneDriver ? 2.5 : 2.2);
                case HEAVY_RAIN -> adjustDeadheadBudgetForServiceTier(
                        serviceTier.wireValue(),
                        sameZoneDriver ? 2.8 : 2.4);
                case STORM -> adjustDeadheadBudgetForServiceTier(
                        serviceTier.wireValue(),
                        sameZoneDriver ? 2.2 : 2.0);
            };
            double urgentCapBoost = urgentOrder ? 0.35 : 0.0;
            if (!urgentOrder && distKm > cleanLocalCap) {
                continue;
            }
            if (distKm > Math.max(cleanLocalCap + urgentCapBoost, maxFallbackDeadheadKm + urgentCapBoost)) {
                continue;
            }
            StressRegime fallbackRegime = deriveFallbackStressRegime(order, trafficIntensity, weather);
            SequenceOptimizer seqOptimizer = new SequenceOptimizer(
                    trafficIntensity,
                    weather,
                    executionProfile == ExecutionProfile.SHOWCASE_PICKUP_WAVE_8,
                    fallbackRegime);

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
            fallbackPlan.setSelectionBucket(sameZoneDriver
                    ? SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                    : SelectionBucket.BORROWED_COVERAGE);
            fallbackPlan.setRunId(activeRunId);
            fallbackPlan.setServiceTier(serviceTier.wireValue());
            fallbackPlan.setBorrowedDependencyScore(sameZoneDriver
                    ? (distKm <= 1.2 ? 0.05 : 0.14)
                    : 0.38);

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
                case HEAVY_RAIN -> 0.10;
                case STORM -> 0.18;
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
            double baseScore = legacyFallbackTuning
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
                            - cancelRisk * 0.10
                            - Math.max(0.0, -lateSlack) / 12.0 * 0.16
                            - Math.max(0.0, pickupReadySlackMinutes - 1.0) / 6.0 * 0.08);

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
            populateFallbackLandingMetrics(fallbackPlan, order, weather, trafficIntensity);
            fallbackPlan.setCoverageQuality(sameZoneDriver ? 0.74 : 0.48);
            fallbackPlan.setReplacementDepth(clamp01((freeDrivers.size() - 1) / 3.0));
            fallbackPlan.setBorrowSuccessProbability(sameZoneDriver
                    ? 0.0
                    : clamp01(1.0 - distKm / Math.max(2.5, maxFallbackDeadheadKm + 0.6)));
            fallbackPlan.setEmptyRiskAfter(clamp01(1.0 - fallbackPlan.getPostDropDemandProbability()));
            fallbackPlan.setExecutionScore(computeExecutionScore(fallbackPlan, fallbackRegime, weather));
            fallbackPlan.setContinuationScore(computeContinuationScore(fallbackPlan, fallbackRegime));
            fallbackPlan.setCoverageScore(computeCoverageScore(fallbackPlan, null, fallbackRegime));
            fallbackPlan.setFutureScore(computeFutureScore(fallbackPlan, fallbackRegime));
            populateSelectionSignals(fallbackPlan, null, fallbackRegime);
            double landingQualityBonus = fallbackPlan.getLastDropLandingScore() * 0.10
                    + fallbackPlan.getPostDropDemandProbability() * 0.08
                    + Math.max(0.0, 1.0 - fallbackPlan.getExpectedPostCompletionEmptyKm() / 2.8) * 0.06;
            double borrowedPenalty = fallbackPlan.getBorrowedDependencyScore() * 0.10
                    + fallbackPlan.getEmptyRiskAfter() * 0.08
                    + Math.max(0.0, fallbackPlan.getExpectedPostCompletionEmptyKm() - 1.4) * 0.04;
            fallbackPlan.setTotalScore(Math.max(0.05, baseScore + landingQualityBonus - borrowedPenalty));
            fallbackPlan.setConfidence(Math.max(0.25, 0.75 - lateRisk - cancelRisk * 0.4));

            if (!legacyFallbackTuning
                    && !minimumCoverageFallback
                    && !isFallbackSafe(
                    fallbackRegime,
                    weather,
                    lateSlack,
                    fallbackPlan.getOnTimeProbability(),
                    fallbackPlan.getPredictedDeadheadKm(),
                    order.getPickupDelayHazard(),
                    pickupReadySlackMinutes,
                    fallbackPlan.getLateRisk())) {
                continue;
            }
            if (!passesExecutionGate(fallbackPlan, fallbackRegime, weather)) {
                continue;
            }

            selectedPlans.add(fallbackPlan);
            injectedCount++;
            freeDrivers.remove(nearest);
            assignedDriverIds.add(nearest.getId());
            assignedOrderIds.add(order.getId());
        }
        return injectedCount;
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

        if (shouldRunReplayRetrain() && currentTick - lastRetrainTick >= RETRAIN_INTERVAL_TICKS) {
            lastRetrainTick = currentTick;
            long retrainStartedNanos = System.nanoTime();
            replayTrainer.retrain(decisionLog, planRanker, uncertaintyEstimator, policySelector);
            latestReplayRetrainLatencyMs = nanosToMillis(retrainStartedNanos);
        }
    }

    private boolean shouldRunReplayRetrain() {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return true;
        }
        return false;
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
            recordOutcomeFact(new DispatchFactSink.OutcomeFact(
                    traceId,
                    activeRunId,
                    currentTick,
                    actualUtility,
                    wasCancelled,
                    wasLate,
                    actualProfit,
                    0.0,
                    Math.max(0.1, order.getPredictedBundleFit()),
                    continuationActualNorm,
                    Instant.now()));
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
            recordOutcomeFact(new DispatchFactSink.OutcomeFact(
                    traceId,
                    activeRunId,
                    currentTick,
                    actualUtility,
                    pending.cancelled,
                    pending.anyLate,
                    pending.realizedProfit,
                    pending.predictedDeadheadKm,
                    pending.bundleEfficiency,
                    pending.continuationActualNorm,
                    Instant.now()));
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

    private void applySoftLandingAdjustments(DispatchPlan plan,
                                             DriverDecisionContext ctx,
                                             GeoPoint endPoint,
                                             double adjustedContinuation,
                                             double nextOrderScore,
                                             double endWeatherExposure,
                                             double endCongestionExposure) {
        double baseCorridorScore = plan.getDeliveryCorridorScore() > 0
                ? plan.getDeliveryCorridorScore()
                : computeCorridorScoreFromContext(ctx, endPoint);
        double baseLandingScore = plan.getLastDropLandingScore() > 0
                ? plan.getLastDropLandingScore()
                : computeLandingScoreFromContext(ctx, endPoint);
        double routeRiskPenalty = endCongestionExposure * 0.10 + endWeatherExposure * 0.08;
        double refinedCorridor = clamp01(
                baseCorridorScore * 0.70
                        + plan.getRemainingDropProximityScore() * 0.15
                        + (1.0 - plan.getDeliveryZigZagPenalty()) * 0.10
                        + clamp01(ctx.deliveryDemandGradient() / 2.5) * 0.05
                        - routeRiskPenalty);
        double refinedLanding = clamp01(
                baseLandingScore * 0.50
                        + adjustedContinuation * 0.25
                        + nextOrderScore * 0.20
                        + refinedCorridor * 0.10
                        + clamp01(ctx.localPostDropOpportunity()) * 0.08
                        - ctx.endZoneIdleRisk() * 0.10
                        - Math.min(0.05, ctx.localMerchantPrepForecast10m() / 40.0)
                        - routeRiskPenalty);
        double expectedEmptyKm = plan.getExpectedPostCompletionEmptyKm() > 0
                ? plan.getExpectedPostCompletionEmptyKm()
                : computeExpectedEmptyKmFromContext(ctx, endPoint);
        expectedEmptyKm = Math.max(0.1,
                expectedEmptyKm * (1.0 + endCongestionExposure * 0.12 + endWeatherExposure * 0.08));
        double expectedNextIdleMinutes = plan.getExpectedNextOrderIdleMinutes() > 0
                ? plan.getExpectedNextOrderIdleMinutes()
                : Math.max(0.5,
                ctx.estimatedIdleMinutes() * (1.0 - refinedLanding * 0.55)
                        + expectedEmptyKm * 0.80
                        + ctx.endZoneIdleRisk() * 1.8
                        - ctx.deliveryDemandGradient() * 0.55
                        - ctx.localPostDropOpportunity() * 1.8
                        + ctx.localEmptyZoneRisk() * 0.7);
        double zigZagPenalty = clamp01(plan.getDeliveryZigZagPenalty());
        double remainingDropProximity = clamp01(plan.getRemainingDropProximityScore());
        if (remainingDropProximity <= 0) {
            remainingDropProximity = computeFallbackRemainingDropProximity(plan);
        }

        plan.setRemainingDropProximityScore(remainingDropProximity);
        plan.setDeliveryCorridorScore(refinedCorridor);
        plan.setLastDropLandingScore(refinedLanding);
        plan.setExpectedPostCompletionEmptyKm(expectedEmptyKm);
        plan.setExpectedNextOrderIdleMinutes(expectedNextIdleMinutes);
        plan.setDeliveryZigZagPenalty(zigZagPenalty);
        plan.setPostDropDemandProbability(Math.max(
                plan.getPostDropDemandProbability(),
                field.getPostDropOpportunityAt(endPoint, 10)));
        plan.setRepositionPenalty(Math.max(plan.getRepositionPenalty(),
                Math.min(0.45, expectedEmptyKm / 6.0 * 0.22 + ctx.endZoneIdleRisk() * 0.08)));
        plan.setCongestionPenalty(Math.max(plan.getCongestionPenalty(),
                clamp01(plan.getCongestionPenalty() + zigZagPenalty * 0.08)));
    }

    private void populateFallbackLandingMetrics(DispatchPlan fallbackPlan,
                                                Order order,
                                                WeatherProfile weather,
                                                double trafficIntensity) {
        GeoPoint endPoint = order.getDropoffPoint();
        double demandSignal = field.getForecastDemandAt(endPoint, 10) * 0.45
                + field.getForecastDemandAt(endPoint, 15) * 0.35
                + field.getForecastDemandAt(endPoint, 30) * 0.20;
        double postDropOpportunity = field.getPostDropOpportunityAt(endPoint, 10);
        double weatherExposure = field.getWeatherExposureAt(endPoint);
        double congestionExposure = field.getCongestionExposureAt(endPoint);
        double landingScore = clamp01(
                postDropOpportunity * 0.30
                        + Math.min(1.0, demandSignal / 2.5) * 0.30
                        + (1.0 - congestionExposure) * 0.20
                        + (1.0 - weatherExposure) * 0.15
                        + field.getShortageAt(endPoint) * 0.10);
        GeoPoint bestZone = findBestAttractionPoint(endPoint);
        double expectedEmptyKm = bestZone == null ? 1.0 : endPoint.distanceTo(bestZone) / 1000.0;
        double corridorScore = clamp01(
                0.45
                        + Math.min(1.0, demandSignal / 3.0) * 0.20
                        + (1.0 - congestionExposure) * 0.20
                        + (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM ? -0.05 : 0.0)
                        - trafficIntensity * 0.05);
        fallbackPlan.setRemainingDropProximityScore(1.0);
        fallbackPlan.setDeliveryCorridorScore(corridorScore);
        fallbackPlan.setLastDropLandingScore(landingScore);
        fallbackPlan.setExpectedPostCompletionEmptyKm(Math.max(0.1, expectedEmptyKm));
        fallbackPlan.setExpectedNextOrderIdleMinutes(Math.max(0.8,
                2.5 + expectedEmptyKm * 0.9 + weatherExposure * 1.2 + congestionExposure));
        fallbackPlan.setContinuationValueScore(landingScore);
        fallbackPlan.setEndZoneOpportunityScore(postDropOpportunity);
        fallbackPlan.setPostDropDemandProbability(postDropOpportunity);
        fallbackPlan.setTrafficExposureScore(congestionExposure);
        fallbackPlan.setWeatherExposureScore(weatherExposure);
        fallbackPlan.setDeliveryZigZagPenalty(0.0);
    }

    private double computeCorridorScoreFromContext(DriverDecisionContext ctx, GeoPoint endPoint) {
        if (ctx.dropCorridorCandidates().isEmpty()) {
            return clamp01(0.45 + ctx.deliveryDemandGradient() * 0.08 - ctx.endZoneIdleRisk() * 0.08);
        }
        double best = 0.0;
        for (DriverDecisionContext.DropCorridorCandidate candidate : ctx.dropCorridorCandidates()) {
            double distKm = endPoint.distanceTo(candidate.anchorPoint()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.4);
            double score = candidate.corridorScore() * 0.65
                    + proximity * 0.20
                    + Math.min(1.0, candidate.demandSignal() / 2.5) * 0.10
                    + (1.0 - candidate.congestionExposure()) * 0.05;
            best = Math.max(best, score);
        }
        return clamp01(best);
    }

    private double computeLandingScoreFromContext(DriverDecisionContext ctx, GeoPoint endPoint) {
        if (ctx.endZoneCandidates().isEmpty()) {
            return clamp01(0.45 + ctx.deliveryDemandGradient() * 0.10 - ctx.endZoneIdleRisk() * 0.12);
        }
        double best = 0.0;
        for (DriverDecisionContext.EndZoneCandidate candidate : ctx.endZoneCandidates()) {
            double distKm = endPoint.distanceTo(candidate.position()) / 1000.0;
            double proximity = 1.0 / (1.0 + distKm / 1.2);
            double score = candidate.attractionScore() * 0.60
                    + proximity * 0.25
                    + (1.0 - candidate.corridorExposure()) * 0.10
                    + (1.0 - candidate.weatherExposure()) * 0.05;
            best = Math.max(best, score);
        }
        return clamp01(best / 1.4);
    }

    private double computeExpectedEmptyKmFromContext(DriverDecisionContext ctx, GeoPoint endPoint) {
        if (ctx.endZoneCandidates().isEmpty()) {
            return 1.4;
        }
        double bestWeighted = Double.MAX_VALUE;
        for (DriverDecisionContext.EndZoneCandidate candidate : ctx.endZoneCandidates()) {
            double distKm = endPoint.distanceTo(candidate.position()) / 1000.0;
            bestWeighted = Math.min(bestWeighted, distKm / Math.max(0.35, candidate.attractionScore()));
        }
        return bestWeighted == Double.MAX_VALUE ? 1.4 : Math.max(0.1, bestWeighted);
    }

    private double computeFallbackRemainingDropProximity(DispatchPlan plan) {
        List<DispatchPlan.Stop> drops = plan.getSequence().stream()
                .filter(stop -> stop.type() == DispatchPlan.Stop.StopType.DROPOFF)
                .toList();
        if (drops.size() <= 1) {
            return 1.0;
        }

        double total = 0.0;
        int count = 0;
        for (int i = 0; i < drops.size() - 1; i++) {
            double lat = 0.0;
            double lng = 0.0;
            int remaining = 0;
            for (int j = i + 1; j < drops.size(); j++) {
                lat += drops.get(j).location().lat();
                lng += drops.get(j).location().lng();
                remaining++;
            }
            GeoPoint centroid = new GeoPoint(lat / remaining, lng / remaining);
            double distKm = drops.get(i).location().distanceTo(centroid) / 1000.0;
            total += clamp01(1.0 / (1.0 + distKm / 1.6));
            count++;
        }
        return count > 0 ? total / count : 0.6;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double weatherSeverity(WeatherProfile weather) {
        if (weather == null) {
            return 0.0;
        }
        return switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.35;
            case HEAVY_RAIN -> 0.75;
            case STORM -> 1.0;
        };
    }

    private boolean isBundleOffline(ModelBundleManifest bundle) {
        if (bundle == null || bundle.onnxPath() == null || bundle.onnxPath().isBlank()) {
            return true;
        }
        if (bundle.onnxPath().startsWith("offline://")) {
            return true;
        }
        try {
            return !Files.exists(Path.of(bundle.onnxPath()));
        } catch (Exception ignored) {
            return true;
        }
    }

    private int countRealAssignedPlans(List<DispatchPlan> plans) {
        return plans.stream()
                .mapToInt(plan -> plan.getOrders().isEmpty() ? 0 : 1)
                .sum();
    }

    private List<Driver> selectPlanningDrivers(List<Driver> availableDrivers,
                                               List<Order> pendingOrders,
                                               double trafficIntensity,
                                               WeatherProfile weather,
                                               Instant currentTime) {
        if (availableDrivers == null || availableDrivers.isEmpty()) {
            return List.of();
        }
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC
                || weather == WeatherProfile.HEAVY_RAIN
                || weather == WeatherProfile.STORM
                || availableDrivers.size() <= 12) {
            return availableDrivers;
        }
        int limit = planningDriverLimit(
                availableDrivers.size(),
                pendingOrders == null ? 0 : pendingOrders.size(),
                trafficIntensity,
                weather);
        if (limit >= availableDrivers.size()) {
            return availableDrivers;
        }
        List<Order> urgentOrders = pendingOrders == null
                ? List.of()
                : pendingOrders.stream()
                .sorted(Comparator
                        .comparingDouble((Order order) -> fallbackPlanningUrgency(order, currentTime))
                        .reversed()
                        .thenComparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Order::getId))
                .limit(6)
                .toList();
        LinkedHashMap<String, Driver> selected = new LinkedHashMap<>();
        for (Order order : urgentOrders) {
            Driver regionalDriver = availableDrivers.stream()
                    .filter(Driver::isAvailable)
                    .filter(driver -> Objects.equals(driver.getRegionId(), order.getPickupRegionId()))
                    .min(Comparator
                            .comparingDouble((Driver driver) -> driver.getCurrentLocation()
                                    .distanceTo(order.getPickupPoint()))
                            .thenComparing(Driver::getId))
                    .orElse(null);
            if (regionalDriver != null) {
                selected.putIfAbsent(regionalDriver.getId(), regionalDriver);
            }
            if (selected.size() >= limit) {
                return new ArrayList<>(selected.values());
            }
        }
        availableDrivers.stream()
                .sorted(Comparator
                        .comparingDouble((Driver driver) -> planningDriverPriority(driver, urgentOrders))
                        .reversed()
                        .thenComparing(Driver::getId))
                .forEach(driver -> {
                    if (selected.size() < limit) {
                        selected.putIfAbsent(driver.getId(), driver);
                    }
                });
        return new ArrayList<>(selected.values());
    }

    private int planningDriverLimit(int availableDriverCount,
                                    int pendingOrderCount,
                                    double trafficIntensity,
                                    WeatherProfile weather) {
        int limit = switch (weather) {
            case CLEAR -> Math.max(8, Math.min(12, pendingOrderCount + 3));
            case LIGHT_RAIN -> Math.max(9, Math.min(14, pendingOrderCount + 4));
            case HEAVY_RAIN -> Math.max(12, Math.min(availableDriverCount, pendingOrderCount + 6));
            case STORM -> availableDriverCount;
        };
        if (trafficIntensity >= 0.50) {
            limit += 1;
        }
        return Math.min(availableDriverCount, limit);
    }

    private double fallbackPlanningUrgency(Order order, Instant currentTime) {
        if (order == null) {
            return 0.0;
        }
        double ageMinutes = order.getCreatedAt() == null
                ? 0.0
                : Math.max(0.0, Duration.between(order.getCreatedAt(), currentTime).toSeconds() / 60.0);
        double readySlackMinutes = order.getPredictedReadyAt() == null
                ? 0.0
                : Math.max(0.0, Duration.between(currentTime, order.getPredictedReadyAt()).toSeconds() / 60.0);
        return clamp01(order.getPredictedLateRisk()) * 0.42
                + clamp01(order.getPickupDelayHazard()) * 0.24
                + clamp01(order.getCancellationRisk()) * 0.10
                + Math.min(1.0, ageMinutes / 15.0) * 0.14
                + Math.max(0.0, 1.0 - readySlackMinutes / 8.0) * 0.10;
    }

    private double planningDriverPriority(Driver driver, List<Order> urgentOrders) {
        if (driver == null) {
            return Double.NEGATIVE_INFINITY;
        }
        if (urgentOrders == null || urgentOrders.isEmpty()) {
            return driver.isAvailable() ? 1.0 : 0.0;
        }
        double bestOrderFit = urgentOrders.stream()
                .mapToDouble(order -> {
                    double pickupKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
                    double proximity = 1.0 / (1.0 + pickupKm / 0.9);
                    double sameZone = Objects.equals(driver.getRegionId(), order.getPickupRegionId()) ? 0.18 : 0.0;
                    return proximity * 0.72
                            + sameZone
                            + clamp01(order.getPredictedLateRisk()) * 0.08
                            + clamp01(order.getPickupDelayHazard()) * 0.06;
                })
                .max()
                .orElse(0.0);
        double augmentBonus = driver.isPrePickupAugmentable() ? 0.10 : 0.0;
        double activeLoadPenalty = Math.min(0.18, driver.getCurrentOrderCount() * 0.06);
        return bestOrderFit + augmentBonus - activeLoadPenalty;
    }

    private int quantizeCount(int value, int bucketSize) {
        if (bucketSize <= 1) {
            return Math.max(0, value);
        }
        return Math.max(0, (int) Math.round(value / (double) bucketSize) * bucketSize);
    }

    private long quantizePercent(double value, int bucketPercent) {
        if (bucketPercent <= 1) {
            return Math.round(clamp01(value) * 100.0);
        }
        return Math.round(Math.round(clamp01(value) * 100.0 / bucketPercent) * bucketPercent);
    }

    private String bucketOrderLoad(int currentOrderCount) {
        if (currentOrderCount <= 0) {
            return "0";
        }
        if (currentOrderCount == 1) {
            return "1";
        }
        return "2+";
    }

    private GraphShadowResolution resolveGraphShadowSnapshot(String dominantServiceTier,
                                                             List<Driver> allDrivers,
                                                             List<Order> pendingOrders) {
        String signature = buildGraphShadowCacheSignature(
                dominantServiceTier,
                allDrivers,
                pendingOrders);
        boolean cacheFresh = graphShadowCacheEntry.matches(activeRunId, dominantServiceTier, signature, dispatchSequence);
        if (cacheFresh) {
            return new GraphShadowResolution(graphShadowCacheEntry.snapshot(), true);
        }
        GraphShadowSnapshot snapshot = PlatformRuntimeBootstrap.getGraphShadowProjector().project(
                activeRunId,
                "dispatch-live",
                dominantServiceTier,
                allDrivers,
                pendingOrders,
                field);
        graphShadowCacheEntry = new GraphShadowCacheEntry(
                activeRunId,
                dominantServiceTier,
                signature,
                dispatchSequence,
                snapshot
        );
        return new GraphShadowResolution(snapshot, false);
    }

    private String buildGraphShadowCacheSignature(String dominantServiceTier,
                                                  List<Driver> allDrivers,
                                                  List<Order> pendingOrders) {
        List<CellValueSnapshot> topCells = field.topCellSnapshots(dominantServiceTier, 4);
        String cellSignature = topCells.stream()
                .map(cell -> cell.cellId() + "@"
                        + quantizePercent(cell.compositeValue(), 10) + "@"
                        + quantizePercent(cell.postDropOpportunity10m(), 10))
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        String orderFrontier = pendingOrders == null
                ? "none"
                : pendingOrders.stream()
                .sorted(Comparator
                        .comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Comparator.comparingDouble(Order::getPredictedLateRisk).reversed())
                        .thenComparing(Comparator.comparingDouble(Order::getPickupDelayHazard).reversed())
                        .thenComparing(Order::getId))
                .limit(5)
                .map(order -> field.cellKeyOf(order.getPickupPoint()) + "@"
                        + quantizePercent(order.getPredictedLateRisk(), 10) + "@"
                        + quantizePercent(order.getPickupDelayHazard(), 10))
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        String driverFrontier = allDrivers == null
                ? "none"
                : allDrivers.stream()
                .sorted(Comparator
                        .comparingInt((Driver driver) -> isGraphShadowRelevantDriver(driver, pendingOrders) ? 0 : 1)
                        .thenComparing(Comparator.comparing(Driver::getState))
                        .thenComparing(Driver::getId))
                .limit(5)
                .map(driver -> field.cellKeyOf(driver.getCurrentLocation()) + "@"
                        + (driver.isAvailable() ? "A" : "B") + "@"
                        + bucketOrderLoad(driver.getCurrentOrderCount()))
                .reduce((left, right) -> left + "|" + right)
                .orElse("none");
        return dominantServiceTier
                + "::cells=" + cellSignature
                + "::orders=" + quantizeCount(pendingOrders == null ? 0 : pendingOrders.size(), 3)
                + "::orderFrontier=" + orderFrontier
                + "::drivers=" + quantizeCount(allDrivers == null ? 0 : allDrivers.size(), 4)
                + "::driverFrontier=" + driverFrontier;
    }

    private boolean isGraphShadowRelevantDriver(Driver driver, List<Order> pendingOrders) {
        if (driver == null) {
            return false;
        }
        if (driver.isAvailable()) {
            return true;
        }
        if (pendingOrders == null || pendingOrders.isEmpty()) {
            return false;
        }
        double minDistanceKm = pendingOrders.stream()
                .mapToDouble(order -> driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0)
                .min()
                .orElse(Double.POSITIVE_INFINITY);
        return minDistanceKm <= 1.8;
    }

    private List<DispatchPlan> shortlistPlansForFullScoring(DriverDecisionContext ctx,
                                                            List<DispatchPlan> generatedPlans,
                                                            double trafficIntensity,
                                                            WeatherProfile weather) {
        if (generatedPlans == null || generatedPlans.isEmpty()) {
            return List.of();
        }
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return generatedPlans;
        }

        List<DispatchPlan> passivePlans = generatedPlans.stream()
                .filter(plan -> plan.getOrders().isEmpty())
                .toList();
        List<DispatchPlan> orderPlans = generatedPlans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .sorted(Comparator.comparingDouble(this::cheapScoringShortlistScore).reversed())
                .toList();
        int scoringBudget = graphScoringBudget(ctx, trafficIntensity, weather, orderPlans.size());
        if (orderPlans.size() <= scoringBudget) {
            return generatedPlans;
        }

        List<DispatchPlan> shortlisted = new ArrayList<>(passivePlans);
        Set<String> seenPlanKeys = new HashSet<>();
        for (DispatchPlan plan : passivePlans) {
            seenPlanKeys.add(shortlistKey(plan));
        }

        addRepresentativePlan(shortlisted, seenPlanKeys, orderPlans,
                plan -> plan.getBundleSize() >= 3 || plan.isWaveLaunchEligible());
        addRepresentativePlan(shortlisted, seenPlanKeys, orderPlans,
                plan -> plan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL);
        addRepresentativePlan(shortlisted, seenPlanKeys, orderPlans,
                plan -> plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
        if (allowBorrowedShortlist(ctx, weather, trafficIntensity)) {
            addRepresentativePlan(shortlisted, seenPlanKeys, orderPlans,
                    plan -> plan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                            || plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE);
        }

        for (DispatchPlan plan : orderPlans) {
            if (countExecutablePlans(shortlisted) >= scoringBudget) {
                break;
            }
            String key = shortlistKey(plan);
            if (seenPlanKeys.add(key)) {
                shortlisted.add(plan);
            }
        }
        return shortlisted;
    }

    private void addRepresentativePlan(List<DispatchPlan> shortlisted,
                                       Set<String> seenPlanKeys,
                                       List<DispatchPlan> orderPlans,
                                       java.util.function.Predicate<DispatchPlan> predicate) {
        if (predicate == null || orderPlans == null) {
            return;
        }
        for (DispatchPlan plan : orderPlans) {
            if (!predicate.test(plan)) {
                continue;
            }
            String key = shortlistKey(plan);
            if (seenPlanKeys.add(key)) {
                shortlisted.add(plan);
            }
            return;
        }
    }

    private int countExecutablePlans(List<DispatchPlan> plans) {
        if (plans == null || plans.isEmpty()) {
            return 0;
        }
        return (int) plans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .count();
    }

    private int graphScoringBudget(DriverDecisionContext ctx,
                                   double trafficIntensity,
                                   WeatherProfile weather,
                                   int generatedPlanCount) {
        int budget = switch (weather) {
            case CLEAR -> 4;
            case LIGHT_RAIN -> 5;
            case HEAVY_RAIN -> 6;
            case STORM -> 7;
        };
        if (ctx != null && (ctx.localReachableBacklog() >= 5 || ctx.reachableOrders().size() >= 6)) {
            budget += 1;
        }
        if (trafficIntensity >= 0.55) {
            budget += 1;
        }
        if (ctx != null && ctx.thirdOrderFeasibilityScore() >= 0.75 && ctx.localReachableBacklog() >= 3) {
            budget += 1;
        }
        return Math.max(3, Math.min(generatedPlanCount, budget));
    }

    private boolean allowBorrowedShortlist(DriverDecisionContext ctx,
                                           WeatherProfile weather,
                                           double trafficIntensity) {
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return true;
        }
        if (trafficIntensity >= 0.52) {
            return true;
        }
        return ctx != null
                && (ctx.localShortagePressure() >= 0.42 || ctx.localReachableBacklog() <= 1);
    }

    private String shortlistKey(DispatchPlan plan) {
        return plan.getDriver().getId() + "::" + plan.getBundle().bundleId();
    }

    private double cheapScoringShortlistScore(DispatchPlan plan) {
        double score = plan.getTotalScore();
        if (plan.getBundleSize() >= 3 || plan.isWaveLaunchEligible()) {
            score += 0.12;
        }
        if (plan.getBundleSize() <= 1 && !plan.isWaveLaunchEligible()) {
            score -= 0.08;
        }
        score += plan.getWaveReadinessScore() * 0.08;
        score += plan.getCoverageQuality() * 0.05;
        score += plan.getLastDropLandingScore() * 0.10;
        score += plan.getPostDropDemandProbability() * 0.08;
        score -= plan.getPredictedDeadheadKm() * 0.10;
        score -= plan.getExpectedPostCompletionEmptyKm() * 0.10;
        score -= plan.getBorrowedDependencyScore() * 0.14;
        score -= plan.getEmptyRiskAfter() * 0.12;
        if (plan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                || plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE) {
            score -= 0.08;
        }
        return score;
    }

    private List<DispatchPlan> applyWaveAssemblyGate(DriverDecisionContext ctx,
                                                     List<DispatchPlan> driverPlans,
                                                     MutableDispatchRecoveryStats recovery) {
        List<DispatchPlan> wavePlans = driverPlans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .filter(plan -> plan.isWaveLaunchEligible() || plan.getBundleSize() >= 3)
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .toList();
        List<DispatchPlan> fallbackPlans = driverPlans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .filter(plan -> !plan.isWaveLaunchEligible() && plan.getBundleSize() <= 2)
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .toList();
        List<DispatchPlan> holdPlans = driverPlans.stream()
                .filter(DispatchPlan::isWaitingForThirdOrder)
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .toList();

        List<DispatchPlan> shortlist = new ArrayList<>(3);
        if (!wavePlans.isEmpty()) {
            DispatchPlan primaryWave = wavePlans.get(0);
            primaryWave.setRunId(activeRunId);
            if (primaryWave.getBundleSize() >= 3) {
                primaryWave.setSelectionBucket(SelectionBucket.WAVE_LOCAL);
            } else {
                primaryWave.setSelectionBucket(SelectionBucket.EXTENSION_LOCAL);
            }
            primaryWave.setHoldRemainingCycles(0);
            shortlist.add(primaryWave);
        }

        boolean strongImmediateWave = !wavePlans.isEmpty()
                && wavePlans.get(0).getBundleSize() >= 3
                && wavePlans.get(0).getOnTimeProbability() >= 0.62
                && wavePlans.get(0).getPredictedDeadheadKm() <= 3.2;
        if (!holdPlans.isEmpty() && !strongImmediateWave) {
            DispatchPlan hold = holdPlans.get(0);
            hold.setRunId(activeRunId);
            int holdTtl = holdTtlCycles(ctx, hold.getStressRegime(), hold.isHarshWeatherStress());
            hold.setHoldRemainingCycles(holdTtl);
            hold.setSelectionBucket(SelectionBucket.HOLD_WAIT3);
            hold.setHoldReason("wait_for_third_order");
            hold.setHoldAnchorZoneId(hold.getDriver() == null ? null : hold.getDriver().getRegionId());
            if (holdTtl > 0 || shortlist.isEmpty()) {
                shortlist.add(hold);
            }
        }

        if (!fallbackPlans.isEmpty()) {
            DispatchPlan fallback = fallbackPlans.get(0);
            fallback.setRunId(activeRunId);
            fallback.setSelectionBucket(SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD);
            fallback.setHoldRemainingCycles(0);
            boolean hasLiveHold = shortlist.stream()
                    .anyMatch(plan -> plan.getSelectionBucket() == SelectionBucket.HOLD_WAIT3
                            && plan.getHoldRemainingCycles() > 0);
            boolean allowFallbackSlot = shouldAllowFallbackSlot(
                    ctx,
                    fallback,
                    !wavePlans.isEmpty(),
                    hasLiveHold);
            if (!hasLiveHold && allowFallbackSlot) {
                shortlist.add(fallback);
            } else if (hasLiveHold
                    && allowFallbackSlot
                    && fallbackOutweighsHold(ctx, fallback, shortlist)) {
                shortlist.removeIf(plan -> plan.getSelectionBucket() == SelectionBucket.HOLD_WAIT3);
                if (recovery != null) {
                    recovery.holdSuppressedByFallbackCount++;
                }
                shortlist.add(fallback);
            }
        }

        if (!shortlist.isEmpty()) {
            return shortlist;
        }

        return driverPlans.stream()
                .filter(plan -> plan.getOrders().isEmpty())
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .limit(1)
                .peek(plan -> {
                    plan.setSelectionBucket(SelectionBucket.EMERGENCY_COVERAGE);
                    plan.setHoldRemainingCycles(0);
                })
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private void trackHoldLifecycle(Driver driver,
                                    List<DispatchPlan> shortlist,
                                    MutableDispatchRecoveryStats recovery) {
        if (driver == null || shortlist == null || recovery == null) {
            return;
        }
        String driverId = driver.getId();
        int previousHoldTtl = holdTtlByDriver.getOrDefault(driverId, 0);
        DispatchPlan liveHold = shortlist.stream()
                .filter(plan -> plan.getSelectionBucket() == SelectionBucket.HOLD_WAIT3
                        && plan.getHoldRemainingCycles() > 0)
                .findFirst()
                .orElse(null);
        if (liveHold != null) {
            holdTtlByDriver.put(driverId, liveHold.getHoldRemainingCycles());
            return;
        }

        if (previousHoldTtl > 0) {
            boolean convertedToWave = shortlist.stream()
                    .anyMatch(plan -> (plan.getSelectionBucket() == SelectionBucket.WAVE_LOCAL
                            || plan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL)
                            && plan.getBundleSize() >= 3);
            boolean movedToFallback = shortlist.stream()
                    .anyMatch(plan -> isFallbackBucket(plan.getSelectionBucket()));
            if (convertedToWave) {
                recovery.holdConvertedToWaveCount++;
            } else if (movedToFallback) {
                recovery.holdExpiredToFallbackCount++;
            }
        }
        holdTtlByDriver.remove(driverId);
    }

    private boolean shouldAllowFallbackSlot(DriverDecisionContext ctx,
                                            DispatchPlan fallback,
                                            boolean hasWaveCandidate,
                                            boolean hasLiveHold) {
        if (fallback == null) {
            return false;
        }
        if (ctx == null) {
            return true;
        }
        if (ctx.harshWeatherStress() || ctx.stressRegime().isAtLeast(StressRegime.STRESS)) {
            return true;
        }
        if (ctx.effectiveSlaSlackMinutes() <= 2.0) {
            return true;
        }
        boolean fragileCoverage = ctx.localShortagePressure() >= 0.34
                || ctx.localDriverDensity() <= 0.32
                || ctx.localReachableBacklog() <= 1;
        boolean localWaveStillViable = hasWaveCandidate
                || ctx.localReachableBacklog() >= 3
                || ctx.nearReadyOrders() >= 2
                || ctx.thirdOrderFeasibilityScore() >= 0.58
                || ctx.waveAssemblyPressure() >= 0.38;
        if (localWaveStillViable
                && !fragileCoverage
                && ctx.effectiveSlaSlackMinutes() > 2.5) {
            return false;
        }
        if (fragileCoverage
                && fallback.getOnTimeProbability() >= 0.78
                && fallback.getPredictedDeadheadKm() <= 2.0
                && fallback.getLateRisk() <= 0.34) {
            return true;
        }
        if (!hasWaveCandidate
                && ctx.localReachableBacklog() <= 1
                && ctx.thirdOrderFeasibilityScore() < 0.50) {
            return true;
        }
        if (fallback.getOnTimeProbability() >= 0.82
                && fallback.getPredictedDeadheadKm() <= 1.6
                && fallback.getLateRisk() <= 0.32
                && ctx.localReachableBacklog() <= 1
                && ctx.thirdOrderFeasibilityScore() < 0.55) {
            return true;
        }
        if (hasLiveHold) {
            return fallback.getOnTimeProbability() >= 0.84
                    && fallback.getPredictedDeadheadKm() <= 1.5
                    && fallback.getLateRisk() <= 0.30
                    && (fragileCoverage
                    || ctx.thirdOrderFeasibilityScore() < 0.50
                    || ctx.effectiveSlaSlackMinutes() <= 3.0);
        }
        return false;
    }

    private boolean isFallbackBucket(SelectionBucket bucket) {
        return bucket == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                || bucket == SelectionBucket.BORROWED_COVERAGE
                || bucket == SelectionBucket.EMERGENCY_COVERAGE;
    }

    private int holdTtlCycles(DriverDecisionContext ctx,
                              StressRegime stressRegime,
                              boolean harshWeather) {
        if (harshWeather || stressRegime == StressRegime.SEVERE_STRESS) {
            return 0;
        }
        if (stressRegime == StressRegime.STRESS) {
            return 1;
        }
        return 2;
    }

    private boolean fallbackOutweighsHold(DriverDecisionContext ctx,
                                          DispatchPlan fallback,
                                          List<DispatchPlan> shortlist) {
        DispatchPlan hold = shortlist.stream()
                .filter(plan -> plan.getSelectionBucket() == SelectionBucket.HOLD_WAIT3)
                .findFirst()
                .orElse(null);
        if (hold == null) {
            return true;
        }
        double scoreGap = fallback.getTotalScore() - hold.getTotalScore();
        boolean strongExecutionFallback = fallback.getOnTimeProbability() >= 0.82
                && fallback.getPredictedDeadheadKm() <= 2.0
                && fallback.getLateRisk() <= 0.32;
        boolean weakHoldWindow = hold.getWaveReadinessScore() < 0.58
                || hold.getHoldRemainingCycles() <= 1;
        boolean urgentSla = ctx != null && ctx.effectiveSlaSlackMinutes() < 2.5;
        boolean fragileCoverage = ctx != null
                && (ctx.localShortagePressure() >= 0.34
                || ctx.localReachableBacklog() <= 1
                || ctx.localDriverDensity() <= 0.32);
        boolean strongLocalWaveSignal = ctx != null
                && (ctx.localReachableBacklog() >= 3
                || ctx.nearReadyOrders() >= 2
                || ctx.thirdOrderFeasibilityScore() >= 0.60
                || ctx.waveAssemblyPressure() >= 0.40);
        boolean materialExecutionGain = fallback.getExecutionScore() >= hold.getExecutionScore() + 0.04
                || fallback.getOnTimeProbability() >= hold.getOnTimeProbability() + 0.05
                || fallback.getPredictedDeadheadKm() <= hold.getPredictedDeadheadKm() - 0.40;
        return strongExecutionFallback
                && !strongLocalWaveSignal
                && (scoreGap >= 0.03 || weakHoldWindow || urgentSla || fragileCoverage || materialExecutionGain);
    }

    private double computeExecutionScore(DispatchPlan plan,
                                         StressRegime regime,
                                         WeatherProfile weather) {
        double deadheadPenalty = deadheadPenalty(plan.getPredictedDeadheadKm());
        double latePenalty = clamp01(plan.getLateRisk());
        double cancelPenalty = clamp01(plan.getCancellationRisk());
        double postEmptyPenalty = clamp01(plan.getExpectedPostCompletionEmptyKm() / 3.2);
        double zigZagPenalty = clamp01(plan.getDeliveryZigZagPenalty());
        double onTime = clamp01(plan.getOnTimeProbability());
        double weatherWeight = (weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN) ? 1.0 : 1.15;
        double regimeWeight = regime == StressRegime.NORMAL ? 1.0
                : regime == StressRegime.STRESS ? 1.10 : 1.20;
        double score = onTime * 0.34
                + clamp01(1.0 - deadheadPenalty) * 0.24
                + clamp01(1.0 - latePenalty) * 0.16
                + clamp01(1.0 - cancelPenalty) * 0.10
                + clamp01(1.0 - postEmptyPenalty) * 0.10
                + clamp01(1.0 - zigZagPenalty) * 0.06
                + clamp01(1.0 - plan.getMerchantPrepRiskScore()) * 0.05;
        score += serviceTierExecutionBias(plan.getServiceTier(), plan.getOnTimeProbability(), plan.getPredictedDeadheadKm());
        return clamp01(score / (weatherWeight * regimeWeight));
    }

    private double computeFutureScore(DispatchPlan plan,
                                      StressRegime regime) {
        return clamp01(
                computeContinuationScore(plan, regime) * 0.72
                        + computeCoverageScore(plan, null, regime) * 0.28);
    }

    private double computeContinuationScore(DispatchPlan plan,
                                            StressRegime regime) {
        double corridor = clamp01(plan.getDeliveryCorridorScore());
        double landing = clamp01(plan.getLastDropLandingScore());
        double nextOrder = clamp01(plan.getNextOrderAcquisitionScore());
        double continuationValue = clamp01(plan.getContinuationValueScore());
        double postDropOpportunity = clamp01(plan.getPostDropDemandProbability());
        double lowEmptyFinish = clamp01(1.0 - plan.getExpectedPostCompletionEmptyKm() / 3.2);
        double lowIdleAfter = clamp01(1.0 - plan.getExpectedNextOrderIdleMinutes() / 8.0);
        DeliveryServiceTier serviceTier = DeliveryServiceTier.fromWireValue(plan.getServiceTier());
        double score;
        if (serviceTier == DeliveryServiceTier.INSTANT) {
            score = corridor * 0.22
                    + landing * 0.22
                    + nextOrder * 0.12
                    + continuationValue * 0.08
                    + postDropOpportunity * 0.20
                    + lowEmptyFinish * 0.12
                    + lowIdleAfter * 0.04;
        } else {
            score = corridor * 0.18
                    + landing * 0.18
                    + nextOrder * 0.20
                    + continuationValue * 0.18
                    + postDropOpportunity * 0.16
                    + lowEmptyFinish * 0.07
                    + lowIdleAfter * 0.03;
        }
        score += serviceTierContinuationBias(plan.getServiceTier(), postDropOpportunity, lowEmptyFinish);
        double regimeWeight = regime == StressRegime.NORMAL ? 1.0
                : regime == StressRegime.STRESS ? 0.82 : 0.65;
        return clamp01(score * regimeWeight);
    }

    private double computeCoverageScore(DispatchPlan plan,
                                        DriverDecisionContext ctx,
                                        StressRegime regime) {
        double coverage = clamp01(plan.getCoverageQuality());
        double replacement = clamp01(plan.getReplacementDepth());
        double lowBorrow = clamp01(1.0 - plan.getBorrowedDependencyScore());
        double lowEmptyRisk = clamp01(1.0 - plan.getEmptyRiskAfter());
        double localOpportunity = ctx == null ? 0.0 : clamp01(ctx.localPostDropOpportunity());
        double localLowRisk = ctx == null ? 0.0 : clamp01(1.0 - ctx.localEmptyZoneRisk());
        double score = coverage * 0.30
                + replacement * 0.17
                + lowBorrow * 0.23
                + lowEmptyRisk * 0.16
                + localOpportunity * 0.08
                + localLowRisk * 0.05
                + clamp01(plan.getBorrowSuccessProbability()) * 0.01;
        double regimeWeight = regime == StressRegime.NORMAL ? 1.0
                : regime == StressRegime.STRESS ? 0.90 : 0.78;
        return clamp01(score * regimeWeight);
    }

    private boolean passesExecutionGate(DispatchPlan plan,
                                        StressRegime regime,
                                        WeatherProfile weather) {
        double deadheadCap = deadheadBudgetFor(plan, regime, weather);
        if (plan.getPredictedDeadheadKm() > deadheadCap) {
            plan.setExecutionGatePassed(false);
            return false;
        }
        if ((weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)
                && regime == StressRegime.NORMAL
                && plan.getBundleSize() <= 2
                && plan.isStressFallbackOnly()
                && plan.getOnTimeProbability() < 0.78) {
            plan.setExecutionGatePassed(false);
            return false;
        }
        if ((weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)
                && plan.getOnTimeProbability() < minOnTimeForGate(plan, regime, weather)) {
            plan.setExecutionGatePassed(false);
            return false;
        }
        double postCompletionCap = plan.getBundleSize() >= 3 ? 3.6 : 2.8;
        if (plan.getExpectedPostCompletionEmptyKm() > postCompletionCap
                && plan.getSelectionBucket() != SelectionBucket.EMERGENCY_COVERAGE) {
            plan.setExecutionGatePassed(false);
            return false;
        }
        if (plan.getExecutionScore() > 0.0
                && plan.getExecutionScore() < minExecutionScoreForGate(plan, regime, weather)
                && plan.getSelectionBucket() != SelectionBucket.EMERGENCY_COVERAGE) {
            plan.setExecutionGatePassed(false);
            return false;
        }
        plan.setExecutionGatePassed(true);
        return true;
    }

    private double deadheadBudgetFor(DispatchPlan plan,
                                     StressRegime regime,
                                     WeatherProfile weather) {
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return regime == StressRegime.SEVERE_STRESS ? 2.4 : 3.0;
        }
        boolean borrowed = plan.getBorrowedDependencyScore() >= 0.25;
        if (plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE) {
            return EMERGENCY_DEADHEAD_CAP_KM;
        }
        if (plan.getBundleSize() <= 1) {
            return adjustDeadheadBudgetForServiceTier(plan.getServiceTier(), borrowed ? 2.2 : 2.6);
        }
        if (plan.getBundleSize() == 2) {
            return adjustDeadheadBudgetForServiceTier(plan.getServiceTier(), borrowed ? 2.8 : 3.0);
        }
        if (plan.getBundleSize() >= 3) {
            return adjustDeadheadBudgetForServiceTier(plan.getServiceTier(), borrowed ? 2.8 : 3.4);
        }
        return adjustDeadheadBudgetForServiceTier(plan.getServiceTier(), 3.0);
    }

    private double minOnTimeForGate(DispatchPlan plan,
                                    StressRegime regime,
                                    WeatherProfile weather) {
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return regime == StressRegime.SEVERE_STRESS ? 0.58 : 0.54;
        }
        if (plan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL) {
            return regime == StressRegime.STRESS ? 0.66 : 0.68;
        }
        if (plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                || plan.getBundleSize() <= 2) {
            return 0.72;
        }
        return regime == StressRegime.STRESS ? 0.66 : 0.70;
    }

    private double minExecutionScoreForGate(DispatchPlan plan,
                                            StressRegime regime,
                                            WeatherProfile weather) {
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return regime == StressRegime.SEVERE_STRESS ? 0.46 : 0.50;
        }
        if (plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                || plan.getBundleSize() <= 2) {
            return 0.53;
        }
        if (plan.getBundleSize() >= 3) {
            return 0.52;
        }
        return 0.55;
    }

    private double deadheadPenalty(double deadheadKm) {
        if (deadheadKm <= 1.5) {
            return clamp01(deadheadKm / 2.5);
        }
        if (deadheadKm <= 3.0) {
            return clamp01(0.6 + (deadheadKm - 1.5) / 1.5 * 0.25);
        }
        return clamp01(0.85 + (deadheadKm - 3.0) / 2.0 * 0.35);
    }

    private double[] adaptiveBlendWeights(DispatchPlan plan,
                                          StressRegime regime,
                                          WeatherProfile weather) {
        double robust = 0.25;
        double execution = 0.50;
        double future = 0.15;
        double utility = 0.10;
        DeliveryServiceTier serviceTier = DeliveryServiceTier.fromWireValue(plan.getServiceTier());

        boolean harshWeather = weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM;
        if (regime == StressRegime.STRESS || harshWeather) {
            robust = 0.28;
            execution = 0.48;
            future = 0.12;
            utility = 0.12;
        }
        if (plan.getSelectionBucket() == SelectionBucket.WAVE_LOCAL
                || plan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL
                || plan.getBundleSize() >= 3) {
            robust += 0.02;
            execution -= 0.03;
            future += 0.02;
            utility -= 0.01;
        }
        if (plan.isStressFallbackOnly() || plan.getBundleSize() <= 2) {
            robust -= 0.05;
            execution += 0.07;
            future -= 0.03;
            utility += 0.01;
        }
        if (serviceTier == DeliveryServiceTier.INSTANT) {
            robust += 0.02;
            execution += 0.10;
            future -= 0.09;
            utility -= 0.03;
            if (regime == StressRegime.NORMAL
                    && (weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN)) {
                execution += 0.03;
                future -= 0.04;
                utility += 0.01;
            }
        }
        if (plan.getBorrowedDependencyScore() >= 0.25) {
            execution += 0.03;
            future -= 0.03;
            utility -= 0.01;
        }
        if (plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                || plan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                || plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE) {
            execution += 0.02;
            future -= 0.02;
        }
        double sum = Math.max(1e-9, robust + execution + future + utility);
        return new double[]{robust / sum, execution / sum, future / sum, utility / sum};
    }

    private double resilientExecutionBonus(DispatchPlan plan,
                                           StressRegime regime) {
        double bonus = 0.0;
        if (plan.getBundleSize() >= 3
                && plan.getOnTimeProbability() >= 0.74
                && plan.getPredictedDeadheadKm() <= 3.2) {
            bonus += 0.035;
        }
        if (plan.getDeliveryCorridorScore() >= 0.70
                && plan.getLastDropLandingScore() >= 0.65
                && plan.getExpectedPostCompletionEmptyKm() <= 1.2) {
            bonus += 0.022;
        }
        if (plan.getBundleSize() <= 2
                && plan.getPredictedDeadheadKm() <= 1.6
                && plan.getOnTimeProbability() >= 0.86
                && plan.getCancellationRisk() <= 0.18) {
            bonus += 0.018;
        }
        if (regime == StressRegime.SEVERE_STRESS) {
            bonus *= 0.7;
        }
        return bonus;
    }

    private double executionStabilityPenalty(DispatchPlan plan,
                                             StressRegime regime,
                                             WeatherProfile weather,
                                             double confidence) {
        double penalty = 0.0;
        boolean cleanWeather = weather == WeatherProfile.CLEAR || weather == WeatherProfile.LIGHT_RAIN;
        if (cleanWeather && regime == StressRegime.NORMAL) {
            penalty += Math.max(0.0, plan.getPredictedDeadheadKm() - 2.6) * 0.075;
            if (plan.getBundleSize() <= 2 && plan.isStressFallbackOnly()) {
                penalty += 0.04 + Math.max(0.0, 0.80 - plan.getOnTimeProbability()) * 0.12;
            }
        } else {
            penalty += Math.max(0.0, plan.getPredictedDeadheadKm() - 3.2) * 0.05;
        }
        penalty += clamp01(plan.getBorrowedDependencyScore()) * 0.035;
        penalty += clamp01(plan.getEmptyRiskAfter()) * 0.030;
        penalty += Math.max(0.0, 0.42 - confidence) * 0.05;
        return penalty;
    }

    private void populateSelectionSignals(DispatchPlan plan,
                                          DriverDecisionContext ctx,
                                          StressRegime regime) {
        int addedOrders = Math.max(1, plan.getBundleSize());
        plan.setMarginalDeadheadPerAddedOrder(
                plan.getPredictedDeadheadKm() / addedOrders);
        plan.setPickupSpreadKm(computePickupSpreadKm(plan.getSequence()));
        double compactness = clamp01(1.0 - plan.getPickupSpreadKm() / 2.5);
        double readiness = ctx == null ? 0.5 : clamp01(ctx.thirdOrderFeasibilityScore());
        double pressure = ctx == null ? 0.0 : clamp01(ctx.waveAssemblyPressure());
        plan.setWaveReadinessScore(clamp01(compactness * 0.55 + readiness * 0.30 + pressure * 0.15));
        double baseCoverage = plan.isStressFallbackOnly() ? 0.35 : 0.72;
        double localOpportunity = ctx == null ? 0.0 : clamp01(ctx.localPostDropOpportunity());
        double lowLocalRisk = ctx == null ? 0.5 : clamp01(1.0 - ctx.localEmptyZoneRisk());
        plan.setCoverageQuality(clamp01(baseCoverage * 0.55 + localOpportunity * 0.25 + lowLocalRisk * 0.20));
        plan.setReplacementDepth(clamp01((plan.getBundleSize() >= 3 ? 0.68 : 0.42)
                + (ctx == null ? 0.0 : Math.min(0.12, ctx.localDemandForecast10m() / 12.0))));
        double borrowedDependency = plan.isStressFallbackOnly() ? 0.35 : 0.10;
        if (ctx != null && ctx.localEmptyZoneRisk() >= 0.70) {
            borrowedDependency += 0.05;
        }
        borrowedDependency -= plan.getBorrowSuccessProbability() * 0.08;
        plan.setBorrowedDependencyScore(clamp01(borrowedDependency));
        double stressMultiplier = regime == StressRegime.NORMAL ? 1.0
                : regime == StressRegime.STRESS ? 1.15 : 1.30;
        double projectedEmptyRisk = clamp01(
                plan.getExpectedPostCompletionEmptyKm() / 4.0 * stressMultiplier
                        + (ctx == null ? 0.0 : ctx.localEmptyZoneRisk() * 0.25)
                        + clamp01(plan.getWeatherExposureScore()) * 0.12
                        + clamp01(plan.getTrafficExposureScore()) * 0.12
                        - clamp01(plan.getPostDropDemandProbability()) * 0.18);
        plan.setEmptyRiskAfter(projectedEmptyRisk);
    }

    private int minimumCoverageTarget(double shortage,
                                      double trafficIntensity,
                                      WeatherProfile weather,
                                      int pendingOrders,
                                      int availableDrivers) {
        int feasibleUnits = Math.max(0, Math.min(pendingOrders, availableDrivers));
        if (feasibleUnits <= 0) {
            return 0;
        }
        double share = switch (weather) {
            case CLEAR -> 0.42;
            case LIGHT_RAIN -> 0.38;
            case HEAVY_RAIN -> 0.30;
            case STORM -> 0.22;
        };
        if (shortage >= 0.50 || trafficIntensity >= 0.45) {
            share += 0.10;
        }
        if (shortage >= 0.70 || trafficIntensity >= 0.60) {
            share += 0.08;
        }
        if (pendingOrders > Math.max(6, availableDrivers)) {
            share += 0.08;
        }
        if (pendingOrders > Math.max(10, availableDrivers * 2)) {
            share += 0.10;
        }
        int scaledTarget = (int) Math.ceil(feasibleUnits * Math.min(0.85, share));
        int floor = switch (weather) {
            case CLEAR, LIGHT_RAIN -> Math.min(feasibleUnits, 2);
            case HEAVY_RAIN, STORM -> 1;
        };
        if (shortage >= 0.70 || trafficIntensity >= 0.60) {
            floor = Math.min(feasibleUnits, Math.max(floor, 3 + feasibleUnits / 6));
        }
        return Math.max(1, Math.min(feasibleUnits, Math.max(floor, scaledTarget)));
    }

    private int applyFallbackSaturationGuard(List<DispatchPlan> selectedPlans,
                                             int requestedTarget,
                                             double trafficIntensity,
                                             WeatherProfile weather) {
        if (requestedTarget <= 0 || executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return requestedTarget;
        }
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return requestedTarget;
        }
        int waveLikeCount = countPlansInBuckets(
                selectedPlans,
                SelectionBucket.WAVE_LOCAL,
                SelectionBucket.EXTENSION_LOCAL);
        int fallbackCount = countPlansInBuckets(
                selectedPlans,
                SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD,
                SelectionBucket.BORROWED_COVERAGE,
                SelectionBucket.EMERGENCY_COVERAGE);
        if (waveLikeCount <= 0) {
            return Math.min(requestedTarget, trafficIntensity >= 0.38 ? 2 : 1);
        }
        int maxFallbackShare = Math.max(1, waveLikeCount);
        if (fallbackCount >= maxFallbackShare) {
            return countRealAssignedPlans(selectedPlans);
        }
        return Math.min(requestedTarget, countRealAssignedPlans(selectedPlans) + (maxFallbackShare - fallbackCount));
    }

    private int countPlansInBuckets(List<DispatchPlan> plans,
                                    SelectionBucket... buckets) {
        if (plans == null || plans.isEmpty() || buckets == null || buckets.length == 0) {
            return 0;
        }
        Set<SelectionBucket> bucketSet = EnumSet.noneOf(SelectionBucket.class);
        bucketSet.addAll(List.of(buckets));
        return (int) plans.stream()
                .filter(plan -> !plan.getOrders().isEmpty())
                .filter(plan -> bucketSet.contains(plan.getSelectionBucket()))
                .count();
    }

    private int adaptiveFallbackOrderLimit(WeatherProfile weather,
                                           double trafficIntensity,
                                           int availableDrivers,
                                           int neededCoverageUnits) {
        int driverScaled = switch (weather) {
            case CLEAR -> Math.max(4, (int) Math.ceil(availableDrivers * 0.18));
            case LIGHT_RAIN -> Math.max(4, (int) Math.ceil(availableDrivers * 0.20));
            case HEAVY_RAIN -> Math.max(5, (int) Math.ceil(availableDrivers * 0.22));
            case STORM -> Math.max(5, (int) Math.ceil(availableDrivers * 0.25));
        };
        int coverageScaled = Math.max(neededCoverageUnits + 2, (int) Math.ceil(neededCoverageUnits * 1.75));
        if (trafficIntensity >= 0.65) {
            coverageScaled += 1;
        }
        int target = Math.max(driverScaled, coverageScaled);
        return Math.max(1, Math.min(Math.max(1, availableDrivers), target));
    }

    private boolean shouldForceMinimumFallbackCoverage(List<DispatchPlan> selectedPlans,
                                                       List<Order> pendingOrders,
                                                       List<Driver> freeDrivers,
                                                       Order order,
                                                       DeliveryServiceTier serviceTier,
                                                       double deadheadKm,
                                                       WeatherProfile weather,
                                                       double pickupReadySlackMinutes) {
        if (countRealAssignedPlans(selectedPlans) > 0
                || pendingOrders == null
                || freeDrivers == null
                || pendingOrders.size() != 1
                || freeDrivers.size() != 1
                || order == null) {
            return false;
        }

        double catastrophicDeadheadKm = switch (weather) {
            case CLEAR -> 2.4;
            case LIGHT_RAIN -> 2.2;
            case HEAVY_RAIN -> 1.9;
            case STORM -> 1.5;
        };
        catastrophicDeadheadKm = adjustDeadheadBudgetForServiceTier(
                serviceTier.wireValue(),
                catastrophicDeadheadKm);

        return deadheadKm <= catastrophicDeadheadKm
                && order.getPickupDelayHazard() <= 0.70
                && pickupReadySlackMinutes <= 4.0;
    }

    private Driver selectLocalSameZoneFallbackDriver(Order order,
                                                     List<Driver> freeDrivers,
                                                     double cleanLocalCapKm) {
        if (order == null || freeDrivers == null || freeDrivers.isEmpty()) {
            return null;
        }
        return freeDrivers.stream()
                .filter(driver -> driver.getRegionId().equals(order.getPickupRegionId()))
                .filter(driver -> driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0 <= cleanLocalCapKm)
                .min(Comparator.comparingDouble(driver ->
                        driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0))
                .orElse(null);
    }

    private Driver selectCoverageAwareFallbackDriver(Order order,
                                                     List<Driver> freeDrivers,
                                                     double trafficIntensity,
                                                     WeatherProfile weather) {
        if (freeDrivers == null || freeDrivers.isEmpty()) {
            return null;
        }
        double weatherSpeedFactor = switch (weather) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 0.88;
            case HEAVY_RAIN -> 0.70;
            case STORM -> 0.55;
        };
        DeliveryServiceTier serviceTier = DeliveryServiceTier.classify(order);
        return freeDrivers.stream()
                .max(Comparator.comparingDouble(driver -> {
                    double distKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
                    double speedKmh = Math.max(8.0, 24.0 * (1.0 - trafficIntensity * 0.45) * weatherSpeedFactor);
                    double etaMinutes = distKm / speedKmh * 60.0;
                    boolean sameZone = driver.getRegionId().equals(order.getPickupRegionId());
                    double sameZoneBonus = sameZone ? 0.52 : 0.0;
                    double adaptiveLocalBonus = sameZone
                            ? (distKm <= 1.2 ? 0.18 : distKm <= 2.2 ? 0.10 : 0.04)
                            : 0.0;
                    double proximityScore = clamp01(1.0 - distKm / 3.4) * 0.28;
                    double etaScore = clamp01(1.0 - etaMinutes / 8.5) * 0.22;
                    double tierBias = switch (serviceTier) {
                        case INSTANT -> sameZone ? 0.08 : -0.05;
                        case TWO_HOUR -> 0.03;
                        case FOUR_HOUR, SCHEDULED -> 0.01;
                        case MULTI_STOP_COD -> -0.02;
                    };
                    double driverWeatherPenalty = field.getWeatherExposureAt(driver.getCurrentLocation()) * 0.06;
                    double driverCongestionPenalty = field.getCongestionExposureAt(driver.getCurrentLocation()) * 0.08;
                    double loadPenalty = Math.min(0.15, driver.getCurrentOrderCount() * 0.08);
                    double sourceShortagePenalty = sameZone
                            ? 0.0
                            : field.getShortageAt(driver.getCurrentLocation()) * 0.10;
                    double sourceAttractionPenalty = sameZone
                            ? 0.0
                            : field.getRiskAdjustedAttractionAt(driver.getCurrentLocation()) * 0.08;
                    return sameZoneBonus + adaptiveLocalBonus + proximityScore + etaScore + tierBias
                            - driverWeatherPenalty - driverCongestionPenalty - loadPenalty
                            - sourceShortagePenalty - sourceAttractionPenalty;
                }))
                .orElse(null);
    }

    private boolean isUrgentFallbackOrder(Order order,
                                          Instant currentTime,
                                          WeatherProfile weather) {
        if (order == null || currentTime == null) {
            return false;
        }
        double elapsedMinutes = Duration.between(order.getCreatedAt(), currentTime)
                .toSeconds() / 60.0;
        double readySlackMinutes = order.getPredictedReadyAt() != null
                ? Math.max(0.0, Duration.between(currentTime, order.getPredictedReadyAt()).toSeconds() / 60.0)
                : 0.0;
        double remainingSlack = order.getPromisedEtaMinutes() - elapsedMinutes - readySlackMinutes;
        double urgentThreshold = switch (weather) {
            case CLEAR -> 2.5;
            case LIGHT_RAIN -> 3.5;
            case HEAVY_RAIN -> 4.5;
            case STORM -> 5.5;
        };
        return remainingSlack <= urgentThreshold
                || (order.getPickupDelayHazard() >= 0.75 && elapsedMinutes >= 8.0);
    }

    private double computePickupSpreadKm(List<DispatchPlan.Stop> sequence) {
        List<GeoPoint> pickups = sequence.stream()
                .filter(stop -> stop.type() == DispatchPlan.Stop.StopType.PICKUP)
                .map(DispatchPlan.Stop::location)
                .toList();
        if (pickups.size() <= 1) {
            return 0.0;
        }
        double maxDistanceKm = 0.0;
        for (int i = 0; i < pickups.size(); i++) {
            for (int j = i + 1; j < pickups.size(); j++) {
                maxDistanceKm = Math.max(maxDistanceKm, pickups.get(i).distanceTo(pickups.get(j)) / 1000.0);
            }
        }
        return maxDistanceKm;
    }

    private void syncThreeOrderPolicyFlags(DispatchPlan plan,
                                           DriverDecisionContext ctx,
                                           StressRegime effectiveRegime) {
        boolean cleanThreeLaunch = shouldTargetCleanThreeLaunch(ctx, effectiveRegime);
        boolean hardThreePolicy = DriverPlanGenerator.requiresHardThreeOrderLaunch(executionProfile, ctx)
                && effectiveRegime != StressRegime.SEVERE_STRESS;
        boolean sparseOrOffRouteDowngrade = isSparseOrOffRouteDowngrade(ctx, effectiveRegime, plan);
        plan.setStressRegime(effectiveRegime);
        plan.setHarshWeatherStress(ctx.harshWeatherStress());
        plan.setHardThreeOrderPolicyActive(cleanThreeLaunch);
        if (!plan.getOrders().isEmpty()) {
            boolean stressFallbackOnly = plan.getBundleSize() < 3
                    && (sparseOrOffRouteDowngrade
                    || cleanThreeLaunch
                    || ctx.harshWeatherStress()
                    || effectiveRegime == StressRegime.SEVERE_STRESS);
            plan.setStressFallbackOnly(stressFallbackOnly);
            if (plan.getBundleSize() >= 3) {
                plan.setWaveLaunchEligible(plan.isWaveLaunchEligible()
                        || cleanThreeLaunch
                        || !hardThreePolicy
                        || (plan.getDeliveryCorridorScore() >= 0.36
                        && plan.getLastDropLandingScore() >= 0.22
                        && plan.getOnTimeProbability() >= 0.58));
            }
        }
    }

    private boolean shouldTargetCleanThreeLaunch(DriverDecisionContext ctx,
                                                 StressRegime effectiveRegime) {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC || ctx == null) {
            return false;
        }
        if (ctx.harshWeatherStress() || effectiveRegime == StressRegime.SEVERE_STRESS) {
            return false;
        }
        if (ctx.reachableOrders().size() < 3) {
            return false;
        }
        boolean localWaveSignal = ctx.localReachableBacklog() >= 3
                || ctx.compactClusterCount() >= 1
                || ctx.nearReadySameMerchantCount() >= 2
                || ctx.nearReadyOrders() >= 3
                || ctx.reachableOrders().size() >= 4
                || ctx.waveAssemblyPressure() >= 0.40;
        return localWaveSignal
                && ctx.thirdOrderFeasibilityScore() >= 0.35
                && ctx.threeOrderSlackBuffer() >= 0.8
                && ctx.localCorridorExposure() <= 0.82;
    }

    private boolean isSparseOrOffRouteDowngrade(DriverDecisionContext ctx,
                                                StressRegime effectiveRegime,
                                                DispatchPlan plan) {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC || ctx == null || plan == null) {
            return false;
        }
        if (ctx.harshWeatherStress() || effectiveRegime == StressRegime.SEVERE_STRESS) {
            return false;
        }
        if (plan.getBundleSize() >= 3) {
            return false;
        }
        boolean credibleWaveWindow = shouldTargetCleanThreeLaunch(ctx, effectiveRegime)
                && (ctx.waveAssemblyPressure() >= 0.30
                || ctx.nearReadyOrders() >= 2
                || ctx.compactClusterCount() >= 1
                || ctx.nearReadySameMerchantCount() >= 1);
        boolean sparseWorld = ctx.localReachableBacklog() <= 1
                && ctx.thirdOrderFeasibilityScore() < 0.45;
        boolean materiallyOffRoute = plan.getPredictedDeadheadKm() >= 2.4
                || plan.getBorrowedDependencyScore() >= 0.25
                || plan.getExpectedPostCompletionEmptyKm() >= 1.8
                || plan.getEmptyRiskAfter() >= 0.55;
        return credibleWaveWindow && !sparseWorld && materiallyOffRoute;
    }

    private void printDecisionHighlights(List<DispatchPlan> selectedPlans) {
        selectedPlans.stream()
                .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                .limit(3)
                .forEach(plan -> System.out.printf(
                        "[Omega-DC][Pick] driver=%s bundle=%d score=%.3f onTime=%.2f corridor=%.2f landing=%.2f emptyKm=%.2f why=%s%n",
                        plan.getDriver().getId(),
                        plan.getBundleSize(),
                        plan.getTotalScore(),
                        plan.getOnTimeProbability(),
                        plan.getDeliveryCorridorScore(),
                        plan.getLastDropLandingScore(),
                        plan.getExpectedPostCompletionEmptyKm(),
                        buildDecisionReason(plan)));
    }

    private String buildDecisionReason(DispatchPlan plan) {
        List<String> reasons = new ArrayList<>();
        if (plan.isWaitingForThirdOrder()) {
            reasons.add("held for third order");
            if (plan.isHardThreeOrderPolicyActive()) {
                reasons.add("clean-regime 3+ policy");
            }
            return String.join(", ", reasons);
        }
        if (plan.getBundleSize() >= 3) {
            reasons.add("visible pickup-wave " + plan.getBundleSize());
        } else if (plan.getBundleSize() == 2) {
            reasons.add(plan.isStressFallbackOnly()
                    ? (plan.getStressRegime() == StressRegime.SEVERE_STRESS || plan.isHarshWeatherStress()
                    ? "downgraded due to severe stress"
                    : "downgraded due to sparse/off-route world")
                    : "compact 2-order wave");
        } else {
            reasons.add(plan.isStressFallbackOnly()
                    ? (plan.getStressRegime() == StressRegime.SEVERE_STRESS || plan.isHarshWeatherStress()
                    ? "downgraded due to severe stress"
                    : "downgraded due to sparse/off-route world")
                    : "safe single coverage");
        }
        if (plan.getDeliveryCorridorScore() >= 0.70) {
            reasons.add("corridor-aligned drops");
        } else if (plan.getDeliveryZigZagPenalty() >= 0.45) {
            reasons.add("zig-zag risk absorbed");
        }
        if (plan.getLastDropLandingScore() >= 0.70) {
            reasons.add("soft landing to hot zone");
        } else if (plan.getExpectedPostCompletionEmptyKm() <= 1.0) {
            reasons.add("low empty-mile finish");
        }
        if (plan.getRemainingDropProximityScore() >= 0.72) {
            reasons.add("remaining drops stay thuận đường");
        }
        if (plan.getOnTimeProbability() >= 0.82) {
            reasons.add("SLA-safe");
        }
        if (plan.isHardThreeOrderPolicyActive() && plan.getBundleSize() >= 3) {
            reasons.add("launched clean 3-wave");
        }
        return String.join(", ", reasons);
    }

    // ── Accessors for diagnostics ───────────────────────────────────────

    private void emitDecisionArtifacts(long tick,
                                       double[] contextFeatures,
                                       double[] planFeatures,
                                       String policyUsed,
                                       DispatchPlan plan,
                                       DriverDecisionContext ctx,
                                       List<DispatchPlan> candidatePlans,
                                       long dispatchDecisionLatencyMs,
                                       String explanation) {
        Map<String, Object> contextSnapshot = buildContextSnapshot(ctx, plan);
        featureStore.put("dispatch-context", plan.getTraceId(), contextSnapshot);
        ShadowAdviceEnvelope shadowAdvice = maybeEmitShadowAdvice(
                plan, ctx, candidatePlans, contextFeatures, policyUsed, explanation);
        DispatchFactSink.DecisionFact decisionFact = new DispatchFactSink.DecisionFact(
                plan.getTraceId(),
                plan.getRunId(),
                tick,
                plan.getDriver().getId(),
                policyUsed,
                executionProfile.name(),
                ablationMode.name(),
                plan.getTotalScore(),
                plan.getConfidence(),
                plan.getBundleSize(),
                semanticPlanSummary(plan),
                contextSnapshot,
                contextFeatures.clone(),
                planFeatures.clone(),
                explanation,
                shadowAdvice.requestClass(),
                shadowAdvice.estimatedInputTokens(),
                shadowAdvice.response().quotaDecision(),
                shadowAdvice.response().fallbackChain(),
                shadowAdvice.response().mode(),
                plan.getServiceTier(),
                activeRouteLatencyMode,
                dispatchDecisionLatencyMs,
                plan.getSelectionBucket().name(),
                plan.getHoldRemainingCycles(),
                plan.getMarginalDeadheadPerAddedOrder(),
                shadowAdvice.response(),
                Instant.now());
        dispatchFactSink.recordDecision(decisionFact);
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.DISPATCH_DECISION_V2,
                decisionFact);
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.FEATURE_SNAPSHOT_V2,
                toFeatureSnapshot(plan, contextSnapshot, contextFeatures));
        ModelBundleManifest etaBundle = modelArtifactProvider.activeBundle("eta-model");
        ModelBundleManifest rankerBundle = modelArtifactProvider.activeBundle("plan-ranker-model");
        boolean etaFallback = isBundleOffline(etaBundle);
        boolean rankerFallback = isBundleOffline(rankerBundle);
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.MODEL_INFERENCE_V1,
                new InferenceTraceV1(
                        plan.getRunId(),
                        plan.getTraceId(),
                        etaBundle.modelKey(),
                        etaBundle.modelVersion(),
                        "tabular-onnx",
                        "onnx-java",
                        plan.getModelInferenceLatencyMs(),
                        plan.getTotalScore(),
                        true,
                        false,
                        false,
                        etaFallback,
                        etaFallback ? "offline-or-missing-bundle" : "none",
                        Instant.now()));
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.MODEL_INFERENCE_V1,
                new InferenceTraceV1(
                        plan.getRunId(),
                        plan.getTraceId() + "-ranker",
                        rankerBundle.modelKey(),
                        rankerBundle.modelVersion(),
                        "tabular-onnx",
                        "onnx-java",
                        plan.getModelInferenceLatencyMs(),
                        plan.getExecutionScore() * 0.7 + plan.getFutureScore() * 0.3,
                        true,
                        false,
                        false,
                        rankerFallback,
                        rankerFallback ? "offline-or-missing-bundle" : "none",
                        Instant.now()));
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.MODEL_INFERENCE_V1,
                new InferenceTraceV1(
                        plan.getRunId(),
                        plan.getTraceId() + "-neural-prior",
                        "neural-route-prior",
                        plan.getNeuralPriorVersion(),
                        "neural-route-prior",
                        plan.getNeuralPriorBackend(),
                        plan.getNeuralPriorLatencyMs(),
                        plan.getNeuralPriorScore(),
                        false,
                        plan.isNeuralPriorUsed() && plan.getNeuralPriorFreshnessMs() > 0,
                        plan.getNeuralPriorFallbackReason() != null
                                && plan.getNeuralPriorFallbackReason().contains("timeout"),
                        !plan.isNeuralPriorUsed(),
                        plan.getNeuralPriorFallbackReason(),
                        Instant.now()));
    }

    private ShadowAdviceEnvelope maybeEmitShadowAdvice(DispatchPlan plan,
                                                       DriverDecisionContext ctx,
                                                       List<DispatchPlan> candidatePlans,
                                                       double[] contextFeatures,
                                                       String policyUsed,
                                                       String explanation) {
        if (ctx == null || !llmEscalationGate.shouldEscalate(ctx, plan, candidatePlans)) {
            return new ShadowAdviceEnvelope(
                    LLMRequestClass.SHADOW_FAST.name(),
                    -1,
                    LLMAdvisorResponse.skipped("escalation gate closed"));
        }
        List<LLMAdvisorRequest.CandidatePlanSummary> summaries =
                (candidatePlans == null ? List.<DispatchPlan>of() : candidatePlans).stream()
                        .filter(candidate -> !candidate.getOrders().isEmpty())
                        .sorted(Comparator.comparingDouble(DispatchPlan::getTotalScore).reversed())
                        .limit(4)
                        .map(candidate -> new LLMAdvisorRequest.CandidatePlanSummary(
                                candidate.getTraceId(),
                                candidate.getBundleSize(),
                                candidate.getTotalScore(),
                                candidate.getOnTimeProbability(),
                                candidate.getDeliveryCorridorScore(),
                                candidate.getLastDropLandingScore(),
                                candidate.getExpectedPostCompletionEmptyKm(),
                                Objects.equals(candidate.getTraceId(), plan.getTraceId()),
                                buildDecisionReason(candidate)))
                        .toList();
        if (summaries.isEmpty()) {
            summaries = List.of(new LLMAdvisorRequest.CandidatePlanSummary(
                    plan.getTraceId(),
                    plan.getBundleSize(),
                    plan.getTotalScore(),
                    plan.getOnTimeProbability(),
                    plan.getDeliveryCorridorScore(),
                    plan.getLastDropLandingScore(),
                    plan.getExpectedPostCompletionEmptyKm(),
                    true,
                    explanation));
        }
        int estimatedInputTokens = estimateShadowInputTokens(contextFeatures, summaries, explanation);
        LLMRequestClass requestClass = classifyShadowRequest(plan, summaries);
        LLMAdvisorResponse response = llmAdvisorClient.advise(new LLMAdvisorRequest(
                activeRunId,
                plan.getTraceId(),
                plan.getDriver().getId(),
                executionProfile.name(),
                policyUsed,
                ctx.stressRegime().name(),
                contextFeatures.clone(),
                summaries,
                requestClass,
                estimatedInputTokens,
                false
        ));
        return new ShadowAdviceEnvelope(requestClass.name(), estimatedInputTokens, response);
    }

    private int estimateShadowInputTokens(double[] contextFeatures,
                                          List<LLMAdvisorRequest.CandidatePlanSummary> summaries,
                                          String explanation) {
        int base = 56 + (contextFeatures == null ? 0 : Math.min(contextFeatures.length, 12) * 6);
        int perPlan = summaries == null ? 0 : summaries.size() * 48;
        int text = explanation == null ? 0 : explanation.length() / 4;
        return Math.max(48, base + perPlan + text);
    }

    private LLMRequestClass classifyShadowRequest(DispatchPlan plan,
                                                  List<LLMAdvisorRequest.CandidatePlanSummary> summaries) {
        if (executionProfile == ExecutionProfile.SHOWCASE_PICKUP_WAVE_8
                || plan.getBundleSize() >= 4
                || plan.getConfidence() < 0.48) {
            return LLMRequestClass.ADVISORY_HIGH_QUALITY;
        }
        if (summaries != null && summaries.size() >= 2) {
            double gap = summaries.get(0).totalScore() - summaries.get(1).totalScore();
            if (gap <= 0.08) {
                return LLMRequestClass.ADVISORY_HIGH_QUALITY;
            }
        }
        return LLMRequestClass.SHADOW_FAST;
    }

    private Map<String, Object> buildContextSnapshot(DriverDecisionContext ctx, DispatchPlan plan) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        DecisionContextV2 decisionContextV2 = new DecisionContextV2(
                plan.getRunId(),
                executionProfile.name(),
                plan.getServiceTier(),
                plan.getDriver() == null ? null : field.cellKeyOf(plan.getDriver().getCurrentLocation()),
                Instant.now(),
                5,
                10,
                ctx == null ? 0.0 : ctx.localMerchantPrepForecast10m(),
                plan.getContinuationValueScore(),
                plan.getPostDropDemandProbability(),
                plan.getEmptyRiskAfter(),
                plan.getNeuralPriorVersion(),
                plan.getNeuralPriorFreshnessMs());
        DriverAccessCluster driverAccessCluster = new DriverAccessCluster(
                plan.getTraceId() + "-cluster",
                plan.getDriver() == null ? null : plan.getDriver().getRegionId(),
                plan.getDriver() == null ? List.of() : List.of(plan.getDriver().getId()),
                plan.getBorrowedDependencyScore() >= 0.25 && plan.getDriver() != null
                        ? List.of(plan.getDriver().getId())
                        : List.of(),
                Math.max(0.8, Math.min(3.0, 0.8 + plan.getPredictedDeadheadKm() * 0.45)),
                plan.getPredictedTotalMinutes() > 0 ? Math.min(12.0, plan.getPredictedTotalMinutes() * 0.35) : 0.0,
                plan.getReplacementDepth(),
                plan.getCoverageQuality(),
                clamp01(1.0 - plan.getEmptyRiskAfter()),
                plan.getPostDropDemandProbability(),
                plan.getBorrowedDependencyScore(),
                plan.getBorrowedDependencyScore() >= 0.25);
        ZoneCoverageSnapshot zoneCoverageSnapshot = new ZoneCoverageSnapshot(
                plan.getDriver() == null ? null : plan.getDriver().getRegionId(),
                ctx == null ? 0 : Math.max(1, (int) Math.round(ctx.localDriverDensity())),
                minimumCoverageTarget(
                        ctx == null ? 0.0 : ctx.localShortagePressure(),
                        ctx == null ? 0.0 : ctx.localTrafficIntensity(),
                        ctx != null && ctx.harshWeatherStress() ? WeatherProfile.HEAVY_RAIN : WeatherProfile.CLEAR,
                        ctx == null ? 0 : ctx.reachableOrders().size(),
                        ctx == null ? 1 : Math.max(1, (int) Math.round(ctx.localDriverDensity()))),
                plan.getPostDropDemandProbability(),
                plan.getEmptyRiskAfter(),
                0,
                plan.getBorrowedDependencyScore() >= 0.25 ? 1 : 0,
                ctx == null ? 1 : Math.max(1, (int) Math.ceil(ctx.localPostDropOpportunity() * 3.0)),
                plan.getBorrowedDependencyScore());
        snapshot.put("runId", plan.getRunId());
        snapshot.put("decisionContextV2", decisionContextV2);
        snapshot.put("driverAccessCluster", driverAccessCluster);
        snapshot.put("zoneCoverageSnapshot", zoneCoverageSnapshot);
        snapshot.put("driverId", plan.getDriver().getId());
        snapshot.put("neuralPriorVersion", plan.getNeuralPriorVersion());
        snapshot.put("neuralPriorFreshnessMs", plan.getNeuralPriorFreshnessMs());
        snapshot.put("neuralPriorScore", plan.getNeuralPriorScore());
        snapshot.put("neuralPriorUsed", plan.isNeuralPriorUsed());
        snapshot.put("reachableOrders", ctx == null ? 0 : ctx.reachableOrders().size());
        snapshot.put("nearReadyOrders", ctx == null ? 0 : ctx.nearReadyOrders());
        snapshot.put("localTrafficIntensity", ctx == null ? 0.0 : ctx.localTrafficIntensity());
        snapshot.put("localTrafficForecast5m", ctx == null ? 0.0 : ctx.localTrafficForecast5m());
        snapshot.put("localTrafficForecast10m", ctx == null ? 0.0 : ctx.localTrafficForecast10m());
        snapshot.put("localWeatherForecast10m", ctx == null ? 0.0 : ctx.localWeatherForecast10m());
        snapshot.put("localDemandForecast5m", ctx == null ? 0.0 : ctx.localDemandForecast5m());
        snapshot.put("localShortageForecast10m", ctx == null ? 0.0 : ctx.localShortageForecast10m());
        snapshot.put("localMerchantPrepForecast10m", ctx == null ? 0.0 : ctx.localMerchantPrepForecast10m());
        snapshot.put("localBorrowSuccessProbability", ctx == null ? 0.0 : ctx.localBorrowSuccessProbability());
        snapshot.put("localShortagePressure", ctx == null ? 0.0 : ctx.localShortagePressure());
        snapshot.put("localPostDropOpportunity", ctx == null ? 0.0 : ctx.localPostDropOpportunity());
        snapshot.put("localEmptyZoneRisk", ctx == null ? 0.0 : ctx.localEmptyZoneRisk());
        snapshot.put("stressRegime", ctx == null ? StressRegime.NORMAL.name() : ctx.stressRegime().name());
        snapshot.put("harshWeatherStress", ctx != null && ctx.harshWeatherStress());
        snapshot.put("thirdOrderFeasibilityScore", ctx == null ? 0.0 : ctx.thirdOrderFeasibilityScore());
        snapshot.put("waveAssemblyPressure", ctx == null ? 0.0 : ctx.waveAssemblyPressure());
        return snapshot;
    }

    private Map<String, Object> semanticPlanSummary(DispatchPlan plan) {
        Map<String, Object> summary = new LinkedHashMap<>();
        RouteAlternative routeAlternative = new RouteAlternative(
                plan.getTraceId() + "-primary",
                plan.getServiceTier(),
                plan.getPredictedTotalMinutes(),
                plan.getPredictedTotalMinutes() * (1.0 + Math.max(0.05, plan.getLateRisk() * 0.6)),
                plan.getPredictedDeadheadKm(),
                clamp01(plan.getWeatherExposureScore()),
                clamp01(plan.getCongestionPenalty()),
                plan.getExpectedPostCompletionEmptyKm(),
                plan.getPostDropDemandProbability(),
                plan.getRoutePriorScore(),
                plan.getSelectionBucket().name().toLowerCase(),
                plan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                        || plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE ? 1 : 2);
        PolicyEvaluationRecord policyEvaluation = new PolicyEvaluationRecord(
                plan.getServiceTier(),
                plan.getExecutionScore(),
                plan.getContinuationScore(),
                plan.getCoverageScore(),
                plan.getRoutePriorScore(),
                plan.getSelectionBucket().name(),
                plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD,
                plan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                        || plan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE,
                plan.getNeuralPriorScore(),
                plan.isNeuralPriorUsed(),
                plan.isExecutionGatePassed() ? "accepted" : "execution_gate");
        summary.put("bundleSize", plan.getBundleSize());
        summary.put("totalScore", plan.getTotalScore());
        summary.put("onTimeProbability", plan.getOnTimeProbability());
        summary.put("lateRisk", plan.getLateRisk());
        summary.put("deliveryCorridorScore", plan.getDeliveryCorridorScore());
        summary.put("lastDropLandingScore", plan.getLastDropLandingScore());
        summary.put("expectedPostCompletionEmptyKm", plan.getExpectedPostCompletionEmptyKm());
        summary.put("remainingDropProximityScore", plan.getRemainingDropProximityScore());
        summary.put("zigZagPenalty", plan.getDeliveryZigZagPenalty());
        summary.put("waveLaunchEligible", plan.isWaveLaunchEligible());
        summary.put("stressFallbackOnly", plan.isStressFallbackOnly());
        summary.put("waitingForThirdOrder", plan.isWaitingForThirdOrder());
        summary.put("selectionBucket", plan.getSelectionBucket().name());
        summary.put("holdTtlRemaining", plan.getHoldRemainingCycles());
        summary.put("executionScore", plan.getExecutionScore());
        summary.put("continuationScore", plan.getContinuationScore());
        summary.put("coverageScore", plan.getCoverageScore());
        summary.put("futureScore", plan.getFutureScore());
        summary.put("postDropDemandProbability", plan.getPostDropDemandProbability());
        summary.put("trafficExposureScore", plan.getTrafficExposureScore());
        summary.put("weatherExposureScore", plan.getWeatherExposureScore());
        summary.put("neuralPriorScore", plan.getNeuralPriorScore());
        summary.put("neuralPriorUsed", plan.isNeuralPriorUsed());
        summary.put("neuralPriorVersion", plan.getNeuralPriorVersion());
        summary.put("neuralPriorFreshnessMs", plan.getNeuralPriorFreshnessMs());
        summary.put("graphAffinityScore", plan.getGraphAffinityScore());
        summary.put("graphExplanation", plan.getGraphExplanationTrace());
        summary.put("marginalDeadheadPerAddedOrder", plan.getMarginalDeadheadPerAddedOrder());
        summary.put("routeAlternative", routeAlternative);
        summary.put("policyEvaluation", policyEvaluation);
        summary.put("reason", buildDecisionReason(plan));
        return summary;
    }

    private FeatureSnapshotV2 toFeatureSnapshot(DispatchPlan plan,
                                                Map<String, Object> contextSnapshot,
                                                double[] contextFeatures) {
        DecisionContextV2 decisionContext = contextSnapshot.get("decisionContextV2") instanceof DecisionContextV2 ctx
                ? ctx
                : new DecisionContextV2(
                        plan.getRunId(),
                        executionProfile.name(),
                        plan.getServiceTier(),
                        plan.getDriver() == null ? null : field.cellKeyOf(plan.getDriver().getCurrentLocation()),
                        Instant.now(),
                        5,
                        10,
                        0.0,
                        plan.getContinuationValueScore(),
                        plan.getPostDropDemandProbability(),
                        plan.getEmptyRiskAfter(),
                        plan.getNeuralPriorVersion(),
                        plan.getNeuralPriorFreshnessMs());
        return new FeatureSnapshotV2(
                decisionContext.runId(),
                plan.getTraceId(),
                plan.getDriver() == null ? null : plan.getDriver().getId(),
                decisionContext.scenario(),
                decisionContext.serviceTier(),
                decisionContext.cellId(),
                decisionContext.featureTimestamp(),
                decisionContext.trafficHorizonMinutes(),
                decisionContext.weatherHorizonMinutes(),
                decisionContext.neuralPriorFreshnessMs(),
                contextFeatures == null ? new double[0] : contextFeatures.clone(),
                new LinkedHashMap<>(contextSnapshot)
        );
    }

    private double computeMerchantPrepRisk(DispatchPlan plan,
                                           double merchantPrepForecastMinutes,
                                           Instant currentTime) {
        double avgReadySlackMinutes = plan.getOrders().stream()
                .filter(order -> order.getPredictedReadyAt() != null)
                .mapToDouble(order -> Math.max(0.0,
                        Duration.between(currentTime, order.getPredictedReadyAt()).toSeconds() / 60.0))
                .average()
                .orElse(0.0);
        double avgHazard = plan.getOrders().stream()
                .mapToDouble(Order::getPickupDelayHazard)
                .average()
                .orElse(0.0);
        return clamp01(
                Math.min(1.0, merchantPrepForecastMinutes / 15.0) * 0.45
                        + Math.min(1.0, avgReadySlackMinutes / 8.0) * 0.25
                        + avgHazard * 0.30);
    }

    private double adjustDeadheadBudgetForServiceTier(String serviceTier, double baseBudgetKm) {
        return switch (DeliveryServiceTier.fromWireValue(serviceTier)) {
            case INSTANT -> Math.max(1.9, baseBudgetKm - 0.25);
            case TWO_HOUR -> baseBudgetKm + 0.20;
            case FOUR_HOUR, SCHEDULED -> baseBudgetKm + 0.35;
            case MULTI_STOP_COD -> baseBudgetKm + 0.15;
        };
    }

    private double serviceTierExecutionBias(String serviceTier,
                                            double onTimeProbability,
                                            double deadheadKm) {
        return switch (DeliveryServiceTier.fromWireValue(serviceTier)) {
            case INSTANT -> clamp01(onTimeProbability) * 0.03 - Math.max(0.0, deadheadKm - 2.4) * 0.02;
            case TWO_HOUR -> 0.01;
            case FOUR_HOUR, SCHEDULED -> 0.0;
            case MULTI_STOP_COD -> -0.01;
        };
    }

    private double serviceTierContinuationBias(String serviceTier,
                                               double postDropOpportunity,
                                               double lowEmptyFinish) {
        return switch (DeliveryServiceTier.fromWireValue(serviceTier)) {
            case INSTANT -> 0.0;
            case TWO_HOUR -> postDropOpportunity * 0.03;
            case FOUR_HOUR, SCHEDULED -> (postDropOpportunity * 0.03 + lowEmptyFinish * 0.02);
            case MULTI_STOP_COD -> lowEmptyFinish * 0.01;
        };
    }

    private void recordOutcomeFact(DispatchFactSink.OutcomeFact outcomeFact) {
        dispatchFactSink.recordOutcome(outcomeFact);
        PlatformRuntimeBootstrap.getCanonicalEventPublisher().publish(
                EventContractCatalog.DISPATCH_OUTCOME_V2,
                outcomeFact);
    }

    @Override
    public String agentId() {
        return "DispatchBrainAgent";
    }

    @Override
    public List<AgentToolDescriptor> describeTools() {
        return List.of(
                new AgentToolDescriptor("ForecastTool", "Demand, ETA, risk, and continuation inference", true, false),
                new AgentToolDescriptor("ContextTool", "Driver-local world modeling and zone context", true, false),
                new AgentToolDescriptor("RouteCacheTool", "Route realism, route-pending state, and route reuse", true, false),
                new AgentToolDescriptor("WaveAssemblyTool", "Pickup-wave construction and bundle policy control", true, false),
                new AgentToolDescriptor("SequenceTool", "Pickup/drop sequencing with corridor-aware routing", true, false),
                new AgentToolDescriptor("MatchingTool", "Conflict-free weighted assignment selection", true, false),
                new AgentToolDescriptor("ReDispatchTool", "Shock handling and resilience branch", true, false),
                new AgentToolDescriptor("FeatureStoreTool", "Online feature capture for replay and governance", false, false),
                new AgentToolDescriptor("ModelInferenceTool", "Champion/fallback model access via artifact provider", true, false),
                new AgentToolDescriptor("PolicyTool", "LLM shadow critique and policy explainability", false, true)
        );
    }

    public SpatiotemporalField getField() { return field; }
    public DecisionLog getDecisionLog() { return decisionLog; }
    public PolicySelector getPolicySelector() { return policySelector; }
    public String getActivePolicy() { return policySelector.getLastSelectedPolicy(); }
    public boolean isModelsWarmedUp() { return planRanker.isWarmedUp(); }

    private record ShadowAdviceEnvelope(
            String requestClass,
            int estimatedInputTokens,
            LLMAdvisorResponse response
    ) {}

    private static final class MutableDispatchRecoveryStats {
        private int generatedOrderPlanCount;
        private int generatedWaveCandidateCount;
        private int generatedExtensionCandidateCount;
        private int generatedFallbackCandidateCount;
        private int generatedHoldCandidateCount;
        private int shortlistedWaveCount;
        private int shortlistedFallbackCount;
        private int shortlistedHoldCount;
        private int solverSelectedWaveCount;
        private int solverSelectedExtensionCount;
        private int solverSelectedFallbackCount;
        private int solverSelectedHoldCount;
        private int fallbackInjectedCount;
        private int executedWaveCount;
        private int executedExtensionCount;
        private int executedFallbackCount;
        private int executedBorrowedCount;
        private int executedLocalCoverageCount;
        private int holdConvertedToWaveCount;
        private int holdExpiredToFallbackCount;
        private int prePickupAugmentConvertedCount;
        private int waveRejectedByDeadheadCount;
        private int waveRejectedBySlaCount;
        private int waveRejectedByConstraintCount;
        private int holdSuppressedByFallbackCount;
        private int borrowedPreferredOverLocalCount;

        private void recordGenerated(List<DispatchPlan> generatedPlans) {
            if (generatedPlans == null) {
                return;
            }
            for (DispatchPlan plan : generatedPlans) {
                if (plan.getOrders().isEmpty()) {
                    if (plan.isWaitingForThirdOrder()) {
                        generatedHoldCandidateCount++;
                    }
                    continue;
                }
                generatedOrderPlanCount++;
                if (plan.isWaveLaunchEligible() || plan.getBundleSize() >= 3) {
                    generatedWaveCandidateCount++;
                    if (plan.getBundleSize() < 3) {
                        generatedExtensionCandidateCount++;
                    }
                } else {
                    generatedFallbackCandidateCount++;
                }
            }
        }

        private void recordShortlisted(List<DispatchPlan> shortlist) {
            if (shortlist == null) {
                return;
            }
            for (DispatchPlan plan : shortlist) {
                switch (plan.getSelectionBucket()) {
                    case WAVE_LOCAL, EXTENSION_LOCAL -> shortlistedWaveCount++;
                    case HOLD_WAIT3 -> shortlistedHoldCount++;
                    case FALLBACK_LOCAL_LOW_DEADHEAD, BORROWED_COVERAGE, EMERGENCY_COVERAGE -> shortlistedFallbackCount++;
                }
            }
        }

        private void recordSolverSelection(List<DispatchPlan> selectedPlans) {
            if (selectedPlans == null) {
                return;
            }
            for (DispatchPlan plan : selectedPlans) {
                switch (plan.getSelectionBucket()) {
                    case WAVE_LOCAL -> solverSelectedWaveCount++;
                    case EXTENSION_LOCAL -> solverSelectedExtensionCount++;
                    case HOLD_WAIT3 -> solverSelectedHoldCount++;
                    default -> solverSelectedFallbackCount++;
                }
            }
        }

        private void recordExecuted(DispatchPlan plan) {
            if (plan == null || plan.getOrders().isEmpty()) {
                return;
            }
            switch (plan.getSelectionBucket()) {
                case WAVE_LOCAL -> executedWaveCount++;
                case EXTENSION_LOCAL -> executedExtensionCount++;
                case BORROWED_COVERAGE, EMERGENCY_COVERAGE -> {
                    executedFallbackCount++;
                    executedBorrowedCount++;
                }
                default -> executedFallbackCount++;
            }
            if (plan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD
                    || plan.getSelectionBucket() == SelectionBucket.WAVE_LOCAL
                    || plan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL) {
                executedLocalCoverageCount++;
            }
        }

        private DispatchRecoveryDecomposition toImmutable() {
            return new DispatchRecoveryDecomposition(
                    generatedOrderPlanCount,
                    generatedWaveCandidateCount,
                    generatedExtensionCandidateCount,
                    generatedFallbackCandidateCount,
                    generatedHoldCandidateCount,
                    shortlistedWaveCount,
                    shortlistedFallbackCount,
                    shortlistedHoldCount,
                    solverSelectedWaveCount,
                    solverSelectedExtensionCount,
                    solverSelectedFallbackCount,
                    solverSelectedHoldCount,
                    fallbackInjectedCount,
                    executedWaveCount,
                    executedExtensionCount,
                    executedFallbackCount,
                    executedBorrowedCount,
                    executedLocalCoverageCount,
                    holdConvertedToWaveCount,
                    holdExpiredToFallbackCount,
                    prePickupAugmentConvertedCount,
                    waveRejectedByDeadheadCount,
                    waveRejectedBySlaCount,
                    waveRejectedByConstraintCount,
                    holdSuppressedByFallbackCount,
                    borrowedPreferredOverLocalCount
            );
        }
    }

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
        dispatchSequence = 0;
        latestReplayRetrainLatencyMs = 0L;
        pendingTraceOutcomes.clear();
        holdTtlByDriver.clear();
        neuralRoutePriorClient.clear();
        activeRunId = "run-unset";
        graphShadowCacheEntry = GraphShadowCacheEntry.empty();
        legacyCoverageGuardrail.reset();
        applyGeneratorConfig();
    }

    static StressRegime classifyStressRegime(double trafficIntensity,
                                             WeatherProfile weather,
                                             double pickupWeatherExposure,
                                             double pickupCongestionExposure,
                                             double endWeatherExposure,
                                             double endCongestionExposure,
                                             double shortagePressure,
                                             int reachableBacklog) {
        double effectiveWeather = Math.max(pickupWeatherExposure, endWeatherExposure);
       double effectiveCongestion = Math.max(pickupCongestionExposure, endCongestionExposure);
        double backlogPressure = Math.min(1.0, reachableBacklog / 8.0);
        double stressScore = trafficIntensity * 0.28
                + effectiveWeather * 0.24
                + effectiveCongestion * 0.22
                + Math.min(1.0, shortagePressure) * 0.18
                + backlogPressure * 0.08;

        if (weather == WeatherProfile.STORM
                || effectiveWeather >= 0.92
                || effectiveCongestion >= 0.94
                || stressScore >= 0.84) {
            return StressRegime.SEVERE_STRESS;
        }
        if (weather == WeatherProfile.HEAVY_RAIN
                || trafficIntensity >= 0.58
                || effectiveCongestion >= 0.68
                || stressScore >= 0.60) {
            return StressRegime.STRESS;
        }
        return StressRegime.NORMAL;
    }

    static boolean isFallbackSafe(StressRegime regime,
                                  WeatherProfile weather,
                                  double lateSlackMinutes,
                                  double onTimeProbability,
                                  double deadheadKm,
                                  double pickupDelayHazard,
                                  double pickupReadySlackMinutes,
                                  double lateRisk) {
        double minSlack = regime == StressRegime.SEVERE_STRESS ? 1.0
                : regime == StressRegime.STRESS ? 0.0 : -1.0;
        double minOnTime = regime == StressRegime.SEVERE_STRESS ? 0.60
                : regime == StressRegime.STRESS ? 0.52 : 0.48;
        double maxDeadhead = regime == StressRegime.SEVERE_STRESS ? 2.0
                : regime == StressRegime.STRESS ? 2.5 : 2.9;
        double maxHazard = regime == StressRegime.SEVERE_STRESS ? 0.35
                : regime == StressRegime.STRESS ? 0.45 : 0.55;
        double maxReadySlack = regime == StressRegime.SEVERE_STRESS ? 1.0
                : regime == StressRegime.STRESS ? 2.5 : 4.0;

        if ((weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM)
                && lateSlackMinutes < minSlack) {
            return false;
        }
        if (onTimeProbability < minOnTime) {
            return false;
        }
        if (deadheadKm > maxDeadhead) {
            return false;
        }
        if (pickupDelayHazard > maxHazard) {
            return false;
        }
        if (pickupReadySlackMinutes > maxReadySlack) {
            return false;
        }
        return lateRisk <= (regime == StressRegime.SEVERE_STRESS ? 0.38
                : regime == StressRegime.STRESS ? 0.52 : 0.45);
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
        applyGeneratorConfig();
    }

    public ExecutionProfile getExecutionProfile() {
        return executionProfile;
    }

    public String getActiveRunId() {
        return activeRunId;
    }

    public void setActiveRunId(String runId) {
        this.activeRunId = (runId == null || runId.isBlank()) ? "run-unset" : runId;
    }

    public void setActiveRouteLatencyMode(String routeLatencyMode) {
        this.activeRouteLatencyMode = (routeLatencyMode == null || routeLatencyMode.isBlank())
                ? SimulationEngine.RouteLatencyMode.SIMULATED_ASYNC.name()
                : routeLatencyMode;
    }

    public long drainLatestReplayRetrainLatencyMs() {
        long sample = latestReplayRetrainLatencyMs;
        latestReplayRetrainLatencyMs = 0L;
        return sample;
    }

    public void setExecutionProfile(ExecutionProfile executionProfile) {
        this.executionProfile = executionProfile == null
                ? ExecutionProfile.MAINLINE_REALISTIC
                : executionProfile;
        applyGeneratorConfig();
    }

    private void applyGeneratorConfig() {
        planGenerator.setHoldPlansEnabled(ablationMode != AblationMode.NO_HOLD);
        planGenerator.setRepositionPlansEnabled(ablationMode != AblationMode.NO_REPOSITION);
        planGenerator.setSmallBatchOnly(ablationMode == AblationMode.SMALL_BATCH_ONLY);
        planGenerator.setExecutionProfile(executionProfile);
        PlatformRuntimeBootstrap.updateExecutionProfile(executionProfile.name());
    }

    private StressRegime elevateStressRegime(StressRegime baseRegime,
                                             double trafficIntensity,
                                             WeatherProfile weather,
                                             double pickupWeatherExposure,
                                             double pickupCongestionExposure,
                                             double endWeatherExposure,
                                             double endCongestionExposure,
                                             double shortagePressure) {
        StressRegime planRegime = classifyStressRegime(
                trafficIntensity,
                weather,
                pickupWeatherExposure,
                pickupCongestionExposure,
                endWeatherExposure,
                endCongestionExposure,
                shortagePressure,
                0);
        return StressRegime.max(baseRegime, planRegime);
    }

    private StressRegime deriveFallbackStressRegime(Order order,
                                                    double trafficIntensity,
                                                    WeatherProfile weather) {
        double pickupWeatherExposure = field.getWeatherExposureAt(order.getPickupPoint());
        double pickupCongestionExposure = field.getCongestionExposureAt(order.getPickupPoint());
        double endWeatherExposure = field.getWeatherExposureAt(order.getDropoffPoint());
        double endCongestionExposure = field.getCongestionExposureAt(order.getDropoffPoint());
        double shortagePressure = Math.max(
                field.getShortageAt(order.getPickupPoint()),
                field.getShortageAt(order.getDropoffPoint()));
        return classifyStressRegime(
                trafficIntensity,
                weather,
                pickupWeatherExposure,
                pickupCongestionExposure,
                endWeatherExposure,
                endCongestionExposure,
                shortagePressure,
                1);
    }

    private boolean isGlobalStress(double shortage,
                                   double trafficIntensity,
                                   WeatherProfile weather,
                                   int pendingOrders,
                                   int availableDrivers) {
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return true;
        }
        if (trafficIntensity >= 0.58 || shortage >= 0.70) {
            return true;
        }
        return pendingOrders > Math.max(8, availableDrivers * 2);
    }

    private boolean allowFallbackCoverage(double shortage,
                                          double trafficIntensity,
                                          WeatherProfile weather,
                                          int pendingOrders,
                                          int availableDrivers) {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return true;
        }
        if (pendingOrders <= 0 || availableDrivers <= 0) {
            return false;
        }
        if (weather == WeatherProfile.HEAVY_RAIN || weather == WeatherProfile.STORM) {
            return true;
        }
        if (pendingOrders == 1 && availableDrivers == 1) {
            return true;
        }
        return shortage >= 0.40
                || trafficIntensity >= 0.42
                || pendingOrders > availableDrivers + 1
                || pendingOrders >= Math.max(6, availableDrivers);
    }

    private boolean shouldApplyLegacyCoverageBackfill(String dominantServiceTier,
                                                      WeatherProfile weather,
                                                      List<Order> pendingOrders,
                                                      List<Driver> availableDrivers,
                                                      List<DispatchPlan> selectedPlans) {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return false;
        }
        if (weather != WeatherProfile.CLEAR && weather != WeatherProfile.LIGHT_RAIN) {
            return false;
        }
        if (!Objects.equals(DeliveryServiceTier.INSTANT.wireValue(), dominantServiceTier)) {
            return false;
        }
        if (pendingOrders == null || availableDrivers == null) {
            return false;
        }
        int feasibleUnits = Math.min(pendingOrders.size(), availableDrivers.size());
        if (feasibleUnits <= 0) {
            return false;
        }
        return countRealAssignedPlans(selectedPlans) < feasibleUnits;
    }

    private boolean shouldUseLegacyDispatchGuardrail(String dominantServiceTier,
                                                     WeatherProfile weather) {
        if (executionProfile != ExecutionProfile.MAINLINE_REALISTIC) {
            return false;
        }
        if (weather != WeatherProfile.CLEAR && weather != WeatherProfile.LIGHT_RAIN) {
            return false;
        }
        return Objects.equals(DeliveryServiceTier.INSTANT.wireValue(), dominantServiceTier);
    }

    private boolean legacyFallbackMode(double trafficIntensity, WeatherProfile weather) {
        if (ablationMode == AblationMode.NO_FALLBACK_TUNING) {
            return true;
        }
        return false;
    }

    private double computeFallbackRecoveryPriority(Order order,
                                                   Instant currentTime,
                                                   WeatherProfile weather) {
        double elapsedMinutes = Duration.between(order.getCreatedAt(), currentTime)
                .toSeconds() / 60.0;
        double readySlackMinutes = order.getPredictedReadyAt() != null
                ? Math.max(0.0, Duration.between(currentTime, order.getPredictedReadyAt()).toSeconds() / 60.0)
                : 0.0;
        double remainingSlack = order.getPromisedEtaMinutes()
                - elapsedMinutes
                - Math.min(4.0, readySlackMinutes);
        double weatherPenalty = switch (weather) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.3;
            case HEAVY_RAIN -> 0.8;
            case STORM -> 1.2;
        };
        double slackScore = Math.max(-6.0, Math.min(18.0, remainingSlack));
        double readinessScore = Math.max(-3.0, 2.5 - readySlackMinutes);
        double riskScore = (1.0 - Math.min(1.0, order.getPickupDelayHazard())) * 2.0
                + (1.0 - Math.min(1.0, order.getCancellationRisk())) * 1.2;
        double feeScore = Math.min(2.0, order.getQuotedFee() / 30000.0);
        return slackScore * 0.55
                + readinessScore * 0.20
                + riskScore * 0.18
                + feeScore * 0.07
                - weatherPenalty;
    }

    // ── Result records ──────────────────────────────────────────────────

    public record DispatchResult(
            List<DispatchPlan> plans,
            List<RepositionAgent.RepositionDecision> repositions,
            String policyUsed,
            int pendingOrderCount,
            int availableDriverCount,
            DispatchRecoveryDecomposition recovery,
            long dispatchDecisionLatencyMs,
            List<Long> modelInferenceLatencySamples,
            List<Long> neuralPriorLatencySamples,
            DispatchStageTimings stageTimings) {}

    public record ModelDiagnostics(
            long etaSamples, boolean etaWarmedUp,
            boolean lateWarmedUp, boolean cancelWarmedUp,
            boolean continuationWarmedUp, boolean rankerWarmedUp,
            int continuationPending,
            int logSize, int logCompleted,
            Map<String, Integer> policySelections) {}

    private static long nanosToMillis(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private static long nanosToRoundedMillis(long nanos) {
        return Math.max(0L, Math.round(nanos / 1_000_000.0));
    }

    private static final class StageTimingAccumulator {
        private long graphShadowProjectionMs;
        private long candidateGenerationMs;
        private long graphAffinityScoringNanos;
        private long optimizerSolveMs;
        private long fallbackInjectionMs;
        private long repositionSelectionMs;
        private boolean graphShadowCacheHit;
        private int generatedCandidateCount;
        private int fullyScoredCandidateCount;

        private DispatchStageTimings toImmutable(int availableDriverCount) {
            return new DispatchStageTimings(
                    graphShadowProjectionMs,
                    candidateGenerationMs,
                    nanosToRoundedMillis(graphAffinityScoringNanos),
                    optimizerSolveMs,
                    fallbackInjectionMs,
                    repositionSelectionMs,
                    graphShadowCacheHit,
                    generatedCandidateCount,
                    fullyScoredCandidateCount,
                    Math.max(0, availableDriverCount)
            );
        }
    }

    private record GraphShadowResolution(
            GraphShadowSnapshot snapshot,
            boolean cacheHit
    ) {}

    private record GraphShadowCacheEntry(
            String runId,
            String serviceTier,
            String signature,
            long dispatchSequence,
            GraphShadowSnapshot snapshot
    ) {
        private static GraphShadowCacheEntry empty() {
            return new GraphShadowCacheEntry(
                    "run-unset",
                    "instant",
                    "none",
                    -1L,
                    new GraphShadowSnapshot("run-unset", "dispatch-live", "instant", "in-memory-shadow",
                            List.of(), List.of(), List.of())
            );
        }

        private boolean matches(String activeRunId,
                                String dominantServiceTier,
                                String candidateSignature,
                                long currentDispatchSequence) {
            return Objects.equals(runId, activeRunId)
                    && Objects.equals(serviceTier, dominantServiceTier)
                    && Objects.equals(signature, candidateSignature)
                    && snapshot != null
                    && currentDispatchSequence - dispatchSequence <= GRAPH_SHADOW_CACHE_MAX_DISPATCH_AGE;
        }
    }

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
