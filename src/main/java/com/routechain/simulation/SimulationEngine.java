package com.routechain.simulation;

import com.routechain.baseline.NearestGreedyBaseline;
import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events.*;
import com.routechain.infra.DatabaseStorageService;
import com.routechain.infra.PlatformRuntimeBootstrap;
import com.routechain.ai.OmegaDispatchAgent;
import com.routechain.core.CompactCoreAdapter;
import com.routechain.core.CompactDispatchDecision;
import com.routechain.core.CompactEvidenceBundle;
import com.routechain.core.CompactSelectedPlanEvidence;
import com.routechain.core.WeightSnapshot;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core simulation engine — orchestrates all generators and dispatches
 * on a configurable tick loop. All events are published to EventBus.
 */
public class SimulationEngine {
    public enum DispatchMode { OMEGA, LEGACY, COMPACT }
    public enum RouteLatencyMode { SIMULATED_ASYNC, IMMEDIATE }

    private final EventBus eventBus = EventBus.getInstance();
    private final List<Region> regions;
    private final List<Driver> drivers = new CopyOnWriteArrayList<>();
    private final List<Order> activeOrders = new CopyOnWriteArrayList<>();
    private final List<Order> completedOrders = new CopyOnWriteArrayList<>();
    private final List<Order> cancelledOrders = new CopyOnWriteArrayList<>();
    private final Map<String, Double> corridorSeverity = new ConcurrentHashMap<>();
    private final List<HcmcCityData.TrafficCorridor> corridors;
    private final List<GeoPoint> pickupPoints;

    private final AtomicLong tickCounter = new AtomicLong(0);
    private final AtomicLong orderIdSeq = new AtomicLong(0);
    private ScheduledExecutorService scheduler;
    private volatile SimulationLifecycle lifecycle = SimulationLifecycle.IDLE;

    // Scenario parameters
    private volatile double trafficIntensity = 0.3;
    private volatile WeatherProfile weatherProfile = WeatherProfile.CLEAR;
    private volatile double demandMultiplier = 0.1; // 10% orders
    private volatile int simulatedHour = 12;
    private volatile int simulatedMinute = 0;
    private volatile int initialDriverCount = 5; // 5 drivers
    private final long randomSeed;
    private final Random rng;
    private static final int DEFAULT_START_HOUR = 12;
    private static final int DEFAULT_START_MINUTE = 0;

    // Timeline history buffer (last 1800 ticks = 30 simulated minutes)
    private final Deque<TimelineDataPoint> timelineHistory = new ConcurrentLinkedDeque<>();
    private static final int TIMELINE_HISTORY_MAX = 1800;

    // Metrics accumulators
    private volatile int totalDelivered = 0;
    private volatile int totalLateDelivered = 0;
    private volatile double totalDeadheadKm = 0;
    private volatile double totalEarnings = 0;
    private volatile long totalAssignmentLatencyMs = 0;
    private volatile long totalDispatchDecisionLatencyMs = 0;
    private volatile int totalAssignments = 0;
    private volatile int totalBundled = 0;
    private volatile int surgeEventsCounter = 0;
    private volatile int shortageEventsCounter = 0;
    private volatile double totalDeliveryCorridorScore = 0;
    private volatile double totalLastDropLandingScore = 0;
    private volatile double totalExpectedPostCompletionEmptyKm = 0;
    private volatile double totalExpectedNextOrderIdleMinutes = 0;
    private volatile double totalZigZagPenalty = 0;
    private volatile int routeMetricPlanCount = 0;
    private volatile int lastDropGoodZoneCount = 0;
    private volatile int visibleBundleThreePlusCount = 0;
    private volatile int cleanRegimeOrderDecisionCount = 0;
    private volatile int cleanRegimeSubThreeSelectedCount = 0;
    private volatile int cleanRegimeWaveAssemblyHoldCount = 0;
    private volatile int cleanRegimeThirdOrderLaunchCount = 0;
    private volatile int stressDowngradeSelectionCount = 0;
    private volatile int totalSelectedOrderPlanCount = 0;
    private volatile int realAssignedPlanCount = 0;
    private volatile int holdOnlySelectionCount = 0;
    private volatile int prePickupAugmentationCount = 0;
    private volatile double borrowedExecutedDeadheadKm = 0.0;
    private volatile double fallbackExecutedDeadheadKm = 0.0;
    private volatile double waveExecutedDeadheadKm = 0.0;
    private volatile int postDropOpportunityCount = 0;
    private volatile int postDropOrderHitCount = 0;
    private volatile double totalPredictedPostDropOpportunity = 0.0;
    private volatile double totalTrafficForecastAbsError = 0.0;
    private volatile double totalWeatherForecastHitRate = 0.0;
    private volatile int forecastDecisionCount = 0;
    private volatile double totalBorrowSuccessCalibrationGap = 0.0;
    private volatile int borrowSuccessCalibrationCount = 0;
    private volatile DispatchRecoveryDecomposition recoveryAccumulator =
            DispatchRecoveryDecomposition.empty();
    private final List<Long> dispatchDecisionLatencySamples = new CopyOnWriteArrayList<>();
    private final List<Long> modelInferenceLatencySamples = new CopyOnWriteArrayList<>();
    private final List<Long> neuralPriorLatencySamples = new CopyOnWriteArrayList<>();
    private final List<DispatchStageTimings> dispatchStageTimingSamples = new CopyOnWriteArrayList<>();
    private final List<Long> replayRetrainLatencySamples = new CopyOnWriteArrayList<>();
    private final List<Long> assignmentAgingLatencySamples = new CopyOnWriteArrayList<>();
    private final Set<String> noDriverFoundOrderIds = ConcurrentHashMap.newKeySet();
    private final Set<String> legacyGuardrailDriverIds = ConcurrentHashMap.newKeySet();
    private final AtomicLong runSequence = new AtomicLong(0);
    private static final AtomicLong GLOBAL_RUN_SEQUENCE = new AtomicLong(0);
    private volatile String currentSessionId = "SESSION-UNSET";
    private volatile String currentRunId = "RUN-UNINITIALIZED";
    private volatile long runWallClockStartedNanos = System.nanoTime();

    // AI dispatch agent (Omega — learned multi-agent brain)
    private final OmegaDispatchAgent omegaAgent;
    private final DispatchAgent legacyDispatchAgent;
    private final NearestGreedyBaseline nearestGreedyBaseline = new NearestGreedyBaseline();
    private final CompactRuntimeCoordinator compactRuntimeCoordinator;
    private final ReDispatchEngine reDispatchEngine = new ReDispatchEngine();
    private final OsrmRoutingService routingService = new OsrmRoutingService();
    private final DatabaseStorageService dbService = new DatabaseStorageService();
    private volatile DispatchMode dispatchMode = DispatchMode.OMEGA;
    private volatile boolean legacyNearestGreedyMode = false;

    // Enhancements
    private final DriverSupplyEngine driverSupplyEngine = new DriverSupplyEngine();
    private final ScenarioShockEngine shockEngine = new ScenarioShockEngine();
    private final SimulationClock clock;
    private final OrderArrivalEngine orderArrivalEngine;
    private final DriverMotionEngine driverMotionEngine;
    private final MerchantWaitEngine merchantWaitEngine;
    private volatile RouteLatencyMode routeLatencyMode = RouteLatencyMode.SIMULATED_ASYNC;
    private static final int LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE = 5;
    private static final int LOCAL_MINI_DISPATCH_MAX_CANDIDATES = 10;
    private static final double LOCAL_MINI_DISPATCH_MAX_DETOUR_KM = 1.1;
    private static final double LOCAL_MINI_DISPATCH_MAX_FIRST_PICKUP_DELAY_MIN = 4.0;
    private volatile boolean miniDispatchRequested = false;
    private final Map<String, Integer> holdCyclesByDriver = new ConcurrentHashMap<>();
    private final Map<String, Long> postDropIdleTickByDriver = new ConcurrentHashMap<>();

    public SimulationEngine() {
        this(42L);
    }

    public SimulationEngine(long seed) {
        this.randomSeed = seed;
        this.rng = new Random(seed);
        this.currentSessionId = "SESSION-s" + seed;
        this.currentRunId = nextRunId();
        PlatformRuntimeBootstrap.ensureInitialized(eventBus);
        this.regions = new ArrayList<>(HcmcCityData.createRegions());
        this.corridors = HcmcCityData.createCorridors();
        this.pickupPoints = HcmcCityData.createPickupPoints();
        for (var c : corridors) {
            corridorSeverity.put(c.id(), 0.0);
        }
        this.clock = new SimulationClock(DEFAULT_START_HOUR, DEFAULT_START_MINUTE);
        this.orderArrivalEngine = new OrderArrivalEngine(regions, shockEngine, rng);
        this.orderArrivalEngine.setManualDemandMultiplier(demandMultiplier);
        
        // Find intersection points (simple heuristic: centers of all regions)
        List<GeoPoint> intersections = regions.stream().map(Region::getCenter).toList();
        this.driverMotionEngine = new DriverMotionEngine(rng, intersections);
        this.merchantWaitEngine = new MerchantWaitEngine(rng);
        
        this.omegaAgent = new OmegaDispatchAgent(regions);
        this.legacyDispatchAgent = new DispatchAgent(regions);
        this.compactRuntimeCoordinator = new CompactRuntimeCoordinator();
    }

    // ── Lifecycle ───────────────────────────────────────────────────────
    public void start() {
        if (lifecycle == SimulationLifecycle.RUNNING) return;

        currentRunId = nextRunId();
        omegaAgent.setActiveRunId(currentRunId);
        omegaAgent.setActiveRouteLatencyMode(routeLatencyMode.name());
        runWallClockStartedNanos = System.nanoTime();
        initializeDrivers(initialDriverCount);
        lifecycle = SimulationLifecycle.RUNNING;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-engine");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, 1000, TimeUnit.MILLISECONDS);
        eventBus.publish(new SimulationStarted(clock.currentInstant()));
    }

    public void stop() {
        lifecycle = SimulationLifecycle.IDLE;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        // Generate run report on stop
        generateRunReport();
        eventBus.publish(new SimulationStopped(clock.currentInstant()));
    }

    public void reset() {
        stop();
        rng.setSeed(randomSeed);
        drivers.clear();
        activeOrders.clear();
        completedOrders.clear();
        cancelledOrders.clear();
        tickCounter.set(0);
        orderIdSeq.set(0);
        simulatedHour = DEFAULT_START_HOUR;
        simulatedMinute = DEFAULT_START_MINUTE;
        clock.reset(DEFAULT_START_HOUR, DEFAULT_START_MINUTE);
        orderArrivalEngine.reset();
        orderArrivalEngine.setManualDemandMultiplier(demandMultiplier);
        totalDelivered = 0;
        totalLateDelivered = 0;
        totalDeadheadKm = 0;
        totalEarnings = 0;
        totalAssignmentLatencyMs = 0;
        totalDispatchDecisionLatencyMs = 0;
        totalAssignments = 0;
        totalBundled = 0;
        surgeEventsCounter = 0;
        shortageEventsCounter = 0;
        totalDeliveryCorridorScore = 0;
        totalLastDropLandingScore = 0;
        totalExpectedPostCompletionEmptyKm = 0;
        totalExpectedNextOrderIdleMinutes = 0;
        totalZigZagPenalty = 0;
        routeMetricPlanCount = 0;
        lastDropGoodZoneCount = 0;
        visibleBundleThreePlusCount = 0;
        cleanRegimeOrderDecisionCount = 0;
        cleanRegimeSubThreeSelectedCount = 0;
        cleanRegimeWaveAssemblyHoldCount = 0;
        cleanRegimeThirdOrderLaunchCount = 0;
        stressDowngradeSelectionCount = 0;
        totalSelectedOrderPlanCount = 0;
        realAssignedPlanCount = 0;
        holdOnlySelectionCount = 0;
        prePickupAugmentationCount = 0;
        borrowedExecutedDeadheadKm = 0.0;
        fallbackExecutedDeadheadKm = 0.0;
        waveExecutedDeadheadKm = 0.0;
        postDropOpportunityCount = 0;
        postDropOrderHitCount = 0;
        totalPredictedPostDropOpportunity = 0.0;
        totalTrafficForecastAbsError = 0.0;
        totalWeatherForecastHitRate = 0.0;
        forecastDecisionCount = 0;
        totalBorrowSuccessCalibrationGap = 0.0;
        borrowSuccessCalibrationCount = 0;
        recoveryAccumulator = DispatchRecoveryDecomposition.empty();
        dispatchDecisionLatencySamples.clear();
        modelInferenceLatencySamples.clear();
        neuralPriorLatencySamples.clear();
        dispatchStageTimingSamples.clear();
        replayRetrainLatencySamples.clear();
        assignmentAgingLatencySamples.clear();
        noDriverFoundOrderIds.clear();
        legacyGuardrailDriverIds.clear();
        miniDispatchRequested = false;
        holdCyclesByDriver.clear();
        postDropIdleTickByDriver.clear();
        compactRuntimeCoordinator.reset();
        currentRunId = nextRunId();
        runWallClockStartedNanos = System.nanoTime();
        omegaAgent.setActiveRunId(currentRunId);
        omegaAgent.setActiveRouteLatencyMode(routeLatencyMode.name());
        omegaAgent.reset();
        legacyDispatchAgent.reset();
        reDispatchEngine.reset();
        routingService.reset();
        eventBus.publish(new SimulationReset(clock.currentInstant()));
    }

