package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events.*;
import com.routechain.infra.DatabaseStorageService;
import com.routechain.ai.OmegaDispatchAgent;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Core simulation engine — orchestrates all generators and dispatches
 * on a configurable tick loop. All events are published to EventBus.
 */
public class SimulationEngine {
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
    private final Random rng = new Random(42);

    // Timeline history buffer (last 1800 ticks = 30 simulated minutes)
    private final Deque<TimelineDataPoint> timelineHistory = new ConcurrentLinkedDeque<>();
    private static final int TIMELINE_HISTORY_MAX = 1800;

    // Metrics accumulators
    private volatile int totalDelivered = 0;
    private volatile int totalLateDelivered = 0;
    private volatile double totalDeadheadKm = 0;
    private volatile double totalEarnings = 0;
    private volatile long totalAssignmentLatencyMs = 0;
    private volatile int totalAssignments = 0;
    private volatile int totalBundled = 0;
    private volatile int surgeEventsCounter = 0;
    private volatile int shortageEventsCounter = 0;

    // AI dispatch agent (Omega — learned multi-agent brain)
    private final OmegaDispatchAgent omegaAgent;
    private final ReDispatchEngine reDispatchEngine = new ReDispatchEngine();
    private final OsrmRoutingService routingService = new OsrmRoutingService();
    private final DatabaseStorageService dbService = new DatabaseStorageService();

    // Enhancements
    private final DriverSupplyEngine driverSupplyEngine = new DriverSupplyEngine();
    private final ScenarioShockEngine shockEngine = new ScenarioShockEngine();
    private final SimulationClock clock;
    private final OrderArrivalEngine orderArrivalEngine;
    private final DriverMotionEngine driverMotionEngine;
    private final MerchantWaitEngine merchantWaitEngine;

    public SimulationEngine() {
        this.regions = new ArrayList<>(HcmcCityData.createRegions());
        this.corridors = HcmcCityData.createCorridors();
        this.pickupPoints = HcmcCityData.createPickupPoints();
        for (var c : corridors) {
            corridorSeverity.put(c.id(), 0.0);
        }
        this.clock = new SimulationClock(simulatedHour, simulatedMinute);
        this.orderArrivalEngine = new OrderArrivalEngine(regions, shockEngine, rng);
        
        // Find intersection points (simple heuristic: centers of all regions)
        List<GeoPoint> intersections = regions.stream().map(Region::getCenter).toList();
        this.driverMotionEngine = new DriverMotionEngine(rng, intersections);
        this.merchantWaitEngine = new MerchantWaitEngine(rng);
        
        this.omegaAgent = new OmegaDispatchAgent(regions);
    }

    // ── Lifecycle ───────────────────────────────────────────────────────
    public void start() {
        if (lifecycle == SimulationLifecycle.RUNNING) return;

        initializeDrivers(initialDriverCount);
        lifecycle = SimulationLifecycle.RUNNING;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sim-engine");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::tick, 0, 1000, TimeUnit.MILLISECONDS);
        eventBus.publish(new SimulationStarted(Instant.now()));
    }

    public void stop() {
        lifecycle = SimulationLifecycle.IDLE;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        // Generate run report on stop
        generateRunReport();
        eventBus.publish(new SimulationStopped(Instant.now()));
    }

