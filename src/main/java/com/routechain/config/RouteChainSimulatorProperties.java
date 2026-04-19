package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routechain.simulator")
public class RouteChainSimulatorProperties {
    private boolean enabled = true;
    private String artifactBaseDir = "build/simulator";
    private int maxRecentRuns = 50;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getArtifactBaseDir() {
        return artifactBaseDir;
    }

    public void setArtifactBaseDir(String artifactBaseDir) {
        this.artifactBaseDir = artifactBaseDir;
    }

    public int getMaxRecentRuns() {
        return maxRecentRuns;
    }

    public void setMaxRecentRuns(int maxRecentRuns) {
        this.maxRecentRuns = maxRecentRuns;
    }
}
