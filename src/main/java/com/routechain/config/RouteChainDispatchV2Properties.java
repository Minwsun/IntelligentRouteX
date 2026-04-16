package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "routechain.dispatch-v2")
public class RouteChainDispatchV2Properties {
    private boolean enabled = false;
    private boolean deterministicLegacyFallbackEnabled = true;
    private boolean shadowMode = true;
    private final Buffer buffer = new Buffer();
    private final Cluster cluster = new Cluster();
    private final Bundle bundle = new Bundle();
    private final Candidate candidate = new Candidate();
    private final Scenario scenario = new Scenario();
    private final Tomtom tomtom = new Tomtom();
    private final OpenMeteo openMeteo = new OpenMeteo();
    private final Sidecar sidecar = new Sidecar();

    public static RouteChainDispatchV2Properties defaults() {
        return new RouteChainDispatchV2Properties();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDeterministicLegacyFallbackEnabled() {
        return deterministicLegacyFallbackEnabled;
    }

    public void setDeterministicLegacyFallbackEnabled(boolean deterministicLegacyFallbackEnabled) {
        this.deterministicLegacyFallbackEnabled = deterministicLegacyFallbackEnabled;
    }

    public boolean isShadowMode() {
        return shadowMode;
    }

    public void setShadowMode(boolean shadowMode) {
        this.shadowMode = shadowMode;
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

    public Scenario getScenario() {
        return scenario;
    }

    public Tomtom getTomtom() {
        return tomtom;
    }

    public OpenMeteo getOpenMeteo() {
        return openMeteo;
    }

    public Sidecar getSidecar() {
        return sidecar;
    }

    public static final class Buffer {
        private Duration holdWindow = Duration.ofSeconds(45);
        private Duration softReleaseWindow = Duration.ofSeconds(30);
        private int softReleaseOrderCount = 1;

        public Duration getHoldWindow() {
            return holdWindow;
        }

        public void setHoldWindow(Duration holdWindow) {
            this.holdWindow = holdWindow;
        }

        public Duration getSoftReleaseWindow() {
            return softReleaseWindow;
        }

        public void setSoftReleaseWindow(Duration softReleaseWindow) {
            this.softReleaseWindow = softReleaseWindow;
        }

        public int getSoftReleaseOrderCount() {
            return softReleaseOrderCount;
        }

        public void setSoftReleaseOrderCount(int softReleaseOrderCount) {
            this.softReleaseOrderCount = softReleaseOrderCount;
        }
    }

    public static final class Cluster {
        private double similarityThreshold = 0.62;
        private int maxClusterSize = 24;
        private int maxNeighborClusters = 3;

        public double getSimilarityThreshold() {
            return similarityThreshold;
        }

        public void setSimilarityThreshold(double similarityThreshold) {
            this.similarityThreshold = similarityThreshold;
        }

        public int getMaxClusterSize() {
            return maxClusterSize;
        }

        public void setMaxClusterSize(int maxClusterSize) {
            this.maxClusterSize = maxClusterSize;
        }

        public int getMaxNeighborClusters() {
            return maxNeighborClusters;
        }

        public void setMaxNeighborClusters(int maxNeighborClusters) {
            this.maxNeighborClusters = maxNeighborClusters;
        }
    }

    public static final class Bundle {
        private int topNeighbors = 12;
        private int beamWidth = 16;
        private int maxBundleSize = 5;
        private double maxPickupSpreadKm = 2.2;
        private double maxMergeEtaRatio = 1.25;

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

        public int getMaxBundleSize() {
            return maxBundleSize;
        }

        public void setMaxBundleSize(int maxBundleSize) {
            this.maxBundleSize = maxBundleSize;
        }

        public double getMaxPickupSpreadKm() {
            return maxPickupSpreadKm;
        }

        public void setMaxPickupSpreadKm(double maxPickupSpreadKm) {
            this.maxPickupSpreadKm = maxPickupSpreadKm;
        }

        public double getMaxMergeEtaRatio() {
            return maxMergeEtaRatio;
        }

        public void setMaxMergeEtaRatio(double maxMergeEtaRatio) {
            this.maxMergeEtaRatio = maxMergeEtaRatio;
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

    public static final class Scenario {
        private double trafficBadThreshold = 1.25;
        private double tomtomRefineBadThreshold = 1.08;
        private double travelTimeDriftBadThreshold = 0.18;
        private double corridorCongestionBadThreshold = 0.70;
        private double rainProbabilityBadThreshold = 0.35;
        private double rainIntensityBadThreshold = 2.5;
        private double visibilityBadThresholdMeters = 4_000.0;
        private double windSpeedBadThresholdMetersPerSecond = 10.8;
        private double demandShiftProbabilityThreshold = 0.60;
        private double uncertaintyThreshold = 0.30;
        private double zoneBurstProbabilityThreshold = 0.60;

        public double getTrafficBadThreshold() {
            return trafficBadThreshold;
        }

        public void setTrafficBadThreshold(double trafficBadThreshold) {
            this.trafficBadThreshold = trafficBadThreshold;
        }

        public double getTomtomRefineBadThreshold() {
            return tomtomRefineBadThreshold;
        }

        public void setTomtomRefineBadThreshold(double tomtomRefineBadThreshold) {
            this.tomtomRefineBadThreshold = tomtomRefineBadThreshold;
        }

        public double getTravelTimeDriftBadThreshold() {
            return travelTimeDriftBadThreshold;
        }

        public void setTravelTimeDriftBadThreshold(double travelTimeDriftBadThreshold) {
            this.travelTimeDriftBadThreshold = travelTimeDriftBadThreshold;
        }

        public double getCorridorCongestionBadThreshold() {
            return corridorCongestionBadThreshold;
        }

        public void setCorridorCongestionBadThreshold(double corridorCongestionBadThreshold) {
            this.corridorCongestionBadThreshold = corridorCongestionBadThreshold;
        }

        public double getRainProbabilityBadThreshold() {
            return rainProbabilityBadThreshold;
        }

        public void setRainProbabilityBadThreshold(double rainProbabilityBadThreshold) {
            this.rainProbabilityBadThreshold = rainProbabilityBadThreshold;
        }

        public double getRainIntensityBadThreshold() {
            return rainIntensityBadThreshold;
        }

        public void setRainIntensityBadThreshold(double rainIntensityBadThreshold) {
            this.rainIntensityBadThreshold = rainIntensityBadThreshold;
        }

        public double getVisibilityBadThresholdMeters() {
            return visibilityBadThresholdMeters;
        }

        public void setVisibilityBadThresholdMeters(double visibilityBadThresholdMeters) {
            this.visibilityBadThresholdMeters = visibilityBadThresholdMeters;
        }

        public double getWindSpeedBadThresholdMetersPerSecond() {
            return windSpeedBadThresholdMetersPerSecond;
        }

        public void setWindSpeedBadThresholdMetersPerSecond(double windSpeedBadThresholdMetersPerSecond) {
            this.windSpeedBadThresholdMetersPerSecond = windSpeedBadThresholdMetersPerSecond;
        }

        public double getDemandShiftProbabilityThreshold() {
            return demandShiftProbabilityThreshold;
        }

        public void setDemandShiftProbabilityThreshold(double demandShiftProbabilityThreshold) {
            this.demandShiftProbabilityThreshold = demandShiftProbabilityThreshold;
        }

        public double getUncertaintyThreshold() {
            return uncertaintyThreshold;
        }

        public void setUncertaintyThreshold(double uncertaintyThreshold) {
            this.uncertaintyThreshold = uncertaintyThreshold;
        }

        public double getZoneBurstProbabilityThreshold() {
            return zoneBurstProbabilityThreshold;
        }

        public void setZoneBurstProbabilityThreshold(double zoneBurstProbabilityThreshold) {
            this.zoneBurstProbabilityThreshold = zoneBurstProbabilityThreshold;
        }
    }

    public static final class Tomtom {
        private boolean enabled = false;
        private String apiKey = "";
        private String baseUrl = "https://api.tomtom.com";
        private Duration cacheTtl = Duration.ofMinutes(30);
        private Duration timeout = Duration.ofMillis(800);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static final class OpenMeteo {
        private boolean enabled = true;
        private String baseUrl = "https://api.open-meteo.com/v1/forecast";
        private Duration timeout = Duration.ofMillis(800);
        private Duration cacheTtl = Duration.ofMinutes(15);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }

        public Duration getCacheTtl() {
            return cacheTtl;
        }

        public void setCacheTtl(Duration cacheTtl) {
            this.cacheTtl = cacheTtl;
        }
    }

    public static final class Sidecar {
        private boolean enabled = false;
        private String baseUrl = "http://localhost:8092";
        private Duration connectTimeout = Duration.ofMillis(400);
        private Duration readTimeout = Duration.ofMillis(1_500);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
