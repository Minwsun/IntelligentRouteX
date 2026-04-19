package com.routechain.simulator.geo;

import com.routechain.domain.GeoPoint;
import com.routechain.domain.Region;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class HcmGeoCatalog {
    private final List<Region> regions = List.of(
            new Region("d1", "District 1"),
            new Region("d3", "District 3"),
            new Region("binh-thanh", "Binh Thanh"),
            new Region("phu-nhuan", "Phu Nhuan"),
            new Region("d7", "District 7"));

    private final List<GeoFeature> merchants = List.of(
            merchant("m-ben-nghe", "Ben Nghe Lunch Cluster", 10.7766, 106.7009, "office", "high"),
            merchant("m-vo-thi-sau", "Vo Thi Sau Mixed Cluster", 10.7860, 106.6918, "mixed-use", "medium"),
            merchant("m-nguyen-huu-canh", "Nguyen Huu Canh Residential", 10.7942, 106.7218, "residential", "medium"),
            merchant("m-tan-dinh", "Tan Dinh Mixed Cluster", 10.7896, 106.6911, "mixed-use", "medium"),
            merchant("m-phu-my-hung", "Phu My Hung Dinner Cluster", 10.7297, 106.7213, "residential", "high"));

    private final List<GeoFeature> hotspots = List.of(
            hotspot("h-office-core", 10.7772, 106.7015, "office"),
            hotspot("h-canal-corridor", 10.7948, 106.7142, "mixed-use"),
            hotspot("h-d7-evening", 10.7288, 106.7224, "residential"));

    private final List<CorridorDefinition> corridors = List.of(
            corridor("c-d1-d3", "arterial", 18.0, new GeoPoint(10.7768, 106.7000), new GeoPoint(10.7870, 106.6890)),
            corridor("c-binh-thanh-core", "arterial", 16.5, new GeoPoint(10.7940, 106.7210), new GeoPoint(10.8040, 106.7160)),
            corridor("c-d7-core", "connector", 19.5, new GeoPoint(10.7297, 106.7213), new GeoPoint(10.7410, 106.7090)));

    public List<Region> regions() {
        return regions;
    }

    public List<GeoFeature> merchants() {
        return merchants;
    }

    public List<GeoFeature> hotspots() {
        return hotspots;
    }

    public List<CorridorDefinition> corridors() {
        return corridors;
    }

    private static GeoFeature merchant(String id, String name, double latitude, double longitude, String zoneClass, String intensity) {
        return new GeoFeature(id, "merchant", new GeoPoint(latitude, longitude), Map.of(
                "name", name,
                "zoneClass", zoneClass,
                "intensity", intensity));
    }

    private static GeoFeature hotspot(String id, double latitude, double longitude, String zoneClass) {
        return new GeoFeature(id, "hotspot", new GeoPoint(latitude, longitude), Map.of("zoneClass", zoneClass));
    }

    private static CorridorDefinition corridor(String id, String className, double speedKph, GeoPoint start, GeoPoint end) {
        return new CorridorDefinition(id, className, List.of(start, end), speedKph, Map.of("className", className));
    }
}
