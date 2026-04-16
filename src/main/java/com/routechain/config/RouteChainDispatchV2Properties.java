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
}