    public SimulationLifecycle getLifecycle() { return lifecycle; }
    public List<Driver> getDrivers() { return Collections.unmodifiableList(drivers); }
    public List<Order> getActiveOrders() { return Collections.unmodifiableList(activeOrders); }
    public List<Region> getRegions() { return Collections.unmodifiableList(regions); }
    public Map<String, Double> getCorridorSeverity() { return Collections.unmodifiableMap(corridorSeverity); }
    public int getSimulatedHour() { return simulatedHour; }
    public int getSimulatedMinute() { return simulatedMinute; }
    public String getSimulatedTimeFormatted() {
        return String.format("%02d:%02d", simulatedHour, simulatedMinute);
    }
    public String getCurrentSessionId() { return currentSessionId; }
    public String getCurrentRunId() { return currentRunId; }
    public RunIdentity getRunIdentity() {
        return new RunIdentity(currentSessionId, currentRunId);
    }
    public long getTickCount() { return tickCounter.get(); }
    public List<TimelineDataPoint> getTimelineHistory() {
        return List.copyOf(timelineHistory);
    }
    public int getTotalDelivered() { return totalDelivered; }
    public int getTotalCancelled() { return cancelledOrders.size(); }
    public OmegaDispatchAgent getOmegaAgent() { return omegaAgent; }
    public SimulationClock getClock() { return clock; }
    public DispatchMode getDispatchMode() { return dispatchMode; }
    public void setDispatchMode(DispatchMode dispatchMode) {
        this.dispatchMode = dispatchMode == null ? DispatchMode.OMEGA : dispatchMode;
    }
    public boolean isLegacyNearestGreedyMode() { return legacyNearestGreedyMode; }
    public void setLegacyNearestGreedyMode(boolean legacyNearestGreedyMode) {
        this.legacyNearestGreedyMode = legacyNearestGreedyMode;
    }
    public CompactEvidenceBundle getLatestCompactEvidence() { return compactRuntimeCoordinator.latestEvidence(); }
    public WeightSnapshot getCurrentCompactWeightSnapshot() {
        return compactRuntimeCoordinator.currentWeightSnapshot();
    }
    public CompactRuntimeStatusView getCurrentCompactStatus() { return compactRuntimeCoordinator.latestStatus(); }
    public void setOmegaAblationMode(OmegaDispatchAgent.AblationMode ablationMode) {
        omegaAgent.setAblationMode(ablationMode);
    }
    public OmegaDispatchAgent.ExecutionProfile getExecutionProfile() {
        return omegaAgent.getExecutionProfile();
    }
    public void setExecutionProfile(OmegaDispatchAgent.ExecutionProfile executionProfile) {
        omegaAgent.setExecutionProfile(executionProfile);
    }
    public RouteLatencyMode getRouteLatencyMode() {
        return routeLatencyMode;
    }
    public void setRouteLatencyMode(RouteLatencyMode routeLatencyMode) {
        this.routeLatencyMode = routeLatencyMode == null
                ? RouteLatencyMode.SIMULATED_ASYNC
                : routeLatencyMode;
        omegaAgent.setActiveRouteLatencyMode(this.routeLatencyMode.name());
    }

    // ── Metric Getters & Headless Execution ─────────────────────────────
    public int getTotalLateDelivered() { return totalLateDelivered; }
    public double getTotalDeadheadKm() { return totalDeadheadKm; }
    public double getTotalEarnings() { return totalEarnings; }
    public long getTotalAssignmentLatencyMs() { return totalAssignmentLatencyMs; }
    public int getTotalAssignments() { return totalAssignments; }
    public int getTotalBundled() { return totalBundled; }
    public int getSurgeEventsCounter() { return surgeEventsCounter; }
    public int getShortageEventsCounter() { return shortageEventsCounter; }

    /** Run headless (sync) tick */
    public void tickHeadless() {
        if (lifecycle != SimulationLifecycle.RUNNING) {
            lifecycle = SimulationLifecycle.RUNNING;
            currentRunId = nextRunId();
            omegaAgent.setActiveRunId(currentRunId);
            omegaAgent.setActiveRouteLatencyMode(routeLatencyMode.name());
            runWallClockStartedNanos = System.nanoTime();
            routingService.setHeadlessMode(true);
            initializeDrivers(initialDriverCount);
        }
        tick();
    }

    // ── Scenario config ─────────────────────────────────────────────────
    public void setTrafficIntensity(double v) { this.trafficIntensity = Math.max(0, Math.min(1, v)); }
    public void setWeatherProfile(WeatherProfile wp) { this.weatherProfile = wp; }
    public void setDemandMultiplier(double dm) {
        this.demandMultiplier = dm;
        orderArrivalEngine.setManualDemandMultiplier(dm);
    }
    public void setInitialDriverCount(int count) { this.initialDriverCount = count; }
    public void setSimulationStartTime(int hour, int minute) {
        int safeHour = Math.max(0, Math.min(23, hour));
        int safeMinute = Math.max(0, Math.min(59, minute));
        this.simulatedHour = safeHour;
        this.simulatedMinute = safeMinute;
        this.clock.reset(safeHour, safeMinute);
    }
    public DriverSupplyEngine getDriverSupplyEngine() { return driverSupplyEngine; }
    public ScenarioShockEngine getShockEngine() { return shockEngine; }
    public double getTrafficIntensity() { return trafficIntensity; }
    public WeatherProfile getWeatherProfile() { return weatherProfile; }
    public double getDemandMultiplier() { return demandMultiplier; }
    public int getInitialDriverCount() { return initialDriverCount; }
    public long getRandomSeed() { return randomSeed; }

    // ── Manual inject API (for editor panel) ────────────────────────────

    /**
     * Inject a manually-created order into the active simulation.
     */
    public void injectOrder(GeoPoint pickup, GeoPoint dropoff, double fee, int promisedEtaMin) {
        String id = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        String custId = "CUS-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
        String pickupRegionId = findNearestRegionId(pickup);
        String dropoffRegionId = findNearestRegionId(dropoff);

        Order order = new Order(id, custId, pickupRegionId, pickup, dropoff,
                dropoffRegionId, fee, promisedEtaMin, clock.currentInstant());
        order.setServiceType("instant");
        activeOrders.add(order);
        eventBus.publish(new OrderCreated(order));
        dbService.saveOrder(order);
        tryImmediatePrePickupAugmentForInjectedOrder(order);
        if (dispatchMode == DispatchMode.OMEGA && hasPrePickupAugmentableDrivers()) {
            miniDispatchRequested = true;
        }
        System.out.println("[Editor] Injected order " + id + " at " +
                String.format("(%.5f, %.5f)", pickup.lat(), pickup.lng()));
    }

    /**
     * Inject a manually-created driver into the simulation.
     */
    public void injectDriver(GeoPoint location) {
        int seq = drivers.size() + 1;
        String id = "DMANUAL-" + seq;
        String name = "Manual D" + seq;
        String regionId = findNearestRegionId(location);
        VehicleType vehicle = VehicleType.MOTORBIKE;

        Driver driver = new Driver(id, name, location, regionId, vehicle);
        drivers.add(driver);
        System.out.println("[Editor] Injected driver " + id + " at " +
                String.format("(%.5f, %.5f)", location.lat(), location.lng()));
    }

    private String findNearestRegionId(GeoPoint point) {
        double minDist = Double.MAX_VALUE;
        String nearestId = regions.isEmpty() ? "unknown" : regions.get(0).getId();
        for (Region r : regions) {
            double dist = point.distanceTo(r.getCenter());
            if (dist < minDist) {
                minDist = dist;
                nearestId = r.getId();
            }
        }
        return nearestId;
    }

    private boolean hasPrePickupAugmentableDrivers() {
        return drivers.stream().anyMatch(this::isOmegaPrePickupAugmentableDriver);
    }

    private void tryImmediatePrePickupAugmentForInjectedOrder(Order order) {
        if (order == null || dispatchMode != DispatchMode.OMEGA) {
            return;
        }

        Driver candidate = drivers.stream()
                .filter(driver -> !driver.hasCompletedFirstPickup())
                .filter(driver -> driver.getCurrentOrderCount() >= 3
                        && driver.getCurrentOrderCount() < LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE)
                .filter(driver -> driver.getState() == DriverState.ROUTE_PENDING
                        || driver.getState() == DriverState.PICKUP_EN_ROUTE
                        || driver.getState() == DriverState.WAITING_PICKUP)
                .filter(driver -> isImmediateInjectedAugmentReachable(driver, order))
                .min(Comparator.comparingDouble(driver ->
                        immediateInjectedAugmentReachKm(driver, order)))
                .orElse(null);
        if (candidate == null || candidate.getActiveOrderIds().contains(order.getId())) {
            return;
        }

        List<DispatchPlan.Stop> baseSequence = remainingSequence(candidate);
        if (baseSequence.isEmpty()) {
            return;
        }

        order.assignDriver(candidate.getId(), clock.currentInstant());
        candidate.addOrder(order.getId());

        List<DispatchPlan.Stop> mergedSequence = new ArrayList<>(baseSequence);
        DispatchPlan.Stop tail = mergedSequence.get(mergedSequence.size() - 1);
        GeoPoint anchor = tail.location();
        double etaCursor = tail.estimatedArrivalMinutes();

        double toPickupKm = anchor.distanceTo(order.getPickupPoint()) / 1000.0;
        etaCursor += Math.max(0.8, toPickupKm / 18.0 * 60.0);
        mergedSequence.add(new DispatchPlan.Stop(
                order.getId(), order.getPickupPoint(), DispatchPlan.Stop.StopType.PICKUP, etaCursor));

        double toDropKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
        etaCursor += Math.max(1.2, toDropKm / 22.0 * 60.0);
        mergedSequence.add(new DispatchPlan.Stop(
                order.getId(), order.getDropoffPoint(), DispatchPlan.Stop.StopType.DROPOFF, etaCursor));

        candidate.replaceAssignedSequenceBeforeFirstPickup(List.copyOf(mergedSequence));
        candidate.clearRouteWaypoints();
        queueDriverRoute(candidate, mergedSequence.get(0).location(), DriverState.PICKUP_EN_ROUTE, 0);
        prePickupAugmentationCount++;
        miniDispatchRequested = true;
    }

