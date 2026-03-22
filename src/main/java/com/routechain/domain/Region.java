package com.routechain.domain;

import com.routechain.domain.Enums.*;

/**
 * Region — a geographic zone in the city (district, CBD, hotspot).
 * Enhanced with demand forecast, opportunity scoring, and zone classification.
 */
public class Region {
    private final String id;
    private final String name;
    private final GeoPoint center;
    private final double radiusMeters;

    // ── Real-time state ─────────────────────────────────────────────────
    private volatile double currentDemandPressure;
    private volatile double currentDriverSupply;
    private volatile double congestionScore;
    private volatile double rainIntensity;
    private volatile SurgeSeverity surgeSeverity;

    // ── Demand forecast ─────────────────────────────────────────────────
    private volatile double predictedDemand5m;
    private volatile double predictedDemand10m;
    private volatile double predictedDemand15m;
    private volatile double spikeProbability;

    // ── Zone characteristics ────────────────────────────────────────────
    private volatile double driverDensity;          // drivers per km²
    private volatile double historicalOrderDensity;  // avg orders/hour historically
    private volatile double hotspotScore;            // apartment/office/food cluster weight
    private volatile double exitTrafficPenalty;       // cost to leave this zone
    private volatile double opportunityScore;        // computed by ZoneOpportunityScorer

    // ── Tracking ────────────────────────────────────────────────────────
    private volatile int recentOrderInflow1m;
    private volatile int recentOrderInflow5m;
    private volatile double averageWaitTime;
    private volatile double averageCancellationRisk;

    // ── Zone type ───────────────────────────────────────────────────────
    public enum ZoneType { CBD, RESIDENTIAL, INDUSTRIAL, CAMPUS, MIXED }
    private final ZoneType zoneType;

    public Region(String id, String name, GeoPoint center, double radiusMeters) {
        this(id, name, center, radiusMeters, ZoneType.MIXED);
    }

    public Region(String id, String name, GeoPoint center, double radiusMeters, ZoneType zoneType) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.radiusMeters = radiusMeters;
        this.zoneType = zoneType;
        this.currentDemandPressure = 0.0;
        this.currentDriverSupply = 0.0;
        this.congestionScore = 0.0;
        this.rainIntensity = 0.0;
        this.surgeSeverity = SurgeSeverity.NORMAL;
        this.hotspotScore = 0.5;
        this.historicalOrderDensity = 1.0;
    }

    // ── Core getters ────────────────────────────────────────────────────
    public String getId() { return id; }
    public String getName() { return name; }
    public GeoPoint getCenter() { return center; }
    public double getRadiusMeters() { return radiusMeters; }
    public ZoneType getZoneType() { return zoneType; }

    // ── State getters ───────────────────────────────────────────────────
    public double getCurrentDemandPressure() { return currentDemandPressure; }
    public double getCurrentDriverSupply() { return currentDriverSupply; }
    public double getCongestionScore() { return congestionScore; }
    public double getRainIntensity() { return rainIntensity; }
    public SurgeSeverity getSurgeSeverity() { return surgeSeverity; }

    // ── Forecast getters ────────────────────────────────────────────────
    public double getPredictedDemand5m() { return predictedDemand5m; }
    public double getPredictedDemand10m() { return predictedDemand10m; }
    public double getPredictedDemand15m() { return predictedDemand15m; }
    public double getSpikeProbability() { return spikeProbability; }
    public double getDriverDensity() { return driverDensity; }
    public double getHistoricalOrderDensity() { return historicalOrderDensity; }
    public double getHotspotScore() { return hotspotScore; }
    public double getExitTrafficPenalty() { return exitTrafficPenalty; }
    public double getOpportunityScore() { return opportunityScore; }
    public int getRecentOrderInflow1m() { return recentOrderInflow1m; }
    public int getRecentOrderInflow5m() { return recentOrderInflow5m; }
    public double getAverageWaitTime() { return averageWaitTime; }
    public double getAverageCancellationRisk() { return averageCancellationRisk; }

    // ── State setters ───────────────────────────────────────────────────
    public void setCurrentDemandPressure(double v) { this.currentDemandPressure = v; }
    public void setCurrentDriverSupply(double v) { this.currentDriverSupply = v; }
    public void setCongestionScore(double v) { this.congestionScore = v; }
    public void setRainIntensity(double v) { this.rainIntensity = v; }
    public void setSurgeSeverity(SurgeSeverity s) { this.surgeSeverity = s; }

    // ── Forecast setters ────────────────────────────────────────────────
    public void setPredictedDemand5m(double v) { this.predictedDemand5m = v; }
    public void setPredictedDemand10m(double v) { this.predictedDemand10m = v; }
    public void setPredictedDemand15m(double v) { this.predictedDemand15m = v; }
    public void setSpikeProbability(double v) { this.spikeProbability = v; }
    public void setDriverDensity(double v) { this.driverDensity = v; }
    public void setHistoricalOrderDensity(double v) { this.historicalOrderDensity = v; }
    public void setHotspotScore(double v) { this.hotspotScore = v; }
    public void setExitTrafficPenalty(double v) { this.exitTrafficPenalty = v; }
    public void setOpportunityScore(double v) { this.opportunityScore = v; }
    public void setRecentOrderInflow1m(int v) { this.recentOrderInflow1m = v; }
    public void setRecentOrderInflow5m(int v) { this.recentOrderInflow5m = v; }
    public void setAverageWaitTime(double v) { this.averageWaitTime = v; }
    public void setAverageCancellationRisk(double v) { this.averageCancellationRisk = v; }

    // ── Computed ────────────────────────────────────────────────────────
    public boolean contains(GeoPoint point) {
        return center.distanceTo(point) <= radiusMeters;
    }

    public double getShortageRatio() {
        if (currentDriverSupply < 0.01) return 1.0;
        return Math.max(0, 1.0 - currentDriverSupply / Math.max(currentDemandPressure, 0.01));
    }

    public double getDriverOversupply() {
        if (currentDemandPressure < 0.01) return currentDriverSupply;
        return Math.max(0, currentDriverSupply - currentDemandPressure) / Math.max(currentDemandPressure, 0.01);
    }
}
