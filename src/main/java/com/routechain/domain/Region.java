package com.routechain.domain;

import com.routechain.domain.Enums.*;

/**
 * Region — a geographic zone in the city (district, CBD, hotspot).
 */
public class Region {
    private final String id;
    private final String name;
    private final GeoPoint center;
    private final double radiusMeters;
    private volatile double currentDemandPressure;
    private volatile double currentDriverSupply;
    private volatile double congestionScore;
    private volatile double rainIntensity;
    private volatile SurgeSeverity surgeSeverity;

    public Region(String id, String name, GeoPoint center, double radiusMeters) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.radiusMeters = radiusMeters;
        this.currentDemandPressure = 0.0;
        this.currentDriverSupply = 0.0;
        this.congestionScore = 0.0;
        this.rainIntensity = 0.0;
        this.surgeSeverity = SurgeSeverity.NORMAL;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public GeoPoint getCenter() { return center; }
    public double getRadiusMeters() { return radiusMeters; }
    public double getCurrentDemandPressure() { return currentDemandPressure; }
    public double getCurrentDriverSupply() { return currentDriverSupply; }
    public double getCongestionScore() { return congestionScore; }
    public double getRainIntensity() { return rainIntensity; }
    public SurgeSeverity getSurgeSeverity() { return surgeSeverity; }

    public void setCurrentDemandPressure(double v) { this.currentDemandPressure = v; }
    public void setCurrentDriverSupply(double v) { this.currentDriverSupply = v; }
    public void setCongestionScore(double v) { this.congestionScore = v; }
    public void setRainIntensity(double v) { this.rainIntensity = v; }
    public void setSurgeSeverity(SurgeSeverity s) { this.surgeSeverity = s; }

    public boolean contains(GeoPoint point) {
        return center.distanceTo(point) <= radiusMeters;
    }

    public double getShortageRatio() {
        if (currentDriverSupply < 0.01) return 1.0;
        return Math.max(0, 1.0 - currentDriverSupply / Math.max(currentDemandPressure, 0.01));
    }
}