    // ── Core tick ───────────────────────────────────────────────────────
    private void tick() {
        try {
            clock.advanceSubTick();
            long subTick = clock.getSubTickCounter();
            tickCounter.set(subTick);
            
            // 1. Movement Sub-tick (Every 5 simulated seconds)
            if (clock.isMovementBoundary()) {
                routingService.advanceSimulationSubTick();
                activateReadyRoutes();
                if (miniDispatchRequested
                        && dispatchMode == DispatchMode.OMEGA
                        && hasPrePickupAugmentableDrivers()) {
                    dispatchPendingOrders(true);
                } else if (miniDispatchRequested && dispatchMode == DispatchMode.COMPACT) {
                    dispatchPendingOrders(true);
                }
                moveDrivers();
                processDeliveries();
                resolveExpiredCompactDecisions(clock.currentInstant());
                if (miniDispatchRequested && dispatchMode == DispatchMode.OMEGA) {
                    dispatchPendingOrders(true);
                } else if (miniDispatchRequested && dispatchMode == DispatchMode.COMPACT) {
                    dispatchPendingOrders(true);
                }
                
                // We record snapshot in movement ticks for smooth animation, 
                // but compute heavy metrics in decision ticks
                if (subTick % (SimulationClock.SUB_TICKS_PER_DECISION / 2) == 0) {
                    recordTimelineSnapshot(subTick);
                }
            }

            // 2. Decision Tick (Every 30 simulated seconds)
            if (clock.isDecisionBoundary()) {
                simulatedHour = clock.getSimulatedHour();
                simulatedMinute = clock.getSimulatedMinute();
                long decTick = clock.getElapsedMinutes();

                evolveWeather();
                evolveTraffic();
                
                // Apply driver shifts & spawning
                driverSupplyEngine.evaluateShifts(drivers, regions, clock, weatherProfile, demandMultiplier);
                
                generateOrders();
                trackDriverProductivity();
                checkReDispatch();
                dispatchPendingOrders(false);
                
                if (dispatchMode == DispatchMode.OMEGA) {
                    omegaAgent.onTick(decTick, driverId -> drivers.stream()
                            .filter(d -> d.getId().equals(driverId))
                            .findFirst()
                            .map(Driver::getAvgEarningPerHour)
                            .orElse(0.0));
                    long replayRetrainLatencyMs = omegaAgent.drainLatestReplayRetrainLatencyMs();
                    if (replayRetrainLatencyMs > 0L) {
                        replayRetrainLatencySamples.add(replayRetrainLatencyMs);
                    }
                }
                
                detectSurges();
                computeMetrics();
            }

            // Traffic refresh (Every 60 simulated seconds)
            if (clock.isTrafficRefreshBoundary()) {
                // Potential high-res traffic update here
            }

            eventBus.publish(new SimulationTick(subTick, clock.currentInstant()));
        } catch (Exception e) {
            System.err.println("[SimEngine] Tick error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ── Initialization ──────────────────────────────────────────────────
    private void initializeDrivers(int count) {
        String[] names = HcmcCityData.driverNames();
        for (int i = 0; i < count && i < names.length; i++) {
            Region region = regions.get(rng.nextInt(regions.size()));
            GeoPoint location = randomPointInRegion(region);
            VehicleType vt = rng.nextDouble() > 0.3 ? VehicleType.MOTORBIKE : VehicleType.CAR;
            Driver d = new Driver("D" + (i + 1), names[i], location, region.getId(), vt);
            driverSupplyEngine.initializeDriver(d);
            drivers.add(d);
            eventBus.publish(new DriverOnline(d.getId()));
        }
    }

    // ── Weather evolution ───────────────────────────────────────────────
    private void evolveWeather() {
        double weatherIntensity = switch (weatherProfile) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.3;
            case HEAVY_RAIN -> 0.7;
            case STORM -> 0.95;
        };

        for (Region region : regions) {
            // Add per-region variation
            double variation = (rng.nextGaussian() * 0.1);
            double intensity = Math.max(0, Math.min(1, weatherIntensity + variation));
            region.setRainIntensity(intensity);
            eventBus.publish(new WeatherChanged(region.getId(), weatherProfile, intensity));
        }
    }

    // ── Traffic evolution ────────────────────────────────────────────────
    private void evolveTraffic() {
        double hourFactor = HcmcCityData.hourlyMultiplier(simulatedHour) / 2.0;
        double weatherFactor = weatherProfile == WeatherProfile.HEAVY_RAIN ? 0.3
                : weatherProfile == WeatherProfile.STORM ? 0.5 : 0.0;

        for (var corridor : corridors) {
            double baseSeverity = trafficIntensity * hourFactor;
            double noise = rng.nextGaussian() * 0.08;
            double severity = Math.max(0, Math.min(1,
                    baseSeverity + weatherFactor + noise));
            corridorSeverity.put(corridor.id(), severity);

            eventBus.publish(new TrafficSegmentUpdated(
                    corridor.id(), corridor.from(), corridor.to(), severity));
        }

        for (Region region : regions) {
            double avgSeverity = corridorSeverity.values().stream()
                    .mapToDouble(Double::doubleValue).average().orElse(0);
            region.setCongestionScore(Math.min(1, avgSeverity + rng.nextGaussian() * 0.05));
            eventBus.publish(new TrafficUpdated(region.getId(), region.getCongestionScore()));
        }
    }

    private void generateOrders() {
        int currentPending = (int) activeOrders.stream()
                .filter(o -> o.getStatus() == com.routechain.domain.Enums.OrderStatus.CONFIRMED 
                          || o.getStatus() == com.routechain.domain.Enums.OrderStatus.PENDING_ASSIGNMENT)
                .count();
        int maxPending = drivers.size() * 5;

        if (currentPending >= maxPending) return;

        List<Order> newOrders = orderArrivalEngine.generateOrders(
                clock.getSimulatedHour(), weatherProfile,
                clock.getSubTickCounter(), clock.currentInstant());
        
        for (Order order : newOrders) {
            if (currentPending >= maxPending) break;

            Region pickupRegion = regions.stream()
                    .filter(r -> r.getId().equals(order.getPickupRegionId()))
                    .findFirst().orElse(regions.get(0));
            
            merchantWaitEngine.assignMerchantTiming(order, pickupRegion);
            
            activeOrders.add(order);
            pickupRegion.setCurrentDemandPressure(pickupRegion.getCurrentDemandPressure() + 1);
            eventBus.publish(new OrderCreated(order));
            dbService.saveOrder(order);
            currentPending++;
        }
    }

    private Order createRandomOrder(Region region) {
        long seq = orderIdSeq.incrementAndGet();
        GeoPoint pickup = randomPointInRegion(region);
        Region dropoffRegion = regions.get(rng.nextInt(regions.size()));
        GeoPoint dropoff = randomPointInRegion(dropoffRegion);

        double dist = pickup.distanceTo(dropoff);
        double fee = 12000 + dist * 5; // VND
        int eta = (int) (dist / 500 * (1 + trafficIntensity)); // rough ETA

        return new Order(java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(),
                "CUS-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase(),
                region.getId(), pickup, dropoff, dropoffRegion.getId(),
                fee, Math.max(5, eta), clock.currentInstant());
    }

    public void spawnRandomOrders(int count) {
        if (regions.isEmpty()) return;
        int activePending = (int) activeOrders.stream()
                .filter(o -> o.getStatus() == com.routechain.domain.Enums.OrderStatus.CONFIRMED 
                          || o.getStatus() == com.routechain.domain.Enums.OrderStatus.PENDING_ASSIGNMENT)
                .count();
        int maxPending = drivers.size() * 5;

        for (int i = 0; i < count; i++) {
            if (activePending >= maxPending) break;
            Region region = regions.get(rng.nextInt(regions.size()));
            Order order = createRandomOrder(region);
            activeOrders.add(order);
            region.setCurrentDemandPressure(region.getCurrentDemandPressure() + 1);
            eventBus.publish(new OrderCreated(order));
            dbService.saveOrder(order);
            activePending++;
        }
    }

    private int computeRouteLatencyTicks() {
        if (routeLatencyMode == RouteLatencyMode.IMMEDIATE) {
            return 0;
        }

        int ticks = 1;
        if (trafficIntensity >= 0.45) {
            ticks++;
        }
        if (weatherProfile == WeatherProfile.HEAVY_RAIN
                || weatherProfile == WeatherProfile.STORM) {
            ticks++;
        }
        return Math.max(1, Math.min(3, ticks));
    }

    private void queueDriverRoute(Driver driver,
                                  GeoPoint target,
                                  DriverState activeState) {
        queueDriverRoute(driver, target, activeState, -1);
    }

    private void queueDriverRoute(Driver driver,
                                  GeoPoint target,
                                  DriverState activeState,
                                  int sequenceIndex) {
        if (driver == null || target == null || activeState == null) {
            return;
        }

        String requestId = "ROUTE-" + driver.getId() + "-"
                + clock.getSubTickCounter() + "-"
                + UUID.randomUUID().toString().substring(0, 6);
        int latencyTicks = computeRouteLatencyTicks();
        driver.prepareRouteRequest(requestId, target, activeState, latencyTicks, sequenceIndex);
        driver.setState(DriverState.ROUTE_PENDING);
        routingService.requestRouteAsync(
                driver,
                driver.getCurrentLocation(),
                target,
                requestId,
                latencyTicks);
    }

    private void activateReadyRoutes() {
        for (Driver driver : drivers) {
            if (driver.getState() != DriverState.ROUTE_PENDING) {
                continue;
            }

            DriverState previousState = driver.getState();
            driver.tickRouteLatency();
            if (!driver.isRouteReadyForActivation()) {
                continue;
            }

            DriverState activeState = driver.activatePendingRoute();
            driver.setState(activeState);
            if (activeState == DriverState.PICKUP_EN_ROUTE) {
                markPickupLegStarted(driver, clock.currentInstant());
                if (!driver.isRouteLockedAfterFirstPickup()
                        && !isLegacyGuardrailDriver(driver)
                        && driver.getCurrentOrderCount() < LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE) {
                    miniDispatchRequested = true;
                }
            }
            eventBus.publish(new DriverStateChanged(
                    driver.getId(),
                    previousState,
                    activeState));
        }
    }

    // ── Driver movement (Delegated to DriverMotionEngine) ──────────────
    private void moveDrivers() {
        double globalTraffic = trafficIntensity;
        for (Driver driver : drivers) {
            if (driver.getState() == DriverState.OFFLINE) continue;

            driverMotionEngine.moveDriver(driver, weatherProfile, globalTraffic, SimulationClock.SUB_TICK_SECONDS);

            eventBus.publish(new DriverLocationUpdated(
                    driver.getId(), driver.getCurrentLocation(), driver.getSpeedKmh()));
        }
    }

    // ── RouteChain Omega dispatch pipeline ──────────────────────────────
    private void dispatchPendingOrders() {
        dispatchPendingOrders(false);
    }

    private void dispatchPendingOrders(boolean miniDispatch) {
        List<Order> pending = activeOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED
                        || o.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .toList();

        if (pending.isEmpty()) {
            if (miniDispatch) {
                miniDispatchRequested = false;
            }
            return;
        }

        // Mark as pending
        for (Order order : pending) {
            order.setStatus(OrderStatus.PENDING_ASSIGNMENT);
        }

        List<Driver> available = drivers.stream()
                .filter(dispatchMode == DispatchMode.OMEGA
                        ? this::isDispatchEligibleDriver
                        : Driver::isAvailable)
                .toList();
        if (miniDispatch && dispatchMode == DispatchMode.OMEGA) {
            available = selectMiniDispatchDrivers(pending, available);
            pending = selectMiniDispatchOrders(pending, available);
        }

        if (available.isEmpty() || pending.isEmpty()) {
            if (!pending.isEmpty() && available.isEmpty()) {
                for (Order order : pending) {
                    noDriverFoundOrderIds.add(order.getId());
                }
            }
            if (miniDispatch) {
                miniDispatchRequested = false;
            }
            return;
        }
        if (miniDispatch) {
            miniDispatchRequested = false;
        }

        List<DispatchPlan> selectedPlans;
        CompactDispatchDecision compactDecision = null;
        Map<String, CompactSelectedPlanEvidence> compactEvidenceByTrace = Map.of();
        if (dispatchMode == DispatchMode.LEGACY) {
            long dispatchStartedNanos = System.nanoTime();
            if (legacyNearestGreedyMode) {
                selectedPlans = nearestGreedyBaseline.dispatch(
                        new ArrayList<>(pending),
                        new ArrayList<>(available));
            } else {
                DispatchAgent.DispatchResult result = legacyDispatchAgent.dispatch(
                        new ArrayList<>(pending), new ArrayList<>(available),
                        drivers, activeOrders, simulatedHour,
                        trafficIntensity, weatherProfile);
                selectedPlans = result.plans();
            }
            long dispatchLatencyMs = (System.nanoTime() - dispatchStartedNanos) / 1_000_000L;
            totalDispatchDecisionLatencyMs += dispatchLatencyMs;
            dispatchDecisionLatencySamples.add(dispatchLatencyMs);
        } else if (dispatchMode == DispatchMode.COMPACT) {
            compactDecision = compactRuntimeCoordinator.dispatch(
                    new ArrayList<>(pending),
                    new ArrayList<>(available),
                    new ArrayList<>(regions),
                    simulatedHour,
                    trafficIntensity,
                    weatherProfile,
                    clock.currentInstant());
            totalDispatchDecisionLatencyMs += compactDecision.dispatchDecisionLatencyMs();
            dispatchDecisionLatencySamples.add(compactDecision.dispatchDecisionLatencyMs());
            selectedPlans = compactDecision.plans();
            Map<String, CompactSelectedPlanEvidence> evidenceByTrace = new LinkedHashMap<>();
            for (CompactSelectedPlanEvidence evidence : compactDecision.selectedPlanEvidence()) {
                evidenceByTrace.put(evidence.traceId(), evidence);
            }
            compactEvidenceByTrace = evidenceByTrace;
        } else {
            OmegaDispatchAgent.DispatchResult result = omegaAgent.dispatch(
                    new ArrayList<>(pending), new ArrayList<>(available),
                    drivers, activeOrders, simulatedHour,
                    trafficIntensity, weatherProfile, clock.currentInstant(), currentRunId);
            recoveryAccumulator = recoveryAccumulator.plus(result.recovery());
            totalDispatchDecisionLatencyMs += result.dispatchDecisionLatencyMs();
            dispatchDecisionLatencySamples.add(result.dispatchDecisionLatencyMs());
            modelInferenceLatencySamples.addAll(result.modelInferenceLatencySamples());
            neuralPriorLatencySamples.addAll(result.neuralPriorLatencySamples());
            dispatchStageTimingSamples.add(result.stageTimings());
            selectedPlans = result.plans();
        }
        if (!pending.isEmpty() && selectedPlans.isEmpty()) {
            for (Order order : pending) {
                noDriverFoundOrderIds.add(order.getId());
            }
        }

        Instant decisionTime = clock.currentInstant();
        if (dispatchMode == DispatchMode.COMPACT && compactDecision != null) {
            compactRuntimeCoordinator.beginDecision(
                    currentRunId,
                    dispatchMode.name(),
                    decisionTime,
                    compactDecision);
        }
        // Execute selected plans
        for (DispatchPlan plan : selectedPlans) {
            Driver best = plan.getDriver();
            DriverState previousState = best.getState();
            totalSelectedOrderPlanCount++;

            if (plan.getOrders().isEmpty()) {
                if (plan.isWaitingForThirdOrder()) {
                    cleanRegimeWaveAssemblyHoldCount++;
                    int remaining = holdCyclesByDriver.getOrDefault(
                            best.getId(),
                            Math.max(0, plan.getHoldRemainingCycles()));
                    if (miniDispatch) {
                        remaining = Math.max(0, remaining - 1);
                    }
                    if (remaining > 0) {
                        holdCyclesByDriver.put(best.getId(), remaining);
                        miniDispatchRequested = true;
                    } else {
                        holdCyclesByDriver.remove(best.getId());
                    }
                }
                holdOnlySelectionCount++;
                if (plan.getBundle().bundleId().startsWith("REPOS") && !plan.getSequence().isEmpty()) {
                    best.clearRouteWaypoints();
                    best.setAssignedSequence(plan.getSequence());
                    best.setTargetLocation(null);
                    queueDriverRoute(best, plan.getSequence().get(0).location(), DriverState.REPOSITIONING, -1);
                    eventBus.publish(new DriverStateChanged(best.getId(),
                            previousState, DriverState.ROUTE_PENDING));
                }
                continue; // HOLD plans or invalid REPOS plans just skip assignment
            }
            holdCyclesByDriver.remove(best.getId());

            DispatchPlan executablePlan = plan;
            if (dispatchMode != DispatchMode.COMPACT) {
                executablePlan = maybeAugmentPrePickupPlan(plan);
                if (executablePlan.getBundleSize() <= plan.getBundleSize()) {
                    DispatchPlan forcedAugmentedPlan = maybeForceMergePrePickupPlan(executablePlan);
                    if (forcedAugmentedPlan.getBundleSize() > executablePlan.getBundleSize()) {
                        executablePlan = forcedAugmentedPlan;
                    }
                }
            }
            if (executablePlan.getBundleSize() > plan.getBundleSize()) {
                prePickupAugmentationCount++;
            }
            realAssignedPlanCount++;

            if (executablePlan.isHardThreeOrderPolicyActive()
                    && !executablePlan.isHarshWeatherStress()
                    && executablePlan.getStressRegime() != com.routechain.ai.StressRegime.SEVERE_STRESS) {
                cleanRegimeOrderDecisionCount++;
                if (executablePlan.getBundleSize() < 3) {
                    cleanRegimeSubThreeSelectedCount++;
                } else {
                    cleanRegimeThirdOrderLaunchCount++;
                }
            }
            if (executablePlan.isStressFallbackOnly() && executablePlan.getBundleSize() < 3) {
                stressDowngradeSelectionCount++;
            }

            Long postDropIdleTick = postDropIdleTickByDriver.remove(best.getId());
            if (postDropIdleTick != null
                    && tickCounter.get() - postDropIdleTick <= compactRuntimeCoordinator.policyConfig().postDropWindowTicks()) {
                postDropOrderHitCount++;
                if (dispatchMode == DispatchMode.COMPACT) {
                    compactRuntimeCoordinator.recordPostDropHit(
                            best.getId(),
                            tickCounter.get(),
                            decisionTime);
                }
            }

            List<Order> newlyAssignedOrders = new ArrayList<>();
            for (Order order : executablePlan.getOrders()) {
                boolean newlyAssigned = !best.getId().equals(order.getAssignedDriverId());
                if (newlyAssigned) {
                    order.assignDriver(best.getId(), decisionTime);
                    newlyAssignedOrders.add(order);
                }
                order.setDecisionTraceId(executablePlan.getTraceId());
                order.setBundle(executablePlan.getBundle().bundleId());
                order.setPredictedLateRisk(executablePlan.getLateRisk());
                order.setPredictedBundleFit(executablePlan.getBundleEfficiency());
                order.setPredictedTravelTime(executablePlan.getPredictedTotalMinutes());
                order.setPredictedAssignmentConfidence(executablePlan.getConfidence());

                if (!best.getActiveOrderIds().contains(order.getId())) {
                    best.addOrder(order.getId());
                }

                if (newlyAssigned) {
                    totalAssignmentLatencyMs += order.getAssignmentLatencyMs();
                    assignmentAgingLatencySamples.add(Math.max(0L, order.getAssignmentLatencyMs()));
                    totalAssignments++;
                }
            }

            syncLegacyGuardrailDriver(best, executablePlan);
            best.clearRouteWaypoints();
            if (isOmegaPrePickupAugmentableDriver(best)) {
                best.replaceAssignedSequenceBeforeFirstPickup(executablePlan.getSequence());
            } else {
                best.setAssignedSequence(executablePlan.getSequence());
            }
            best.setTargetLocation(null);
            best.setActiveBundleId(executablePlan.getBundle().bundleId());
            queueDriverRoute(best, executablePlan.getSequence().get(0).location(), DriverState.PICKUP_EN_ROUTE, 0);
            if (dispatchMode == DispatchMode.OMEGA
                    && !best.isRouteLockedAfterFirstPickup()
                    && !isLegacyGuardrailPlan(executablePlan)
                    && executablePlan.getBundleSize() < LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE) {
                miniDispatchRequested = true;
            }

            double deadheadKm = executablePlan.getPredictedDeadheadKm();
            totalDeadheadKm += deadheadKm;
            best.addDeadheadDistance(deadheadKm);
            if (executablePlan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                    || executablePlan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE) {
                borrowedExecutedDeadheadKm += deadheadKm;
            } else if (executablePlan.getSelectionBucket() == SelectionBucket.FALLBACK_LOCAL_LOW_DEADHEAD) {
                fallbackExecutedDeadheadKm += deadheadKm;
            } else if (executablePlan.getSelectionBucket() == SelectionBucket.WAVE_LOCAL
                    || executablePlan.getSelectionBucket() == SelectionBucket.EXTENSION_LOCAL
                    || executablePlan.getSelectionBucket() == SelectionBucket.SINGLE_LOCAL) {
                waveExecutedDeadheadKm += deadheadKm;
            }
            best.setContinuationValueCurrentZone(executablePlan.getEndZoneOpportunity());
            best.setOverloadRisk(executablePlan.getCancellationRisk());
            ensureRouteMetrics(executablePlan, best);

            routeMetricPlanCount++;
            totalDeliveryCorridorScore += executablePlan.getDeliveryCorridorScore();
            totalLastDropLandingScore += executablePlan.getLastDropLandingScore();
            totalExpectedPostCompletionEmptyKm += executablePlan.getExpectedPostCompletionEmptyKm();
            totalExpectedNextOrderIdleMinutes += executablePlan.getExpectedNextOrderIdleMinutes();
            totalZigZagPenalty += executablePlan.getDeliveryZigZagPenalty();
            totalPredictedPostDropOpportunity += executablePlan.getPostDropDemandProbability();
            totalTrafficForecastAbsError += executablePlan.getTrafficForecastAbsError();
            totalWeatherForecastHitRate += executablePlan.getWeatherForecastHitRate();
            forecastDecisionCount++;
            if (executablePlan.getSelectionBucket() == SelectionBucket.BORROWED_COVERAGE
                    || executablePlan.getSelectionBucket() == SelectionBucket.EMERGENCY_COVERAGE) {
                totalBorrowSuccessCalibrationGap += Math.abs(
                        1.0 - executablePlan.getBorrowSuccessProbability());
                borrowSuccessCalibrationCount++;
            }
            if (executablePlan.getLastDropLandingScore() >= 0.60) {
                lastDropGoodZoneCount++;
            }
            if (executablePlan.getBundleSize() >= 3) {
                visibleBundleThreePlusCount++;
            }
            if (dispatchMode == DispatchMode.COMPACT && compactDecision != null) {
                CompactSelectedPlanEvidence evidence = compactEvidenceByTrace.get(executablePlan.getTraceId());
                if (evidence != null) {
                    compactRuntimeCoordinator.recordSelectedPlan(
                            executablePlan,
                            evidence,
                            compactDecision.weightSnapshotBefore(),
                            decisionTime);
                }
            }

            if (newlyAssignedOrders.size() > 1) {
                totalBundled += newlyAssignedOrders.size();
            }

            eventBus.publish(new DispatchDecision(
                    currentRunId,
                    executablePlan.getOrders().get(0).getId(),
                    best.getId(),
                    executablePlan.getSelectionBucket().name(),
                    executablePlan.getTotalScore(),
                    executablePlan.getPredictedTotalMinutes(),
                    deadheadKm,
                    executablePlan.getConfidence(),
                    executablePlan.getServiceTier(),
                    routeLatencyMode.name(),
                    dispatchDecisionLatencySamples.isEmpty()
                            ? 0L
                            : dispatchDecisionLatencySamples.get(dispatchDecisionLatencySamples.size() - 1),
                    executablePlan.getHoldRemainingCycles(),
                    executablePlan.getMarginalDeadheadPerAddedOrder()));

            for (Order order : newlyAssignedOrders) {
                eventBus.publish(new OrderAssigned(order.getId(), best.getId()));
            }
            eventBus.publish(new DriverStateChanged(best.getId(),
                    previousState, DriverState.ROUTE_PENDING));
        }
    }

    private boolean isDispatchEligibleDriver(Driver driver) {
        return driver != null && (driver.isAvailable() || isOmegaPrePickupAugmentableDriver(driver));
    }

    private DispatchPlan maybeAugmentPrePickupPlan(DispatchPlan plan) {
        Driver driver = plan.getDriver();
        if (driver == null
                || !isOmegaPrePickupAugmentableDriver(driver)
                || isLegacyGuardrailPlan(plan)
                || plan.getOrders().isEmpty()) {
            return plan;
        }

        List<Order> existingOrders = activeOrders.stream()
                .filter(order -> driver.getId().equals(order.getAssignedDriverId()))
                .filter(this::isPendingPickupOrder)
                .toList();
        if (existingOrders.isEmpty()) {
            return plan;
        }

        LinkedHashMap<String, Order> mergedOrderMap = new LinkedHashMap<>();
        for (Order order : existingOrders) {
            mergedOrderMap.put(order.getId(), order);
        }
        for (Order order : plan.getOrders()) {
            mergedOrderMap.putIfAbsent(order.getId(), order);
        }
        if (mergedOrderMap.size() <= existingOrders.size() || mergedOrderMap.size() > 5) {
            return plan;
        }

        List<DispatchPlan.Stop> baseSequence = remainingSequence(driver);
        if (baseSequence.isEmpty()) {
            return plan;
        }

        SequenceOptimizer optimizer = new SequenceOptimizer(
                trafficIntensity,
                weatherProfile,
                getExecutionProfile() == OmegaDispatchAgent.ExecutionProfile.SHOWCASE_PICKUP_WAVE_8,
                plan.getStressRegime());
        List<Order> mergedOrders = new ArrayList<>(mergedOrderMap.values());
        DispatchPlan.Bundle mergedBundle = new DispatchPlan.Bundle(
                "AUG-" + UUID.randomUUID().toString().substring(0, 6),
                List.copyOf(mergedOrders),
                mergedOrders.stream().mapToDouble(Order::getQuotedFee).sum(),
                mergedOrders.size());
        List<List<DispatchPlan.Stop>> sequences = optimizer.generateFeasibleSequences(
                driver, mergedBundle, mergedOrders.size() >= 4 ? 8 : 6);
        if (sequences.isEmpty()) {
            return plan;
        }

        List<DispatchPlan.Stop> mergedSequence = sequences.get(0);
        DispatchPlan.Stop baseFirstStop = baseSequence.get(0);
        double mergedFirstStopArrival = mergedSequence.stream()
                .filter(stop -> stop.type() == baseFirstStop.type()
                        && Objects.equals(stop.orderId(), baseFirstStop.orderId()))
                .mapToDouble(DispatchPlan.Stop::estimatedArrivalMinutes)
                .findFirst()
                .orElse(Double.MAX_VALUE);
        double firstPickupDelayCapMinutes = switch (weatherProfile) {
            case CLEAR -> 5.0;
            case LIGHT_RAIN -> 4.5;
            case HEAVY_RAIN -> LOCAL_MINI_DISPATCH_MAX_FIRST_PICKUP_DELAY_MIN;
            case STORM -> 2.5;
        };
        if (mergedFirstStopArrival > baseFirstStop.estimatedArrivalMinutes() + firstPickupDelayCapMinutes) {
            return plan;
        }
        double baseDistanceKm = computeSequenceDistanceKm(driver.getCurrentLocation(), baseSequence);
        double mergedDistanceKm = computeSequenceDistanceKm(driver.getCurrentLocation(), mergedSequence);
        double distanceMultiplierCap = switch (weatherProfile) {
            case CLEAR -> 1.18;
            case LIGHT_RAIN -> 1.15;
            case HEAVY_RAIN -> 1.10;
            case STORM -> 1.06;
        };
        if (baseDistanceKm > 0.0 && mergedDistanceKm > baseDistanceKm * distanceMultiplierCap) {
            return plan;
        }

        SequenceOptimizer.RouteObjectiveMetrics metrics = optimizer.evaluateRouteObjective(
                driver, mergedSequence, mergedOrders);
        double corridorTolerance = weatherProfile == WeatherProfile.CLEAR ? 0.14
                : weatherProfile == WeatherProfile.LIGHT_RAIN ? 0.12 : 0.08;
        double landingTolerance = weatherProfile == WeatherProfile.CLEAR ? 0.14
                : weatherProfile == WeatherProfile.LIGHT_RAIN ? 0.12 : 0.08;
        double zigZagCap = weatherProfile == WeatherProfile.CLEAR ? 0.72
                : weatherProfile == WeatherProfile.LIGHT_RAIN ? 0.68 : 0.60;
        if (metrics.deliveryCorridorScore() + corridorTolerance < Math.max(0.26, plan.getDeliveryCorridorScore())) {
            return plan;
        }
        if (metrics.lastDropLandingScore() + landingTolerance < Math.max(0.18, plan.getLastDropLandingScore())) {
            return plan;
        }
        if (metrics.deliveryZigZagPenalty() > Math.max(zigZagCap, plan.getDeliveryZigZagPenalty() + 0.18)) {
            return plan;
        }
        int addedOrders = mergedOrders.size() - plan.getBundleSize();
        double addedDistanceKm = Math.max(0.0, mergedDistanceKm - baseDistanceKm);
        double addedDeadheadCapKm = switch (weatherProfile) {
            case CLEAR -> 0.8;
            case LIGHT_RAIN -> 0.7;
            case HEAVY_RAIN -> 0.55;
            case STORM -> 0.35;
        };
        if (addedDistanceKm > addedDeadheadCapKm) {
            return plan;
        }
        double predictedOnTimeDrop = Math.max(0.0, Math.min(0.18, 0.03 * addedOrders));
        double maxOnTimeDrop = switch (weatherProfile) {
            case CLEAR -> 0.05;
            case LIGHT_RAIN -> 0.04;
            case HEAVY_RAIN -> 0.03;
            case STORM -> 0.02;
        };
        if (predictedOnTimeDrop > maxOnTimeDrop) {
            return plan;
        }

        DispatchPlan augmentedPlan = new DispatchPlan(driver, mergedBundle, mergedSequence);
        augmentedPlan.setTraceId(plan.getTraceId() + "-AUG");
        augmentedPlan.setTotalScore(plan.getTotalScore() + Math.min(0.12, 0.03 * addedOrders));
        augmentedPlan.setConfidence(Math.max(plan.getConfidence(), 0.55));
        augmentedPlan.setPredictedDeadheadKm(
                driver.getCurrentLocation().distanceTo(mergedSequence.get(0).location()) / 1000.0);
        augmentedPlan.setPredictedTotalMinutes(mergedSequence.get(mergedSequence.size() - 1).estimatedArrivalMinutes());
        augmentedPlan.setOnTimeProbability(Math.max(0.30, plan.getOnTimeProbability() - predictedOnTimeDrop));
        augmentedPlan.setLateRisk(Math.min(0.95, plan.getLateRisk() + 0.04 * addedOrders));
        augmentedPlan.setCancellationRisk(Math.min(0.95, plan.getCancellationRisk() + 0.03 * addedOrders));
        augmentedPlan.setDriverProfit(plan.getDriverProfit());
        augmentedPlan.setCustomerFee(mergedOrders.stream().mapToDouble(Order::getQuotedFee).sum());
        augmentedPlan.setBundleEfficiency(Math.max(plan.getBundleEfficiency(), mergedOrders.size() / (double) Math.max(1, plan.getBundleSize())));
        augmentedPlan.setEndZoneOpportunity(Math.max(plan.getEndZoneOpportunity(), metrics.lastDropLandingScore()));
        augmentedPlan.setNextOrderAcquisitionScore(plan.getNextOrderAcquisitionScore());
        augmentedPlan.setCongestionPenalty(plan.getCongestionPenalty());
        augmentedPlan.setRepositionPenalty(plan.getRepositionPenalty());
        augmentedPlan.setRemainingDropProximityScore(metrics.remainingDropProximityScore());
        augmentedPlan.setDeliveryCorridorScore(metrics.deliveryCorridorScore());
        augmentedPlan.setLastDropLandingScore(metrics.lastDropLandingScore());
        augmentedPlan.setExpectedPostCompletionEmptyKm(metrics.expectedPostCompletionEmptyKm());
        augmentedPlan.setDeliveryZigZagPenalty(metrics.deliveryZigZagPenalty());
        augmentedPlan.setExpectedNextOrderIdleMinutes(metrics.expectedNextOrderIdleMinutes());
        augmentedPlan.setStressFallbackOnly(mergedOrders.size() < 3 && plan.isStressFallbackOnly());
        augmentedPlan.setWaveLaunchEligible(mergedOrders.size() >= 3);
        augmentedPlan.setWaitingForThirdOrder(false);
        augmentedPlan.setHardThreeOrderPolicyActive(plan.isHardThreeOrderPolicyActive());
        augmentedPlan.setHarshWeatherStress(plan.isHarshWeatherStress());
        augmentedPlan.setStressRegime(plan.getStressRegime());
        augmentedPlan.setRunId(plan.getRunId());
        augmentedPlan.setSelectionBucket(SelectionBucket.EXTENSION_LOCAL);
        return augmentedPlan;
    }

    private DispatchPlan maybeForceMergePrePickupPlan(DispatchPlan plan) {
        Driver driver = plan.getDriver();
        if (driver == null
                || !isOmegaPrePickupAugmentableDriver(driver)
                || isLegacyGuardrailPlan(plan)
                || plan.getOrders().isEmpty()) {
            return plan;
        }
        if (weatherProfile == WeatherProfile.HEAVY_RAIN || weatherProfile == WeatherProfile.STORM) {
            return plan;
        }

        List<Order> existingOrders = activeOrders.stream()
                .filter(order -> driver.getId().equals(order.getAssignedDriverId()))
                .filter(this::isPendingPickupOrder)
                .toList();
        if (existingOrders.isEmpty()) {
            return plan;
        }

        LinkedHashMap<String, Order> mergedOrderMap = new LinkedHashMap<>();
        for (Order order : existingOrders) {
            mergedOrderMap.put(order.getId(), order);
        }
        int existingCount = mergedOrderMap.size();
        for (Order order : plan.getOrders()) {
            mergedOrderMap.putIfAbsent(order.getId(), order);
        }
        if (mergedOrderMap.size() <= existingCount || mergedOrderMap.size() > LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE) {
            return plan;
        }

        List<DispatchPlan.Stop> baseSequence = remainingSequence(driver);
        if (baseSequence.isEmpty()) {
            return plan;
        }
        Set<String> baseSequenceOrderIds = new HashSet<>();
        for (DispatchPlan.Stop stop : baseSequence) {
            baseSequenceOrderIds.add(stop.orderId());
        }

        List<Order> newOrders = plan.getOrders().stream()
                .filter(order -> !baseSequenceOrderIds.contains(order.getId()))
                .toList();
        if (newOrders.isEmpty()) {
            return plan;
        }

        List<DispatchPlan.Stop> mergedSequence = new ArrayList<>(baseSequence);
        DispatchPlan.Stop tailStop = mergedSequence.get(mergedSequence.size() - 1);
        GeoPoint anchor = tailStop.location();
        double etaCursor = tailStop.estimatedArrivalMinutes();

        for (Order order : newOrders) {
            double toPickupKm = anchor.distanceTo(order.getPickupPoint()) / 1000.0;
            etaCursor += Math.max(0.8, toPickupKm / 18.0 * 60.0);
            mergedSequence.add(new DispatchPlan.Stop(
                    order.getId(),
                    order.getPickupPoint(),
                    DispatchPlan.Stop.StopType.PICKUP,
                    etaCursor));

            double toDropKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
            etaCursor += Math.max(1.2, toDropKm / 22.0 * 60.0);
            mergedSequence.add(new DispatchPlan.Stop(
                    order.getId(),
                    order.getDropoffPoint(),
                    DispatchPlan.Stop.StopType.DROPOFF,
                    etaCursor));
            anchor = order.getDropoffPoint();
        }

        List<Order> mergedOrders = new ArrayList<>(mergedOrderMap.values());
        DispatchPlan.Bundle mergedBundle = new DispatchPlan.Bundle(
                "AUG-FORCE-" + UUID.randomUUID().toString().substring(0, 6),
                List.copyOf(mergedOrders),
                mergedOrders.stream().mapToDouble(Order::getQuotedFee).sum(),
                mergedOrders.size());
        double baseDistanceKm = computeSequenceDistanceKm(driver.getCurrentLocation(), baseSequence);
        double mergedDistanceKm = computeSequenceDistanceKm(driver.getCurrentLocation(), mergedSequence);
        double addedDistanceKm = Math.max(0.0, mergedDistanceKm - baseDistanceKm);
        double addedDeadheadCapKm = switch (weatherProfile) {
            case CLEAR -> 0.55;
            case LIGHT_RAIN -> 0.45;
            case HEAVY_RAIN -> 0.30;
            case STORM -> 0.20;
        };
        if (addedDistanceKm > addedDeadheadCapKm) {
            return plan;
        }

        DispatchPlan augmentedPlan = new DispatchPlan(driver, mergedBundle, List.copyOf(mergedSequence));
        int addedOrders = Math.max(1, mergedOrders.size() - Math.max(1, plan.getBundleSize()));
        double predictedOnTimeDrop = Math.max(0.0, Math.min(0.12, addedOrders * 0.02));
        double maxOnTimeDrop = switch (weatherProfile) {
            case CLEAR -> 0.04;
            case LIGHT_RAIN -> 0.03;
            case HEAVY_RAIN -> 0.02;
            case STORM -> 0.015;
        };
        if (predictedOnTimeDrop > maxOnTimeDrop) {
            return plan;
        }
        augmentedPlan.setTraceId(plan.getTraceId() + "-AUGF");
        augmentedPlan.setTotalScore(plan.getTotalScore() + Math.min(0.08, addedOrders * 0.02));
        augmentedPlan.setConfidence(Math.max(plan.getConfidence(), 0.55));
        augmentedPlan.setPredictedDeadheadKm(plan.getPredictedDeadheadKm());
        augmentedPlan.setPredictedTotalMinutes(Math.max(plan.getPredictedTotalMinutes(), etaCursor));
        augmentedPlan.setOnTimeProbability(Math.max(0.25, plan.getOnTimeProbability() - predictedOnTimeDrop));
        augmentedPlan.setLateRisk(Math.min(0.98, plan.getLateRisk() + addedOrders * 0.03));
        augmentedPlan.setCancellationRisk(Math.min(0.98, plan.getCancellationRisk() + addedOrders * 0.02));
        augmentedPlan.setDriverProfit(plan.getDriverProfit());
        augmentedPlan.setCustomerFee(mergedOrders.stream().mapToDouble(Order::getQuotedFee).sum());
        augmentedPlan.setBundleEfficiency(Math.max(
                plan.getBundleEfficiency(),
                mergedOrders.size() / (double) Math.max(1, plan.getBundleSize())));
        augmentedPlan.setEndZoneOpportunity(plan.getEndZoneOpportunity());
        augmentedPlan.setNextOrderAcquisitionScore(plan.getNextOrderAcquisitionScore());
        augmentedPlan.setCongestionPenalty(plan.getCongestionPenalty());
        augmentedPlan.setRepositionPenalty(plan.getRepositionPenalty());
        augmentedPlan.setRemainingDropProximityScore(plan.getRemainingDropProximityScore());
        augmentedPlan.setDeliveryCorridorScore(plan.getDeliveryCorridorScore());
        augmentedPlan.setLastDropLandingScore(plan.getLastDropLandingScore());
        augmentedPlan.setExpectedPostCompletionEmptyKm(plan.getExpectedPostCompletionEmptyKm());
        augmentedPlan.setDeliveryZigZagPenalty(plan.getDeliveryZigZagPenalty());
        augmentedPlan.setExpectedNextOrderIdleMinutes(plan.getExpectedNextOrderIdleMinutes());
        augmentedPlan.setStressFallbackOnly(mergedOrders.size() < 3 && plan.isStressFallbackOnly());
        augmentedPlan.setWaveLaunchEligible(mergedOrders.size() >= 3);
        augmentedPlan.setWaitingForThirdOrder(false);
        augmentedPlan.setHardThreeOrderPolicyActive(plan.isHardThreeOrderPolicyActive());
        augmentedPlan.setHarshWeatherStress(plan.isHarshWeatherStress());
        augmentedPlan.setStressRegime(plan.getStressRegime());
        augmentedPlan.setRunId(plan.getRunId());
        augmentedPlan.setSelectionBucket(SelectionBucket.EXTENSION_LOCAL);
        augmentedPlan.setExecutionGatePassed(plan.isExecutionGatePassed());
        augmentedPlan.setExecutionScore(plan.getExecutionScore());
        augmentedPlan.setFutureScore(plan.getFutureScore());
        return augmentedPlan;
    }

    private List<DispatchPlan.Stop> remainingSequence(Driver driver) {
        List<DispatchPlan.Stop> assignedSequence = driver.getAssignedSequence();
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return List.of();
        }
        int currentIndex = Math.max(0, Math.min(driver.getCurrentSequenceIndex(), assignedSequence.size() - 1));
        return List.copyOf(assignedSequence.subList(currentIndex, assignedSequence.size()));
    }

    private double computeSequenceDistanceKm(GeoPoint start, List<DispatchPlan.Stop> sequence) {
        if (start == null || sequence == null || sequence.isEmpty()) {
            return 0.0;
        }
        double distanceKm = 0.0;
        GeoPoint previous = start;
        for (DispatchPlan.Stop stop : sequence) {
            distanceKm += previous.distanceTo(stop.location()) / 1000.0;
            previous = stop.location();
        }
        return distanceKm;
    }

    private boolean isPendingPickupOrder(Order order) {
        if (order == null) {
            return false;
        }
        OrderStatus status = order.getStatus();
        return status == OrderStatus.ASSIGNED || status == OrderStatus.PICKUP_EN_ROUTE;
    }

    private List<Driver> selectMiniDispatchDrivers(List<Order> pendingOrders,
                                                   List<Driver> availableDrivers) {
        if (availableDrivers == null || availableDrivers.isEmpty()) {
            return List.of();
        }
        List<Driver> augmentableDrivers = availableDrivers.stream()
                .filter(this::isOmegaPrePickupAugmentableDriver)
                .filter(driver -> pendingOrders.stream().anyMatch(order -> isMiniDispatchReachable(driver, order)))
                .toList();
        if (!augmentableDrivers.isEmpty()) {
            return augmentableDrivers;
        }
        List<Driver> localDrivers = availableDrivers.stream()
                .filter(driver -> isOmegaPrePickupAugmentableDriver(driver)
                        || pendingOrders.stream().anyMatch(order -> isMiniDispatchReachable(driver, order)))
                .toList();
        return localDrivers;
    }

    private List<Order> selectMiniDispatchOrders(List<Order> pendingOrders,
                                                 List<Driver> localDrivers) {
        if (pendingOrders == null || pendingOrders.isEmpty() || localDrivers == null || localDrivers.isEmpty()) {
            return List.of();
        }
        return pendingOrders.stream()
                .filter(order -> localDrivers.stream().anyMatch(driver -> isMiniDispatchReachable(driver, order)))
                .sorted(Comparator.comparingDouble(order -> minMiniDispatchDistanceKm(localDrivers, order)))
                .limit(Math.max(LOCAL_MINI_DISPATCH_MAX_CANDIDATES, localDrivers.size() * 3L))
                .toList();
    }

    private boolean isMiniDispatchReachable(Driver driver, Order order) {
        if (driver == null || order == null) {
            return false;
        }
        double pickupKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        if (isOmegaPrePickupAugmentableDriver(driver)) {
            if (driver.getCurrentOrderCount() >= LOCAL_MINI_DISPATCH_MAX_BUNDLE_SIZE) {
                return false;
            }
            double pickupCapKm = switch (weatherProfile) {
                case CLEAR -> 3.2;
                case LIGHT_RAIN -> 3.0;
                case HEAVY_RAIN -> 2.6;
                case STORM -> 2.2;
            };
            double detourCapKm = switch (weatherProfile) {
                case CLEAR -> 1.35;
                case LIGHT_RAIN -> 1.20;
                case HEAVY_RAIN -> LOCAL_MINI_DISPATCH_MAX_DETOUR_KM;
                case STORM -> 0.75;
            };
            GeoPoint routeAnchor = resolvePrePickupRouteAnchor(driver);
            if (routeAnchor == null) {
                return pickupKm <= pickupCapKm;
            }
            double detourKm = computeDetourKm(driver.getCurrentLocation(), routeAnchor, order.getPickupPoint());
            return pickupKm <= pickupCapKm && detourKm <= detourCapKm;
        }
        return pickupKm <= 2.2;
    }

    private double minMiniDispatchDistanceKm(List<Driver> localDrivers, Order order) {
        return localDrivers.stream()
                .mapToDouble(driver -> driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0)
                .min()
                .orElse(Double.MAX_VALUE);
    }

    private boolean isImmediateInjectedAugmentReachable(Driver driver, Order order) {
        if (driver == null || order == null) {
            return false;
        }
        GeoPoint routeAnchor = resolvePrePickupRouteAnchor(driver);
        double pickupKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        if (routeAnchor == null) {
            return pickupKm <= 1.2;
        }
        double anchorPickupKm = routeAnchor.distanceTo(order.getPickupPoint()) / 1000.0;
        double detourKm = computeDetourKm(driver.getCurrentLocation(), routeAnchor, order.getPickupPoint());
        double reachCapKm = switch (weatherProfile) {
            case CLEAR -> 2.20;
            case LIGHT_RAIN -> 2.00;
            case HEAVY_RAIN -> 1.60;
            case STORM -> 1.20;
        };
        double detourCapKm = switch (weatherProfile) {
            case CLEAR -> 1.20;
            case LIGHT_RAIN -> 1.00;
            case HEAVY_RAIN -> 0.80;
            case STORM -> 0.60;
        };
        return Math.min(pickupKm, anchorPickupKm) <= reachCapKm || detourKm <= detourCapKm;
    }

    private double immediateInjectedAugmentReachKm(Driver driver, Order order) {
        if (driver == null || order == null) {
            return Double.MAX_VALUE;
        }
        double pickupKm = driver.getCurrentLocation().distanceTo(order.getPickupPoint()) / 1000.0;
        GeoPoint routeAnchor = resolvePrePickupRouteAnchor(driver);
        if (routeAnchor == null) {
            return pickupKm;
        }
        return Math.min(pickupKm, routeAnchor.distanceTo(order.getPickupPoint()) / 1000.0);
    }

    private GeoPoint resolvePrePickupRouteAnchor(Driver driver) {
        if (driver == null) {
            return null;
        }
        if (driver.getPendingTargetLocation() != null) {
            return driver.getPendingTargetLocation();
        }
        if (driver.getTargetLocation() != null) {
            return driver.getTargetLocation();
        }
        List<DispatchPlan.Stop> assignedSequence = driver.getAssignedSequence();
        if (assignedSequence == null || assignedSequence.isEmpty()) {
            return null;
        }
        int index = Math.max(0, Math.min(driver.getCurrentSequenceIndex(), assignedSequence.size() - 1));
        return assignedSequence.get(index).location();
    }

    private double computeDetourKm(GeoPoint origin, GeoPoint anchor, GeoPoint candidatePickup) {
        if (origin == null || anchor == null || candidatePickup == null) {
            return Double.MAX_VALUE;
        }
        double directMeters = origin.distanceTo(anchor);
        if (directMeters <= 0.0) {
            return origin.distanceTo(candidatePickup) / 1000.0;
        }
        double viaMeters = origin.distanceTo(candidatePickup) + candidatePickup.distanceTo(anchor);
        return Math.max(0.0, (viaMeters - directMeters) / 1000.0);
    }

    private void markPickupLegStarted(Driver driver, Instant simulatedNow) {
        if (driver == null || driver.getAssignedSequence() == null || driver.getAssignedSequence().isEmpty()) {
            return;
        }
        int routeIndex = driver.getActiveRouteSequenceIndex() >= 0
                ? driver.getActiveRouteSequenceIndex()
                : driver.getCurrentSequenceIndex();
        int index = Math.max(0, Math.min(routeIndex, driver.getAssignedSequence().size() - 1));
        DispatchPlan.Stop currentStop = driver.getAssignedSequence().get(index);
        if (currentStop.type() != DispatchPlan.Stop.StopType.PICKUP || currentStop.orderId() == null) {
            return;
        }
        activeOrders.stream()
                .filter(order -> currentStop.orderId().equals(order.getId()))
                .filter(order -> order.getStatus() == OrderStatus.ASSIGNED
                        || order.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .findFirst()
                .ifPresent(order -> order.markPickupStarted(simulatedNow));
    }

    // ── Re-dispatch check ────────────────────────────────────────────────
    private void ensureRouteMetrics(DispatchPlan plan, Driver driver) {
        if (plan.getOrders().isEmpty() || plan.getSequence().isEmpty()) {
            return;
        }
        boolean missingRouteMetrics = plan.getDeliveryCorridorScore() <= 0.0
                && plan.getLastDropLandingScore() <= 0.0
                && plan.getExpectedPostCompletionEmptyKm() <= 0.0;
        if (!missingRouteMetrics) {
            return;
        }

        SequenceOptimizer evaluator = new SequenceOptimizer(trafficIntensity, weatherProfile);
        SequenceOptimizer.RouteObjectiveMetrics metrics = evaluator.evaluateRouteObjective(
                driver, plan.getSequence(), plan.getOrders());
        plan.setRemainingDropProximityScore(metrics.remainingDropProximityScore());
        plan.setDeliveryCorridorScore(metrics.deliveryCorridorScore());
        plan.setLastDropLandingScore(metrics.lastDropLandingScore());
        plan.setExpectedPostCompletionEmptyKm(metrics.expectedPostCompletionEmptyKm());
        plan.setDeliveryZigZagPenalty(metrics.deliveryZigZagPenalty());
        plan.setExpectedNextOrderIdleMinutes(metrics.expectedNextOrderIdleMinutes());
    }

    private void checkReDispatch() {
        // Run re-dispatch every 5 ticks to avoid overhead
        if (tickCounter.get() % 5 != 0) return;

        List<Order> toReplan = reDispatchEngine.evaluateReDispatchCandidates(
                activeOrders, drivers, trafficIntensity, weatherProfile);

        for (Order order : toReplan) {
            reDispatchEngine.executeReDispatch(order, drivers);
        }
    }

    // ── Process deliveries (state transitions) ──────────────────────────
    private void processDeliveries() {
        Instant simulatedNow = clock.currentInstant();
        for (Driver driver : drivers) {
            List<DispatchPlan.Stop> seq = driver.getAssignedSequence();
            if (seq == null
                    || driver.getState() == DriverState.ONLINE_IDLE
                    || driver.getState() == DriverState.ROUTE_PENDING) {
                continue;
            }
            if (driver.getMerchantWaitTicksRemaining() > 0) {
                continue;
            }

            if (driver.getTargetLocation() == null) {
                int idx = driver.getCurrentSequenceIndex();
                if (idx < seq.size()) {
                    DispatchPlan.Stop currentStop = seq.get(idx);
                    Order order = currentStop.orderId() == null ? null
                            : activeOrders.stream()
                            .filter(o -> o.getId().equals(currentStop.orderId()))
                            .findFirst()
                            .orElse(null);

                    if (order != null && order.getStatus() != OrderStatus.CANCELLED) {
                        if (currentStop.type() == DispatchPlan.Stop.StopType.PICKUP) {
                            if (!merchantWaitEngine.isMerchantReady(order, simulatedNow)) {
                                double waitMinutes = merchantWaitEngine
                                        .estimateWaitMinutes(order, simulatedNow);
                                int waitTicks = (int) Math.ceil(
                                        (waitMinutes * 60.0) / SimulationClock.SUB_TICK_SECONDS);
                                if (driver.getState() != DriverState.WAITING_PICKUP) {
                                    DriverState oldState = driver.getState();
                                    driver.setState(DriverState.WAITING_PICKUP);
                                    eventBus.publish(new DriverStateChanged(
                                            driver.getId(), oldState, DriverState.WAITING_PICKUP));
                                }
                                driver.setMerchantWaitTicksRemaining(Math.max(1, waitTicks));
                                continue;
                            }
                            order.markPickedUp(simulatedNow);
                            driver.markFirstPickupCompleted();
                            eventBus.publish(new OrderPickedUp(order.getId()));
                        } else if (currentStop.type() == DispatchPlan.Stop.StopType.DROPOFF) {
                            order.markDelivered(simulatedNow);
                            driver.removeOrder(order.getId());
                            driver.addEarning(order.getQuotedFee());
                            driver.incrementCompletedOrders();
                            totalDelivered++;
                            if (order.isLate()) totalLateDelivered++;
                            totalEarnings += order.getQuotedFee();
                            completedOrders.add(order);
                            activeOrders.remove(order);
                            eventBus.publish(new OrderDelivered(order.getId()));
                            dbService.saveOrder(order);
                            if (dispatchMode == DispatchMode.COMPACT
                                    && order.getDecisionTraceId() != null) {
                                compactRuntimeCoordinator.recordOrderDelivered(
                                        order.getDecisionTraceId(),
                                        order.getId(),
                                        !order.isLate(),
                                        order.getQuotedFee());
                            }

                            double etaActual = 0;
                            if (order.getAssignedAt() != null && order.getDeliveredAt() != null) {
                                etaActual = java.time.Duration.between(
                                        order.getAssignedAt(), order.getDeliveredAt()).toSeconds() / 60.0;
                            }
                            if (etaActual <= 0) {
                                double distKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
                                etaActual = distKm / Math.max(6, driver.getSpeedKmh()) * 60;
                            }
                            if (dispatchMode == DispatchMode.OMEGA) {
                                omegaAgent.onOrderDelivered(order, driver, etaActual, order.isLate(),
                                        order.getQuotedFee(), trafficIntensity, weatherProfile,
                                        simulatedHour, tickCounter.get(), simulatedNow);
                            }
                        }
                    }
                    driver.advanceSequenceIndex();
                    idx++;
                }

                if (idx < seq.size()) {
                    DispatchPlan.Stop nextStop = seq.get(idx);
                    DriverState nextState = nextStop.type() == DispatchPlan.Stop.StopType.PICKUP
                            ? DriverState.PICKUP_EN_ROUTE
                            : DriverState.DELIVERING;

                    if (nextStop.orderId() != null) {
                        Order nextOrder = activeOrders.stream()
                                .filter(o -> o.getId().equals(nextStop.orderId()))
                                .findFirst()
                                .orElse(null);
                        if (nextOrder != null && nextOrder.getStatus() != OrderStatus.CANCELLED) {
                            if (nextStop.type() == DispatchPlan.Stop.StopType.PICKUP
                                    && nextOrder.getStatus() != OrderStatus.PICKED_UP
                                    && nextOrder.getStatus() != OrderStatus.DROPOFF_EN_ROUTE) {
                            } else if (nextStop.type() == DispatchPlan.Stop.StopType.DROPOFF) {
                                nextOrder.markDropoffStarted(simulatedNow);
                            }
                        }
                    }

                    DriverState oldState = driver.getState();
                    driver.setTargetLocation(null);
                    queueDriverRoute(driver, nextStop.location(), nextState, idx);
                    eventBus.publish(new DriverStateChanged(
                            driver.getId(), oldState, DriverState.ROUTE_PENDING));
                } else {
                    DriverState oldState = driver.getState();
                    driver.setState(DriverState.ONLINE_IDLE);
                    driver.setTargetLocation(null);
                    driver.setActiveBundleId(null);
                    driver.clearRouteWaypoints();
                    driver.clearPendingRoute();
                    driver.setAssignedSequence(null);
                    clearLegacyGuardrailDriver(driver);
                    postDropIdleTickByDriver.put(driver.getId(), tickCounter.get());
                    postDropOpportunityCount++;
                    if (dispatchMode == DispatchMode.COMPACT) {
                        compactRuntimeCoordinator.markDriverIdle(driver.getId(), tickCounter.get(), simulatedNow);
                    }
                    miniDispatchRequested = true;
                    eventBus.publish(new DriverStateChanged(driver.getId(),
                                oldState, DriverState.ONLINE_IDLE));
                }
            }
        }

        // Customer cancellations are modeled as a short-horizon hazard before pickup only.
        for (Order order : activeOrders) {
            if (isCustomerCancellable(order.getStatus())) {
                double subTickHazard = computeCancellationSubTickProbability(order, simulatedNow);
                double shortHorizonRisk = 1.0 - Math.pow(
                        1.0 - subTickHazard,
                        Math.max(1.0, 15.0 * 60.0 / SimulationClock.SUB_TICK_SECONDS));

                order.setCancellationRisk(shortHorizonRisk);

                if (rng.nextDouble() < subTickHazard) {
                    order.markCancelled("customer_cancelled", simulatedNow);
                    cancelledOrders.add(order);
                    
                    if (order.getAssignedDriverId() != null) {
                        Driver driver = drivers.stream()
                                .filter(d -> d.getId().equals(order.getAssignedDriverId()))
                                .findFirst().orElse(null);
                            if (driver != null && driver.getActiveOrderIds().contains(order.getId())) {
                                driver.removeOrder(order.getId());
                                if (driver.getActiveOrderIds().isEmpty()) {
                                    driver.setState(DriverState.ONLINE_IDLE);
                                    driver.setTargetLocation(null);
                                    driver.setActiveBundleId(null);
                                    driver.setAssignedSequence(null);
                                    driver.clearRouteWaypoints();
                                    driver.clearPendingRoute();
                                    clearLegacyGuardrailDriver(driver);
                                    if (dispatchMode == DispatchMode.COMPACT) {
                                        compactRuntimeCoordinator.markDriverIdle(
                                                driver.getId(),
                                                tickCounter.get(),
                                                simulatedNow);
                                    }
                                    miniDispatchRequested = true;
                                }
                            }
                        }
                    eventBus.publish(new OrderCancelled(order.getId(), "customer_cancelled"));
                    dbService.saveOrder(order);
                    if (dispatchMode == DispatchMode.COMPACT
                            && order.getDecisionTraceId() != null) {
                        compactRuntimeCoordinator.recordOrderCancelled(
                                order.getDecisionTraceId(),
                                order.getId());
                    }

                    // Omega learning callback
                    if (dispatchMode == DispatchMode.OMEGA) {
                        omegaAgent.onOrderCancelled(order, trafficIntensity,
                                weatherProfile, simulatedHour, tickCounter.get(), simulatedNow);
                    }
                }
            }
        }

        // Clean up delivered/cancelled from active list
        activeOrders.removeIf(o -> o.getStatus() == OrderStatus.DELIVERED
                || o.getStatus() == OrderStatus.CANCELLED
                || o.getStatus() == OrderStatus.FAILED);
    }

    private void resolveExpiredCompactDecisions(Instant now) {
        if (dispatchMode != DispatchMode.COMPACT) {
            return;
        }
        compactRuntimeCoordinator.expire(tickCounter.get(), now);
    }

    private boolean isOmegaPrePickupAugmentableDriver(Driver driver) {
        return driver != null
                && driver.isPrePickupAugmentable()
                && !isLegacyGuardrailDriver(driver);
    }

    private boolean isLegacyGuardrailDriver(Driver driver) {
        return driver != null && legacyGuardrailDriverIds.contains(driver.getId());
    }

    private boolean isLegacyGuardrailPlan(DispatchPlan plan) {
        return plan != null && plan.isLegacyGuardrailPlan();
    }

    private void syncLegacyGuardrailDriver(Driver driver, DispatchPlan plan) {
        if (driver == null) {
            return;
        }
        if (isLegacyGuardrailPlan(plan)) {
            legacyGuardrailDriverIds.add(driver.getId());
        } else {
            legacyGuardrailDriverIds.remove(driver.getId());
        }
    }

    private void clearLegacyGuardrailDriver(Driver driver) {
        if (driver != null) {
            legacyGuardrailDriverIds.remove(driver.getId());
        }
    }

    private boolean isCustomerCancellable(OrderStatus status) {
        return status == OrderStatus.CONFIRMED
                || status == OrderStatus.PENDING_ASSIGNMENT
                || status == OrderStatus.ASSIGNED
                || status == OrderStatus.PICKUP_EN_ROUTE;
    }

    private double computeCancellationSubTickProbability(Order order, Instant simulatedNow) {
        double waitMinutes = order.getCreatedAt() != null
                ? java.time.Duration.between(order.getCreatedAt(), simulatedNow).toSeconds() / 60.0
                : 0.0;

        boolean unassigned = order.getAssignedDriverId() == null;
        double effectiveWait = Math.max(0.0, waitMinutes - (unassigned ? 2.0 : 4.0));
        double weatherPenalty = switch (weatherProfile) {
            case CLEAR -> 0.0;
            case LIGHT_RAIN -> 0.15;
            case HEAVY_RAIN -> 0.45;
            case STORM -> 0.80;
        };
        double demandPenalty = Math.max(0.0, demandMultiplier - 1.0) * 0.20;
        double congestionPenalty = trafficIntensity * 0.40;
        double assignedShield = unassigned ? 0.0 : 0.75;

        double linear = -3.8
                + (effectiveWait * 0.38)
                + weatherPenalty
                + demandPenalty
                + congestionPenalty
                - assignedShield;

        double perMinuteProbability = 0.18 / (1.0 + Math.exp(-linear));
        double subTickFraction = SimulationClock.SUB_TICK_SECONDS / 60.0;
        return 1.0 - Math.pow(1.0 - perMinuteProbability, subTickFraction);
    }

    // ── Surge detection ─────────────────────────────────────────────────
    private void detectSurges() {
        for (Region region : regions) {
            long pendingInRegion = activeOrders.stream()
                    .filter(o -> o.getPickupRegionId().equals(region.getId()))
                    .filter(o -> o.getStatus() == OrderStatus.PENDING_ASSIGNMENT
                            || o.getStatus() == OrderStatus.CONFIRMED)
                    .count();

            long driversInRegion = drivers.stream()
                    .filter(d -> d.getState() == DriverState.ONLINE_IDLE)
                    .filter(d -> region.contains(d.getCurrentLocation()))
                    .count();

            region.setCurrentDriverSupply(driversInRegion);
            double shortage = region.getShortageRatio();

            double surgeScore = 0.30 * Math.min(1, pendingInRegion / 5.0)
                    + 0.20 * shortage
                    + 0.15 * region.getCongestionScore()
                    + 0.15 * region.getRainIntensity()
                    + 0.10 * trafficIntensity
                    + 0.10 * (demandMultiplier - 1.0);

            surgeScore = Math.max(0, Math.min(1, surgeScore));

            SurgeSeverity severity;
            if (surgeScore >= 0.75) severity = SurgeSeverity.CRITICAL;
            else if (surgeScore >= 0.55) severity = SurgeSeverity.HIGH;
            else if (surgeScore >= 0.35) severity = SurgeSeverity.MEDIUM;
            else severity = SurgeSeverity.NORMAL;

            SurgeSeverity old = region.getSurgeSeverity();
            region.setSurgeSeverity(severity);

            if (severity != SurgeSeverity.NORMAL && severity != old) {
                surgeEventsCounter++;
                String cause = buildSurgeCause(pendingInRegion, shortage, region);
                eventBus.publish(new SurgeDetected(region.getId(), surgeScore, severity, cause));
                eventBus.publish(new AlertRaised(
                        "SURGE-" + region.getId() + "-" + tickCounter.get(),
                        AlertType.SURGE,
                        severity + " surge in " + region.getName(),
                        cause,
                        severity,
                        region.getId(),
                        clock.currentInstant()
                ));

                // AI insight
                if (severity == SurgeSeverity.HIGH || severity == SurgeSeverity.CRITICAL) {
                    int reroute = (int) (driversInRegion * 0.3 + 2);
                    eventBus.publish(new AiInsight(
                            "Surge Prediction",
                            region.getName() + " surges detected. Rerouting " + reroute + " drivers.",
                            "Deadhead -" + (int)(surgeScore * 20) + "%",
                            clock.currentInstant()
                    ));
                }
            }

            if (shortage > 0.5) {
                shortageEventsCounter++;
                eventBus.publish(new DriverShortageDetected(region.getId(), shortage));
            }
        }
    }

    private String buildSurgeCause(long pending, double shortage, Region region) {
        StringBuilder sb = new StringBuilder();
        if (pending > 3) sb.append("High order backlog (").append(pending).append("). ");
        if (shortage > 0.5) sb.append("Driver shortage (").append((int)(shortage*100)).append("%). ");
        if (region.getRainIntensity() > 0.5) sb.append("Heavy rain. ");
        if (region.getCongestionScore() > 0.6) sb.append("Traffic congestion. ");
        return sb.toString().trim();
    }

    // ── Driver productivity tracking ─────────────────────────────────────
    private void trackDriverProductivity() {
        for (Driver driver : drivers) {
            driver.tickProductivity();
        }
    }

    // ── Metrics computation ─────────────────────────────────────────────
    private void computeMetrics() {
        int active = activeOrders.size();
        int activeDriverCount = (int) drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE).count();

        double onTime = totalDelivered > 0
                ? (double)(totalDelivered - totalLateDelivered) / totalDelivered * 100 : 100;

        double deadheadPct = totalDeadheadKm > 0 && totalDelivered > 0
                ? Math.min(40, totalDeadheadKm / (totalDelivered * 2) * 100) : 0;

        double netPerHour = totalEarnings / Math.max(1, tickCounter.get() / 3600.0);

        double avgAssignLatency = totalAssignments > 0
                ? (double) totalAssignmentLatencyMs / totalAssignments / 1000.0 : 0;

        double avgUtilization = drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE)
                .mapToDouble(Driver::getComputedUtilization)
                .average().orElse(0);

        int totalOrders = totalDelivered + cancelledOrders.size() + active;
        double bundleRate = totalOrders > 0 ? (double) totalBundled / totalOrders * 100 : 0;

        eventBus.publish(new MetricsSnapshot(
                Math.round(onTime * 10) / 10.0,
                Math.round(deadheadPct * 10) / 10.0,
                Math.round(netPerHour),
                avgAssignLatency,
                active,
                activeDriverCount,
                totalDelivered,
                cancelledOrders.size(),
                Math.round(bundleRate * 10) / 10.0,
                reDispatchEngine.getReDispatchCount(),
                Math.round(avgUtilization * 1000) / 1000.0
        ));
    }

