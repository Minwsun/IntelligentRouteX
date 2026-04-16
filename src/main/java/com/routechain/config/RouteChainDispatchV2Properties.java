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
}