    public void reset() {
        stop();
        drivers.clear();
        activeOrders.clear();
        completedOrders.clear();
        cancelledOrders.clear();
        tickCounter.set(0);
        orderIdSeq.set(0);
        totalDelivered = 0;
        totalLateDelivered = 0;
        totalDeadheadKm = 0;
        totalEarnings = 0;
        totalAssignmentLatencyMs = 0;
        totalAssignments = 0;
        totalBundled = 0;
        surgeEventsCounter = 0;
        shortageEventsCounter = 0;
        omegaAgent.reset();
        reDispatchEngine.reset();
        routingService.reset();
        eventBus.publish(new SimulationReset(Instant.now()));
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
    public long getTickCount() { return tickCounter.get(); }
    public List<TimelineDataPoint> getTimelineHistory() {
        return List.copyOf(timelineHistory);
    }
    public int getTotalDelivered() { return totalDelivered; }
    public int getTotalCancelled() { return cancelledOrders.size(); }
    public OmegaDispatchAgent getOmegaAgent() { return omegaAgent; }
    public SimulationClock getClock() { return clock; }

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
            routingService.setHeadlessMode(true);
            initializeDrivers(initialDriverCount);
        }
        tick();
    }

    // ── Scenario config ─────────────────────────────────────────────────
    public void setTrafficIntensity(double v) { this.trafficIntensity = Math.max(0, Math.min(1, v)); }
    public void setWeatherProfile(WeatherProfile wp) { this.weatherProfile = wp; }
    public void setDemandMultiplier(double dm) { this.demandMultiplier = dm; }
    public void setInitialDriverCount(int count) { this.initialDriverCount = count; }
    public DriverSupplyEngine getDriverSupplyEngine() { return driverSupplyEngine; }
    public ScenarioShockEngine getShockEngine() { return shockEngine; }
    public double getTrafficIntensity() { return trafficIntensity; }
    public WeatherProfile getWeatherProfile() { return weatherProfile; }
    public double getDemandMultiplier() { return demandMultiplier; }
    public int getInitialDriverCount() { return initialDriverCount; }

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
                dropoffRegionId, fee, promisedEtaMin);
        activeOrders.add(order);
        eventBus.publish(new OrderCreated(order));
        dbService.saveOrder(order);
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

    // ── Core tick ───────────────────────────────────────────────────────
    private void tick() {
        try {
            clock.advanceSubTick();
            long subTick = clock.getSubTickCounter();
            
            // 1. Movement Sub-tick (Every 5 simulated seconds)
            if (clock.isMovementBoundary()) {
                moveDrivers();
                processDeliveries();
                
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
                dispatchPendingOrders();
                
                omegaAgent.onTick(decTick, driverId -> {
                    return drivers.stream()
                            .filter(d -> d.getId().equals(driverId))
                            .findFirst()
                            .map(Driver::getAvgEarningPerHour)
                            .orElse(0.0);
                });
                
                detectSurges();
                computeMetrics();
            }

            // Traffic refresh (Every 60 simulated seconds)
            if (clock.isTrafficRefreshBoundary()) {
                // Potential high-res traffic update here
            }

            eventBus.publish(new SimulationTick(subTick, Instant.now()));
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
                clock.getSimulatedHour(), weatherProfile, clock.getSubTickCounter());
        
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
                fee, Math.max(5, eta));
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
        List<Order> pending = activeOrders.stream()
                .filter(o -> o.getStatus() == OrderStatus.CONFIRMED
                        || o.getStatus() == OrderStatus.PENDING_ASSIGNMENT)
                .toList();

        if (pending.isEmpty()) return;

        // Mark as pending
        for (Order order : pending) {
            order.setStatus(OrderStatus.PENDING_ASSIGNMENT);
        }

        List<Driver> available = drivers.stream()
                .filter(Driver::isAvailable)
                .toList();

        if (available.isEmpty()) return;

        // Run Omega dispatch pipeline
        OmegaDispatchAgent.DispatchResult result = omegaAgent.dispatch(
                new ArrayList<>(pending), new ArrayList<>(available),
                drivers, activeOrders, simulatedHour,
                trafficIntensity, weatherProfile);

        // Execute selected plans
        for (DispatchPlan plan : result.plans()) {
            Driver best = plan.getDriver();

            if (plan.getOrders().isEmpty()) {
                if (plan.getBundle().bundleId().startsWith("REPOS") && !plan.getSequence().isEmpty()) {
                     best.clearRouteWaypoints();
                     best.setAssignedSequence(plan.getSequence());
                     best.setState(DriverState.REPOSITIONING);
                     best.setTargetLocation(plan.getSequence().get(0).location());
                     eventBus.publish(new DriverStateChanged(best.getId(),
                         DriverState.ONLINE_IDLE, DriverState.REPOSITIONING));
                     routingService.requestRouteAsync(best, best.getCurrentLocation(), best.getTargetLocation());
                }
                continue; // HOLD plans or invalid REPOS plans just skip assignment
            }

            for (Order order : plan.getOrders()) {
                order.assignDriver(best.getId());
                order.markPickupStarted();
                order.setDecisionTraceId(plan.getTraceId());
                order.setBundle(plan.getBundle().bundleId());
                order.setPredictedLateRisk(plan.getLateRisk());
                order.setPredictedBundleFit(plan.getBundleEfficiency());

                best.addOrder(order.getId());

                totalAssignmentLatencyMs += order.getAssignmentLatencyMs();
                totalAssignments++;
            }

            best.clearRouteWaypoints();
            best.setAssignedSequence(plan.getSequence());
            best.setState(DriverState.PICKUP_EN_ROUTE);
            best.setTargetLocation(plan.getSequence().get(0).location());
            best.setActiveBundleId(plan.getBundle().bundleId());
            routingService.requestRouteAsync(best, best.getCurrentLocation(), best.getTargetLocation());

            double deadheadKm = plan.getPredictedDeadheadKm();
            totalDeadheadKm += deadheadKm;
            best.addDeadheadDistance(deadheadKm);
            best.setContinuationValueCurrentZone(plan.getEndZoneOpportunity());
            best.setOverloadRisk(plan.getCancellationRisk());

            if (plan.getBundleSize() > 1) {
                totalBundled += plan.getBundleSize();
            }

            eventBus.publish(new DispatchDecision(
                    plan.getOrders().get(0).getId(), best.getId(),
                    plan.getTotalScore(), plan.getPredictedTotalMinutes(),
                    deadheadKm, plan.getConfidence()));

            for (Order order : plan.getOrders()) {
                eventBus.publish(new OrderAssigned(order.getId(), best.getId()));
            }
            eventBus.publish(new DriverStateChanged(best.getId(),
                    DriverState.ONLINE_IDLE, DriverState.PICKUP_EN_ROUTE));
        }
    }

    // ── Re-dispatch check ────────────────────────────────────────────────
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
        for (Driver driver : drivers) {
            List<DispatchPlan.Stop> seq = driver.getAssignedSequence();
            if (seq == null || driver.getState() == DriverState.ONLINE_IDLE) {
                continue;
            }

            // If driver just arrived at target location (or target is null on initial pickup)
            if (driver.getTargetLocation() == null) {
                int idx = driver.getCurrentSequenceIndex();
                if (idx < seq.size()) {
                    DispatchPlan.Stop currentStop = seq.get(idx);
                    Order order = null;
                    if (currentStop.orderId() != null) {
                        order = activeOrders.stream()
                                .filter(o -> o.getId().equals(currentStop.orderId()))
                                .findFirst().orElse(null);
                    }

                    if (order != null && order.getStatus() != OrderStatus.CANCELLED) {
                        if (currentStop.type() == DispatchPlan.Stop.StopType.PICKUP) {
                            order.markPickedUp();
                            eventBus.publish(new OrderPickedUp(order.getId()));
                        } else if (currentStop.type() == DispatchPlan.Stop.StopType.DROPOFF) {
                            order.markDelivered();
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

                            double distKm = order.getPickupPoint().distanceTo(order.getDropoffPoint()) / 1000.0;
                            double etaActual = distKm / Math.max(6, driver.getSpeedKmh()) * 60;
                            omegaAgent.onOrderDelivered(order, driver, etaActual, order.isLate(),
                                    order.getQuotedFee(), trafficIntensity, weatherProfile,
                                    simulatedHour, tickCounter.get());
                        }
                    }
                    driver.advanceSequenceIndex();
                    idx++;
                }

                // Proceed to next stop in sequence, or finish
                if (idx < seq.size()) {
                    DispatchPlan.Stop nextStop = seq.get(idx);
                    driver.setTargetLocation(nextStop.location());
                    driver.setState(nextStop.type() == DispatchPlan.Stop.StopType.PICKUP ? 
                            DriverState.PICKUP_EN_ROUTE : DriverState.DELIVERING);
                    routingService.requestRouteAsync(driver, driver.getCurrentLocation(), driver.getTargetLocation());
                    
                    // Mark order status appropriately if picking up or delivering
                    if (nextStop.orderId() != null) {
                        Order nextOrder = activeOrders.stream()
                                .filter(o -> o.getId().equals(nextStop.orderId()))
                                .findFirst().orElse(null);
                        if (nextOrder != null && nextOrder.getStatus() != OrderStatus.CANCELLED) {
                            if (nextStop.type() == DispatchPlan.Stop.StopType.PICKUP && nextOrder.getStatus() == OrderStatus.PENDING_ASSIGNMENT) {
                                 nextOrder.markPickupStarted();
                            } else if (nextStop.type() == DispatchPlan.Stop.StopType.DROPOFF) {
                                 nextOrder.markDropoffStarted();
                            }
                        }
                    }
                } else {
                    DriverState oldState = driver.getState();
                    driver.setState(DriverState.ONLINE_IDLE);
                    driver.setAssignedSequence(null);
                    eventBus.publish(new DriverStateChanged(driver.getId(),
                                oldState, DriverState.ONLINE_IDLE));
                }
            }
        }

        // Random cancellation hazard for active orders using Sigmoid approximation
        for (Order order : activeOrders) {
            if (order.getStatus() != OrderStatus.DELIVERED
                    && order.getStatus() != OrderStatus.CANCELLED) {
                
                double waitTimeFactor = order.getStatus() == OrderStatus.PENDING_ASSIGNMENT ? 0.02 : 0.005;
                double weatherFactor = (weatherProfile == WeatherProfile.HEAVY_RAIN ? 0.08 : 0)
                        + (weatherProfile == WeatherProfile.STORM ? 0.15 : 0);
                double shortageFactor = 0.05 * trafficIntensity; 
                
                // Sigmoid: 1 / (1 + exp(-x))
                // We use a base affine translation to keep baseline hazard low (-5.0 base ~ 0.006 hazard)
                double linearSum = -5.0 + (waitTimeFactor * 10) + (weatherFactor * 15) + (shortageFactor * 10);
                double hazard = 1.0 / (1.0 + Math.exp(-linearSum)); 

                order.setCancellationRisk(hazard);

                if (rng.nextDouble() < hazard) {
                    order.markCancelled("customer_cancelled");
                    cancelledOrders.add(order);
                    
                    if (order.getAssignedDriverId() != null) {
                        Driver driver = drivers.stream()
                                .filter(d -> d.getId().equals(order.getAssignedDriverId()))
                                .findFirst().orElse(null);
                        if (driver != null && driver.getActiveOrderIds().contains(order.getId())) {
                            driver.removeOrder(order.getId());
                            // If sequence logic is used, cancelling breaks the sequence partially.
                            // For simplicity, we just clear current routing and let redispatch or idle take over
                            if (driver.getActiveOrderIds().isEmpty()) {
                                driver.setState(DriverState.ONLINE_IDLE);
                                driver.setTargetLocation(null);
                                driver.setAssignedSequence(null);
                                driver.clearRouteWaypoints();
                            }
                        }
                    }
                    eventBus.publish(new OrderCancelled(order.getId(), "customer_cancelled"));
                    dbService.saveOrder(order);

                    // Omega learning callback
                    omegaAgent.onOrderCancelled(order, trafficIntensity,
                            weatherProfile, simulatedHour);
                }
            }
        }

        // Clean up delivered/cancelled from active list
        activeOrders.removeIf(o -> o.getStatus() == OrderStatus.DELIVERED
                || o.getStatus() == OrderStatus.CANCELLED
                || o.getStatus() == OrderStatus.FAILED);
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
                String cause = buildSurgeCause(pendingInRegion, shortage, region);
                eventBus.publish(new SurgeDetected(region.getId(), surgeScore, severity, cause));
                eventBus.publish(new AlertRaised(
                        "SURGE-" + region.getId() + "-" + tickCounter.get(),
                        AlertType.SURGE,
                        severity + " surge in " + region.getName(),
                        cause,
                        severity,
                        region.getId(),
                        Instant.now()
                ));

                // AI insight
                if (severity == SurgeSeverity.HIGH || severity == SurgeSeverity.CRITICAL) {
                    int reroute = (int) (driversInRegion * 0.3 + 2);
                    eventBus.publish(new AiInsight(
                            "Surge Prediction",
                            region.getName() + " surges detected. Rerouting " + reroute + " drivers.",
                            "Deadhead -" + (int)(surgeScore * 20) + "%",
                            Instant.now()
                    ));
                }
            }

            if (shortage > 0.5) {
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

        RunReportExporter exporter = new RunReportExporter("simulation", 42);
        RunReport report = exporter.generateReport(
                drivers, completedOrders, cancelledOrders, activeOrders,
                totalDelivered, totalLateDelivered, totalDeadheadKm, totalEarnings,
                totalAssignmentLatencyMs, totalAssignments, totalBundled,
                reDispatchEngine.getReDispatchCount(),
                tickCounter.get(), surgeEventsCounter, shortageEventsCounter
        );

        System.out.println(report.toSummary());
        eventBus.publish(new RunReportGenerated(
                report.runId(), report.scenarioName(),
                report.completionRate(), report.onTimeRate(),
                report.deadheadDistanceRatio(), report.bundleRate(),
                report.reDispatchCount(), Instant.now()
        ));
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
}