    // ── Run report generation ────────────────────────────────────────────
    private void generateRunReport() {
        if (totalDelivered == 0 && activeOrders.isEmpty()) return;

        RunReport report = createRunReport("simulation-" + dispatchMode.name().toLowerCase(), 42);
        PlatformRuntimeBootstrap.recordRunReport(report);
        BenchmarkArtifactWriter.writeControlRoomFrame(createControlRoomFrame(report));

        System.out.println(report.toSummary());
        eventBus.publish(new RunReportGenerated(
                report.runId(), report.scenarioName(),
                report.completionRate(), report.onTimeRate(),
                report.deadheadDistanceRatio(), report.bundleRate(),
                report.reDispatchCount(), clock.currentInstant()
        ));
    }

    public RunReport createRunReport(String scenarioName, long seed) {
        RunReportExporter exporter = new RunReportExporter(currentRunId, scenarioName, seed, clock.startInstant());
        double wallClockSeconds = Math.max(
                0.001,
                (System.nanoTime() - runWallClockStartedNanos) / 1_000_000_000.0);
        double tickThroughputPerSec = tickCounter.get() / wallClockSeconds;
        return exporter.generateReport(
                drivers, completedOrders, cancelledOrders, activeOrders,
                totalDelivered, totalLateDelivered, totalDeadheadKm, totalEarnings,
                totalAssignmentLatencyMs,
                dispatchDecisionLatencySamples,
                modelInferenceLatencySamples,
                neuralPriorLatencySamples,
                dispatchStageTimingSamples,
                replayRetrainLatencySamples,
                assignmentAgingLatencySamples,
                tickThroughputPerSec,
                totalAssignments, totalBundled,
                reDispatchEngine.getReDispatchCount(),
                tickCounter.get(), surgeEventsCounter, shortageEventsCounter,
                totalDeliveryCorridorScore, totalLastDropLandingScore,
                totalExpectedPostCompletionEmptyKm, totalExpectedNextOrderIdleMinutes,
                totalZigZagPenalty, routeMetricPlanCount,
                lastDropGoodZoneCount, visibleBundleThreePlusCount,
                cleanRegimeOrderDecisionCount, cleanRegimeSubThreeSelectedCount,
                cleanRegimeWaveAssemblyHoldCount, cleanRegimeThirdOrderLaunchCount,
                stressDowngradeSelectionCount, totalSelectedOrderPlanCount,
                realAssignedPlanCount, holdOnlySelectionCount, prePickupAugmentationCount,
                borrowedExecutedDeadheadKm, fallbackExecutedDeadheadKm, waveExecutedDeadheadKm,
                postDropOpportunityCount, postDropOrderHitCount, totalPredictedPostDropOpportunity,
                totalTrafficForecastAbsError, totalWeatherForecastHitRate, forecastDecisionCount,
                totalBorrowSuccessCalibrationGap, borrowSuccessCalibrationCount,
                noDriverFoundOrderIds.size(),
                recoveryAccumulator,
                clock.currentInstant()
        );
    }

