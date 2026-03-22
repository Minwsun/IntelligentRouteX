package com.routechain.simulation;

import com.routechain.domain.*;
import com.routechain.domain.Enums.*;
import com.routechain.infra.EventBus;
import com.routechain.infra.Events.*;

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
    private volatile double demandMultiplier = 1.0;
    private volatile int simulatedHour = 12;
    private volatile int simulatedMinute = 0;
    private volatile int initialDriverCount = 30;
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

    // AI dispatch agent (7-layer orchestrator)
    private final DispatchAgent dispatchAgent;
    private final ReDispatchEngine reDispatchEngine = new ReDispatchEngine();

    public SimulationEngine() {
        this.regions = new ArrayList<>(HcmcCityData.createRegions());
        this.corridors = HcmcCityData.createCorridors();
        this.pickupPoints = HcmcCityData.createPickupPoints();
        for (var c : corridors) {
            corridorSeverity.put(c.id(), 0.0);
        }
        this.dispatchAgent = new DispatchAgent(regions);
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
        dispatchAgent.reset();
        reDispatchEngine.reset();
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

    // ── Scenario config ─────────────────────────────────────────────────
    public void setTrafficIntensity(double v) { this.trafficIntensity = Math.max(0, Math.min(1, v)); }
    public void setWeatherProfile(WeatherProfile wp) { this.weatherProfile = wp; }
    public void setDemandMultiplier(double dm) { this.demandMultiplier = dm; }
    public void setInitialDriverCount(int count) { this.initialDriverCount = count; }
    public double getTrafficIntensity() { return trafficIntensity; }
    public WeatherProfile getWeatherProfile() { return weatherProfile; }
    public double getDemandMultiplier() { return demandMultiplier; }
    public int getInitialDriverCount() { return initialDriverCount; }

    // ── Core tick ───────────────────────────────────────────────────────
    private void tick() {
        try {
            long tick = tickCounter.incrementAndGet();
            long totalMinutes = 12 * 60 + tick; // start at noon, 1 tick = 1 simulated minute
            simulatedHour = (int) ((totalMinutes / 60) % 24);
            simulatedMinute = (int) (totalMinutes % 60);

            evolveWeather();
            evolveTraffic();
            generateOrders();
            moveDrivers();
            trackDriverProductivity();
            checkReDispatch();
            dispatchPendingOrders();
            processDeliveries();
            detectSurges();
            computeMetrics();
            recordTimelineSnapshot(tick);

            eventBus.publish(new SimulationTick(tick, Instant.now()));
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

    // ── Order generation (Poisson-like) ─────────────────────────────────
    private void generateOrders() {
        Map<String, Double> baseRates = HcmcCityData.baseDemandRates();
        double hourMult = HcmcCityData.hourlyMultiplier(simulatedHour);
        double weatherMult = switch (weatherProfile) {
            case CLEAR -> 1.0;
            case LIGHT_RAIN -> 1.3;
            case HEAVY_RAIN -> 1.7;
            case STORM -> 0.6; // fewer orders in storm
        };

        for (Region region : regions) {
            double rate = baseRates.getOrDefault(region.getId(), 0.1)
                    * hourMult * weatherMult * demandMultiplier;

            // Poisson approximation
            if (rng.nextDouble() < rate) {
                Order order = createRandomOrder(region);
                activeOrders.add(order);
                region.setCurrentDemandPressure(region.getCurrentDemandPressure() + 1);
                eventBus.publish(new OrderCreated(order));
            }
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

        return new Order("ORD-" + seq, "CUS-" + (rng.nextInt(1000) + 1),
                region.getId(), pickup, dropoff, dropoffRegion.getId(),
                fee, Math.max(5, eta));
    }

    // ── Driver movement ─────────────────────────────────────────────────
    private void moveDrivers() {
        for (Driver driver : drivers) {
            if (driver.getState() == DriverState.OFFLINE) continue;

            GeoPoint target = driver.getTargetLocation();
            if (target == null) {
                // Idle drivers wander slightly
                if (driver.getState() == DriverState.ONLINE_IDLE) {
                    double dx = (rng.nextGaussian() * 0.0004);
                    double dy = (rng.nextGaussian() * 0.0004);
                    GeoPoint newLoc = new GeoPoint(
                            driver.getCurrentLocation().lat() + dx,
                            driver.getCurrentLocation().lng() + dy
                    );
                    driver.setCurrentLocation(newLoc);
                    driver.setSpeedKmh(5 + rng.nextDouble() * 10);
                }
            } else {
                // Calculate speed based on traffic + weather
                double speedMs = (20 + rng.nextDouble() * 20) * (1.0 - trafficIntensity * 0.6);
                if (weatherProfile == WeatherProfile.HEAVY_RAIN) speedMs *= 0.7;
                if (weatherProfile == WeatherProfile.STORM) speedMs *= 0.4;

                // Determine movement target: follow waypoints if available,
                // otherwise fall back to straight-line toward final target
                GeoPoint moveTarget;
                if (driver.hasRouteWaypoints()) {
                    moveTarget = driver.getCurrentWaypoint();
                } else {
                    moveTarget = target;
                }

                GeoPoint newLoc = driver.getCurrentLocation().moveTowards(moveTarget, speedMs);
                driver.setCurrentLocation(newLoc);
                driver.setSpeedKmh(speedMs * 3.6);

                // Check if arrived at current waypoint → advance
                if (driver.hasRouteWaypoints() && newLoc.distanceTo(moveTarget) < 30) {
                    driver.advanceWaypoint();
                }

                // Check if arrived at final destination
                if (newLoc.distanceTo(target) < 30) {
                    driver.setTargetLocation(null);
                    driver.clearRouteWaypoints();
                }
            }

            eventBus.publish(new DriverLocationUpdated(
                    driver.getId(), driver.getCurrentLocation(), driver.getSpeedKmh()));
        }
    }

    // ── AI 7-layer dispatch pipeline ─────────────────────────────────────
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

        // Run full 7-layer dispatch
        DispatchAgent.DispatchResult result = dispatchAgent.dispatch(
                new ArrayList<>(pending), new ArrayList<>(available),
                drivers, activeOrders, simulatedHour,
                trafficIntensity, weatherProfile);

        // Execute selected plans
        for (DispatchPlan plan : result.plans()) {
            Driver best = plan.getDriver();

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

            best.setState(DriverState.PICKUP_EN_ROUTE);
            best.setTargetLocation(plan.getOrders().get(0).getPickupPoint());
            best.setActiveBundleId(plan.getBundle().bundleId());

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
        for (Order order : activeOrders) {
            if (order.getAssignedDriverId() == null) continue;

            Driver driver = drivers.stream()
                    .filter(d -> d.getId().equals(order.getAssignedDriverId()))
                    .findFirst().orElse(null);
            if (driver == null) continue;

            switch (order.getStatus()) {
                case PICKUP_EN_ROUTE -> {
                    if (driver.getTargetLocation() == null) {
                        // Arrived at pickup
                        order.markPickedUp();
                        driver.setState(DriverState.DELIVERING);
                        driver.setTargetLocation(order.getDropoffPoint());
                        eventBus.publish(new OrderPickedUp(order.getId()));
                    }
                }
                case PICKED_UP, DROPOFF_EN_ROUTE -> {
                    order.markDropoffStarted();
                    if (driver.getTargetLocation() == null) {
                        // Arrived at dropoff
                        order.markDelivered();
                        driver.setState(DriverState.ONLINE_IDLE);
                        driver.removeOrder(order.getId());
                        driver.addEarning(order.getQuotedFee());
                        driver.incrementCompletedOrders();
                        totalDelivered++;
                        if (order.isLate()) totalLateDelivered++;
                        totalEarnings += order.getQuotedFee();
                        completedOrders.add(order);
                        eventBus.publish(new OrderDelivered(order.getId()));
                        eventBus.publish(new DriverStateChanged(driver.getId(),
                                DriverState.DELIVERING, DriverState.ONLINE_IDLE));
                    }
                }
                default -> {}
            }

            // Random cancellation risk
            if (order.getStatus() != OrderStatus.DELIVERED
                    && order.getStatus() != OrderStatus.CANCELLED) {
                double risk = trafficIntensity * 0.1
                        + (weatherProfile == WeatherProfile.HEAVY_RAIN ? 0.08 : 0)
                        + (weatherProfile == WeatherProfile.STORM ? 0.15 : 0);
                order.setCancellationRisk(risk);

                if (rng.nextDouble() < risk * 0.01) { // very small chance per tick
                    order.markCancelled("customer_cancelled");
                    cancelledOrders.add(order);
                    if (driver.getActiveOrderIds().contains(order.getId())) {
                        driver.removeOrder(order.getId());
                        driver.setState(DriverState.ONLINE_IDLE);
                        driver.setTargetLocation(null);
                    }
                    eventBus.publish(new OrderCancelled(order.getId(), "customer_cancelled"));
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
        double angle = rng.nextDouble() * 2 * Math.PI;
        double dist = rng.nextDouble() * region.getRadiusMeters();
        double dLat = dist * Math.cos(angle) / 111320.0;
        double dLng = dist * Math.sin(angle) / (111320.0 * Math.cos(Math.toRadians(region.getCenter().lat())));
        return new GeoPoint(
                region.getCenter().lat() + dLat,
                region.getCenter().lng() + dLng
        );
    }
}
