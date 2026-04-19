package com.routechain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "routechain.dispatch-v2.smoke-runner")
public class DispatchV2SmokeRunnerProperties {
    private boolean enabled;
    private String traceId = "portable-smoke";
    private String outputDir = "data/run/dispatch-smoke";
    private String bundleRoot = ".";
    private boolean expectHotStart;
    private String expectedPreviousTraceId = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getOutputDir() {
        return outputDir;
    }

    public void setOutputDir(String outputDir) {
        this.outputDir = outputDir;
    }

    public String getBundleRoot() {
        return bundleRoot;
    }

    public void setBundleRoot(String bundleRoot) {
        this.bundleRoot = bundleRoot;
    }

    public boolean isExpectHotStart() {
        return expectHotStart;
    }

    public void setExpectHotStart(boolean expectHotStart) {
        this.expectHotStart = expectHotStart;
    }

    public String getExpectedPreviousTraceId() {
        return expectedPreviousTraceId;
    }

    public void setExpectedPreviousTraceId(String expectedPreviousTraceId) {
        this.expectedPreviousTraceId = expectedPreviousTraceId;
    }
}