    public ControlRoomFrame createControlRoomFrame(RunReport report) {
        return ControlRoomFrameBuilder.buildFromEngine(this, report);
    }

    // ── Timeline snapshot recorder ───────────────────────────────────────
    private void recordTimelineSnapshot(long tick) {
        // Average traffic severity across all corridors
        double avgTraffic = corridorSeverity.values().stream()
                .mapToDouble(Double::doubleValue).average().orElse(0);

        // Weather intensity: average rain across regions
        double avgWeather = regions.stream()
                .mapToDouble(Region::getRainIntensity).average().orElse(0);

        // Max surge score across regions
        double maxSurge = 0;
        for (Region region : regions) {
            double surgeVal = switch (region.getSurgeSeverity()) {
                case NORMAL -> 0.0;
                case MEDIUM -> 0.4;
                case HIGH -> 0.7;
                case CRITICAL -> 1.0;
            };
            maxSurge = Math.max(maxSurge, surgeVal);
        }

        // Pending orders count
        int pending = (int) activeOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.PENDING_ASSIGNMENT
                        || o.getStatus() == OrderStatus.CONFIRMED)
                .count();

        // Active drivers count
        int activeDriverCount = (int) drivers.stream()
                .filter(d -> d.getState() != DriverState.OFFLINE).count();

