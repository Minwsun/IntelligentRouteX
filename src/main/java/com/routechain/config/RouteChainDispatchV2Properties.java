package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "routechain.dispatch-v2")
public class RouteChainDispatchV2Properties {
    private boolean enabled = false;
    private boolean mlEnabled = false;
    private boolean sidecarRequired = false;
    private boolean selectorOrtoolsEnabled = false;
    private boolean warmStartEnabled = true;
    private boolean hotStartEnabled = true;
    private boolean tomtomEnabled = false;
    private boolean openMeteoEnabled = true;
    private Duration tick = Duration.ofSeconds(30);
    private final Buffer buffer = new Buffer();
    private final Cluster cluster = new Cluster();
    private final Bundle bundle = new Bundle();
    private final Candidate candidate = new Candidate();
    private final Context context = new Context();
    private final Pair pair = new Pair();
    private final MicroCluster microCluster = new MicroCluster();
    private final BoundaryExpansion boundaryExpansion = new BoundaryExpansion();
    private final Scenario scenario = new Scenario();
    private final Selector selector = new Selector();
    private final Feedback feedback = new Feedback();
    private final WarmHotStart warmHotStart = new WarmHotStart();

    public static RouteChainDispatchV2Properties defaults() {
        return new RouteChainDispatchV2Properties();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isMlEnabled() {
        return mlEnabled;
    }

    public void setMlEnabled(boolean mlEnabled) {
        this.mlEnabled = mlEnabled;
    }

    public boolean isSidecarRequired() {
        return sidecarRequired;
    }

    public void setSidecarRequired(boolean sidecarRequired) {
        this.sidecarRequired = sidecarRequired;
    }

    public boolean isSelectorOrtoolsEnabled() {
        return selectorOrtoolsEnabled;
    }

    public void setSelectorOrtoolsEnabled(boolean selectorOrtoolsEnabled) {
        this.selectorOrtoolsEnabled = selectorOrtoolsEnabled;
    }

    public boolean isWarmStartEnabled() {
        return warmStartEnabled;
    }

    public void setWarmStartEnabled(boolean warmStartEnabled) {
        this.warmStartEnabled = warmStartEnabled;
    }

    public boolean isHotStartEnabled() {
        return hotStartEnabled;
    }

    public void setHotStartEnabled(boolean hotStartEnabled) {
        this.hotStartEnabled = hotStartEnabled;
    }

    public boolean isTomtomEnabled() {
        return tomtomEnabled;
    }

    public void setTomtomEnabled(boolean tomtomEnabled) {
        this.tomtomEnabled = tomtomEnabled;
    }

    public boolean isOpenMeteoEnabled() {
        return openMeteoEnabled;
    }

    public void setOpenMeteoEnabled(boolean openMeteoEnabled) {
        this.openMeteoEnabled = openMeteoEnabled;
    }

    public Duration getTick() {
        return tick;
    }

    public void setTick(Duration tick) {
        this.tick = tick;
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public Cluster getCluster() {
        return cluster;
    }

    public Bundle getBundle() {
        return bundle;
    }

    public Candidate getCandidate() {
        return candidate;
    }

    public Context getContext() {
        return context;
    }

    public Pair getPair() {
        return pair;
    }

    public MicroCluster getMicroCluster() {
        return microCluster;
    }

    public BoundaryExpansion getBoundaryExpansion() {
        return boundaryExpansion;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public Selector getSelector() {
        return selector;
    }

    public Feedback getFeedback() {
        return feedback;
    }

    public WarmHotStart getWarmHotStart() {
        return warmHotStart;
    }

    public static final class Buffer {
        private Duration holdWindow = Duration.ofSeconds(45);

        public Duration getHoldWindow() {
            return holdWindow;
        }

        public void setHoldWindow(Duration holdWindow) {
            this.holdWindow = holdWindow;
        }
    }

    public static final class Cluster {
        private int maxSize = 24;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }

    public static final class Bundle {
        private int maxSize = 5;
        private int topNeighbors = 12;
        private int beamWidth = 16;

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getTopNeighbors() {
            return topNeighbors;
        }

        public void setTopNeighbors(int topNeighbors) {
            this.topNeighbors = topNeighbors;
        }

        public int getBeamWidth() {
            return beamWidth;
        }

        public void setBeamWidth(int beamWidth) {
            this.beamWidth = beamWidth;
        }
    }

    public static final class Candidate {
        private int maxAnchors = 3;
        private int maxDrivers = 8;
        private int maxRouteAlternatives = 4;

        public int getMaxAnchors() {
            return maxAnchors;
        }

        public void setMaxAnchors(int maxAnchors) {
            this.maxAnchors = maxAnchors;
        }

        public int getMaxDrivers() {
            return maxDrivers;
        }

        public void setMaxDrivers(int maxDrivers) {
            this.maxDrivers = maxDrivers;
        }

        public int getMaxRouteAlternatives() {
            return maxRouteAlternatives;
        }

        public void setMaxRouteAlternatives(int maxRouteAlternatives) {
            this.maxRouteAlternatives = maxRouteAlternatives;
        }
    }

    public static final class Context {
        private double baselineSpeedKph = 22.0;
        private double heavyRainMultiplier = 1.28;
        private double lightRainMultiplier = 1.07;
        private int tomtomRefineBudgetPerTick = 8;
        private final Freshness freshness = new Freshness();
        private final Timeouts timeouts = new Timeouts();

        public double getBaselineSpeedKph() {
            return baselineSpeedKph;
        }

        public void setBaselineSpeedKph(double baselineSpeedKph) {
            this.baselineSpeedKph = baselineSpeedKph;
        }

        public double getHeavyRainMultiplier() {
            return heavyRainMultiplier;
        }

        public void setHeavyRainMultiplier(double heavyRainMultiplier) {
            this.heavyRainMultiplier = heavyRainMultiplier;
        }

        public double getLightRainMultiplier() {
            return lightRainMultiplier;
        }

        public void setLightRainMultiplier(double lightRainMultiplier) {
            this.lightRainMultiplier = lightRainMultiplier;
        }

        public int getTomtomRefineBudgetPerTick() {
            return tomtomRefineBudgetPerTick;
        }

        public void setTomtomRefineBudgetPerTick(int tomtomRefineBudgetPerTick) {
            this.tomtomRefineBudgetPerTick = tomtomRefineBudgetPerTick;
        }

        public Freshness getFreshness() {
            return freshness;
        }

        public Timeouts getTimeouts() {
            return timeouts;
        }
    }

    public static final class Freshness {
        private Duration weatherMaxAge = Duration.ofMinutes(15);
        private Duration trafficMaxAge = Duration.ofMinutes(10);
        private Duration forecastMaxAge = Duration.ofMinutes(30);

        public Duration getWeatherMaxAge() {
            return weatherMaxAge;
        }

        public void setWeatherMaxAge(Duration weatherMaxAge) {
            this.weatherMaxAge = weatherMaxAge;
        }

        public Duration getTrafficMaxAge() {
            return trafficMaxAge;
        }

        public void setTrafficMaxAge(Duration trafficMaxAge) {
            this.trafficMaxAge = trafficMaxAge;
        }

        public Duration getForecastMaxAge() {
            return forecastMaxAge;
        }

        public void setForecastMaxAge(Duration forecastMaxAge) {
            this.forecastMaxAge = forecastMaxAge;
        }
    }

    public static final class Timeouts {
        private Duration etaMlTimeout = Duration.ofMillis(150);

        public Duration getEtaMlTimeout() {
            return etaMlTimeout;
        }

        public void setEtaMlTimeout(Duration etaMlTimeout) {
            this.etaMlTimeout = etaMlTimeout;
        }
    }

    public static final class Pair {
        private double pickupDistanceKmThreshold = 2.2;
        private int readyGapMinutesThreshold = 15;
        private double dropAngleDiffDegreesThreshold = 55.0;
        private double mergeEtaRatioThreshold = 1.25;
        private double scoreThreshold = 0.45;
        private Duration mlTimeout = Duration.ofMillis(120);
        private int maxCandidateNeighborsPerOrder = 12;
        private final WeatherTightened weatherTightened = new WeatherTightened();

        public double getPickupDistanceKmThreshold() {
            return pickupDistanceKmThreshold;
        }

        public void setPickupDistanceKmThreshold(double pickupDistanceKmThreshold) {
            this.pickupDistanceKmThreshold = pickupDistanceKmThreshold;
        }

        public int getReadyGapMinutesThreshold() {
            return readyGapMinutesThreshold;
        }

        public void setReadyGapMinutesThreshold(int readyGapMinutesThreshold) {
            this.readyGapMinutesThreshold = readyGapMinutesThreshold;
        }

        public double getDropAngleDiffDegreesThreshold() {
            return dropAngleDiffDegreesThreshold;
        }

        public void setDropAngleDiffDegreesThreshold(double dropAngleDiffDegreesThreshold) {
            this.dropAngleDiffDegreesThreshold = dropAngleDiffDegreesThreshold;
        }

        public double getMergeEtaRatioThreshold() {
            return mergeEtaRatioThreshold;
        }

        public void setMergeEtaRatioThreshold(double mergeEtaRatioThreshold) {
            this.mergeEtaRatioThreshold = mergeEtaRatioThreshold;
        }

        public double getScoreThreshold() {
            return scoreThreshold;
        }

        public void setScoreThreshold(double scoreThreshold) {
            this.scoreThreshold = scoreThreshold;
        }

        public Duration getMlTimeout() {
            return mlTimeout;
        }

        public void setMlTimeout(Duration mlTimeout) {
            this.mlTimeout = mlTimeout;
        }

        public int getMaxCandidateNeighborsPerOrder() {
            return maxCandidateNeighborsPerOrder;
        }

        public void setMaxCandidateNeighborsPerOrder(int maxCandidateNeighborsPerOrder) {
            this.maxCandidateNeighborsPerOrder = maxCandidateNeighborsPerOrder;
        }

        public WeatherTightened getWeatherTightened() {
            return weatherTightened;
        }
    }

    public static final class WeatherTightened {
        private double pickupDistanceKmThreshold = 1.4;
        private int readyGapMinutesThreshold = 10;
        private double mergeEtaRatioThreshold = 1.15;

        public double getPickupDistanceKmThreshold() {
            return pickupDistanceKmThreshold;
        }

        public void setPickupDistanceKmThreshold(double pickupDistanceKmThreshold) {
            this.pickupDistanceKmThreshold = pickupDistanceKmThreshold;
        }

        public int getReadyGapMinutesThreshold() {
            return readyGapMinutesThreshold;
        }

        public void setReadyGapMinutesThreshold(int readyGapMinutesThreshold) {
            this.readyGapMinutesThreshold = readyGapMinutesThreshold;
        }

        public double getMergeEtaRatioThreshold() {
            return mergeEtaRatioThreshold;
        }

        public void setMergeEtaRatioThreshold(double mergeEtaRatioThreshold) {
            this.mergeEtaRatioThreshold = mergeEtaRatioThreshold;
        }
    }

    public static final class MicroCluster {
        private int timeBucketMinutes = 15;
        private double splitScoreThreshold = 0.55;

        public int getTimeBucketMinutes() {
            return timeBucketMinutes;
        }

        public void setTimeBucketMinutes(int timeBucketMinutes) {
            this.timeBucketMinutes = timeBucketMinutes;
        }

        public double getSplitScoreThreshold() {
            return splitScoreThreshold;
        }

        public void setSplitScoreThreshold(double splitScoreThreshold) {
            this.splitScoreThreshold = splitScoreThreshold;
        }
    }

    public static final class BoundaryExpansion {
        private double minSupportScoreThreshold = 0.52;
        private int maxBoundaryOrdersPerCluster = 2;
        private double weatherTightenedSupportThreshold = 0.62;

        public double getMinSupportScoreThreshold() {
            return minSupportScoreThreshold;
        }

        public void setMinSupportScoreThreshold(double minSupportScoreThreshold) {
            this.minSupportScoreThreshold = minSupportScoreThreshold;
        }

        public int getMaxBoundaryOrdersPerCluster() {
            return maxBoundaryOrdersPerCluster;
        }

        public void setMaxBoundaryOrdersPerCluster(int maxBoundaryOrdersPerCluster) {
            this.maxBoundaryOrdersPerCluster = maxBoundaryOrdersPerCluster;
        }

        public double getWeatherTightenedSupportThreshold() {
            return weatherTightenedSupportThreshold;
        }

        public void setWeatherTightenedSupportThreshold(double weatherTightenedSupportThreshold) {
            this.weatherTightenedSupportThreshold = weatherTightenedSupportThreshold;
        }
    }

    public static final class Scenario {
        private double weatherBadEtaMultiplier = 1.18;
        private double trafficBadEtaMultiplier = 1.22;
        private int merchantDelayMinutes = 6;
        private double driverDriftPenalty = 0.08;
        private double pickupQueuePenalty = 0.06;

        public double getWeatherBadEtaMultiplier() {
            return weatherBadEtaMultiplier;
        }

        public void setWeatherBadEtaMultiplier(double weatherBadEtaMultiplier) {
            this.weatherBadEtaMultiplier = weatherBadEtaMultiplier;
        }

        public double getTrafficBadEtaMultiplier() {
            return trafficBadEtaMultiplier;
        }

        public void setTrafficBadEtaMultiplier(double trafficBadEtaMultiplier) {
            this.trafficBadEtaMultiplier = trafficBadEtaMultiplier;
        }

        public int getMerchantDelayMinutes() {
            return merchantDelayMinutes;
        }

        public void setMerchantDelayMinutes(int merchantDelayMinutes) {
            this.merchantDelayMinutes = merchantDelayMinutes;
        }

        public double getDriverDriftPenalty() {
            return driverDriftPenalty;
        }

        public void setDriverDriftPenalty(double driverDriftPenalty) {
            this.driverDriftPenalty = driverDriftPenalty;
        }

        public double getPickupQueuePenalty() {
            return pickupQueuePenalty;
        }

        public void setPickupQueuePenalty(double pickupQueuePenalty) {
            this.pickupQueuePenalty = pickupQueuePenalty;
        }
    }

    public static final class Selector {
        private boolean greedyRepairEnabled = true;
        private int repairPassLimit = 1;
        private double fallbackPenalty = 0.03;

        public boolean isGreedyRepairEnabled() {
            return greedyRepairEnabled;
        }

        public void setGreedyRepairEnabled(boolean greedyRepairEnabled) {
            this.greedyRepairEnabled = greedyRepairEnabled;
        }

        public int getRepairPassLimit() {
            return repairPassLimit;
        }

        public void setRepairPassLimit(int repairPassLimit) {
            this.repairPassLimit = repairPassLimit;
        }

        public double getFallbackPenalty() {
            return fallbackPenalty;
        }

        public void setFallbackPenalty(double fallbackPenalty) {
            this.fallbackPenalty = fallbackPenalty;
        }
    }

    public static final class Feedback {
        private boolean decisionLogEnabled = true;
        private boolean snapshotEnabled = true;
        private boolean replayEnabled = true;

        public boolean isDecisionLogEnabled() {
            return decisionLogEnabled;
        }

        public void setDecisionLogEnabled(boolean decisionLogEnabled) {
            this.decisionLogEnabled = decisionLogEnabled;
        }

        public boolean isSnapshotEnabled() {
            return snapshotEnabled;
        }

        public void setSnapshotEnabled(boolean snapshotEnabled) {
            this.snapshotEnabled = snapshotEnabled;
        }

        public boolean isReplayEnabled() {
            return replayEnabled;
        }

        public void setReplayEnabled(boolean replayEnabled) {
            this.replayEnabled = replayEnabled;
        }
    }

    public static final class WarmHotStart {
        private boolean loadLatestSnapshotOnBoot = true;

        public boolean isLoadLatestSnapshotOnBoot() {
            return loadLatestSnapshotOnBoot;
        }

        public void setLoadLatestSnapshotOnBoot(boolean loadLatestSnapshotOnBoot) {
            this.loadLatestSnapshotOnBoot = loadLatestSnapshotOnBoot;
        }
    }
}