        // Road description
        String roadDesc = buildRoadDescription(avgTraffic, avgWeather, maxSurge);

        TimelineDataPoint dp = new TimelineDataPoint(
                tick, simulatedHour, simulatedMinute,
                getSimulatedTimeFormatted(),
                avgTraffic, avgWeather, maxSurge,
                pending, activeDriverCount, roadDesc
        );

        timelineHistory.addLast(dp);
        while (timelineHistory.size() > TIMELINE_HISTORY_MAX) {
            timelineHistory.pollFirst();
        }

        // Publish event for UI consumption
        eventBus.publish(new TimelineSnapshot(
                dp.formattedTime(), dp.avgTrafficSeverity(),
                dp.weatherIntensity(), dp.maxSurgeScore(),
                dp.pendingOrders(), dp.activeDrivers(), dp.roadDescription()
        ));
    }

    private String buildRoadDescription(double avgTraffic, double avgWeather, double maxSurge) {
        StringBuilder sb = new StringBuilder();
        if (avgTraffic > 0.7) sb.append("Severe congestion. ");
        else if (avgTraffic > 0.4) sb.append("Moderate traffic. ");
        else sb.append("Roads clear. ");

        if (avgWeather > 0.6) sb.append("Heavy rain, slippery roads. ");
        else if (avgWeather > 0.2) sb.append("Light rain. ");

        if (maxSurge >= 0.7) sb.append("Order surge active. ");
        return sb.toString().trim();
    }

    // ── Utilities ────────────────────────────────────────────────────────
    private GeoPoint randomPointInRegion(Region region) {
        int maxRetries = 10;
        for (int i = 0; i < maxRetries; i++) {
            double angle = rng.nextDouble() * 2 * Math.PI;
            double dist = Math.sqrt(rng.nextDouble()) * region.getRadiusMeters();
            double dLat = dist * Math.cos(angle) / 111320.0;
            double dLng = dist * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(region.getCenter().lat())));
            GeoPoint point = new GeoPoint(region.getCenter().lat() + dLat, region.getCenter().lng() + dLng);

            boolean inRegionBounds = false;
            for (Region r : regions) {
                if (r.contains(point)) {
                    inRegionBounds = true;
                    break;
                }
            }

            if (inRegionBounds && isValidLandPoint(point)) return point;
        }
        return region.getCenter();
    }

    private boolean isValidLandPoint(GeoPoint p) {
        double lat = p.lat();
        double lng = p.lng();
        if (lat >= 10.760 && lat <= 10.785 && lng >= 106.715 && lng <= 106.735) return false;
        if (lat >= 10.795 && lat <= 10.825 && lng >= 106.720 && lng <= 106.745) return false;
        if (lat >= 10.750 && lat <= 10.765 && lng >= 106.705 && lng <= 106.725) return false;
        if (lat < 10.710 && lng > 106.720) return false;
        return true;
    }

    private String nextRunId() {
        long sequence = runSequence.incrementAndGet();
        long globalSequence = GLOBAL_RUN_SEQUENCE.incrementAndGet();
        return "RUN-s" + randomSeed + "-" + String.format("%06d", sequence)
                + "-g" + String.format("%06d", globalSequence);
    }
}
