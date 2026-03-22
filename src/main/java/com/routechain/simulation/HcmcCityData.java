package com.routechain.simulation;

import com.routechain.domain.*;
import java.util.*;

/**
 * Hard-coded HCMC city topology: regions, pickup points, traffic corridors.
 * This replaces PostGIS for the simulation-only MVP.
 */
public final class HcmcCityData {
    private HcmcCityData() {}

    public static final GeoPoint CITY_CENTER = new GeoPoint(10.7769, 106.7009);

    /** All regions (districts) with real HCMC coordinates */
    public static List<Region> createRegions() {
        return List.of(
            new Region("q1",    "Quận 1",       new GeoPoint(10.7769, 106.7009), 1800),
            new Region("q3",    "Quận 3",       new GeoPoint(10.7834, 106.6868), 1500),
            new Region("bt",    "Bình Thạnh",   new GeoPoint(10.8011, 106.7102), 2200),
            new Region("pn",    "Phú Nhuận",    new GeoPoint(10.7975, 106.6791), 1200),
            new Region("tb",    "Tân Bình",     new GeoPoint(10.8011, 106.6527), 2500),
            new Region("td",    "Thủ Đức",      new GeoPoint(10.8487, 106.7718), 3500),
            new Region("q7",    "Quận 7",       new GeoPoint(10.7340, 106.7219), 2800),
            new Region("nb",    "Nhà Bè",       new GeoPoint(10.6940, 106.7040), 3000),
            new Region("q5",    "Quận 5",       new GeoPoint(10.7548, 106.6636), 1400),
            new Region("q10",   "Quận 10",      new GeoPoint(10.7726, 106.6662), 1300),
            new Region("gv",    "Gò Vấp",       new GeoPoint(10.8389, 106.6588), 2200),
            new Region("q4",    "Quận 4",       new GeoPoint(10.7574, 106.7044), 1100)
        );
    }

    /** Pickup points scattered across HCMC for order generation */
    public static List<GeoPoint> createPickupPoints() {
        return List.of(
            // Q1 - CBD
            new GeoPoint(10.7769, 106.7009), new GeoPoint(10.7730, 106.6980),
            new GeoPoint(10.7800, 106.6970), new GeoPoint(10.7755, 106.7055),
            // Q3
            new GeoPoint(10.7834, 106.6868), new GeoPoint(10.7810, 106.6900),
            // Binh Thanh
            new GeoPoint(10.8011, 106.7102), new GeoPoint(10.8050, 106.7150),
            new GeoPoint(10.7980, 106.7130),
            // Phu Nhuan
            new GeoPoint(10.7975, 106.6791), new GeoPoint(10.7950, 106.6810),
            // Tan Binh
            new GeoPoint(10.8011, 106.6527), new GeoPoint(10.8050, 106.6560),
            // Thu Duc
            new GeoPoint(10.8487, 106.7718), new GeoPoint(10.8420, 106.7650),
            // Q7
            new GeoPoint(10.7340, 106.7219), new GeoPoint(10.7380, 106.7180),
            // Q5
            new GeoPoint(10.7548, 106.6636),
            // Q10
            new GeoPoint(10.7726, 106.6662),
            // Go Vap
            new GeoPoint(10.8389, 106.6588)
        );
    }

    /** Traffic corridors */
    public static List<TrafficCorridor> createCorridors() {
        return List.of(
            new TrafficCorridor("c1", "Nguyen Hue - Q1",
                new GeoPoint(10.7740, 106.7005), new GeoPoint(10.7800, 106.6980)),
            new TrafficCorridor("c2", "Nguyen Huu Canh - BT",
                new GeoPoint(10.7900, 106.7100), new GeoPoint(10.8050, 106.7150)),
            new TrafficCorridor("c3", "Cong Hoa - TB",
                new GeoPoint(10.7950, 106.6600), new GeoPoint(10.8100, 106.6500)),
            new TrafficCorridor("c4", "Xa Lo Ha Noi - TD",
                new GeoPoint(10.8100, 106.7200), new GeoPoint(10.8500, 106.7700)),
            new TrafficCorridor("c5", "Nguyen Van Linh - Q7",
                new GeoPoint(10.7400, 106.7000), new GeoPoint(10.7300, 106.7400)),
            new TrafficCorridor("c6", "Vo Van Kiet - Q1/Q5",
                new GeoPoint(10.7600, 106.6800), new GeoPoint(10.7560, 106.7050)),
            new TrafficCorridor("c7", "Dien Bien Phu - BT/Q3",
                new GeoPoint(10.7870, 106.6900), new GeoPoint(10.7950, 106.7080)),
            new TrafficCorridor("c8", "Pham Van Dong - GV/TD",
                new GeoPoint(10.8300, 106.6700), new GeoPoint(10.8500, 106.7200))
        );
    }

    /** Vietnamese driver names */
    public static String[] driverNames() {
        return new String[]{
            "Nguyễn Văn A", "Trần Thị B", "Lê Văn C", "Phạm Minh D",
            "Hoàng Anh E", "Võ Thành F", "Đặng Quốc G", "Bùi Ngọc H",
            "Đỗ Thanh I", "Ngô Văn K", "Dương Thị L", "Lý Hải M",
            "Vũ Minh N", "Phan Thanh O", "Trương Văn P", "Hồ Thị Q",
            "Mai Văn R", "Chu Thế S", "Đinh Công T", "Tạ Đức U",
            "Lương Văn V", "Hà Thị W", "Cao Minh X", "Thái Bảo Y",
            "Kiều Văn Z", "Trịnh Quang AA", "Lưu Đức BB", "Nguyễn Hải CC",
            "Trần Quốc DD", "Lê Thị EE", "Phạm Văn FF", "Hoàng Thế GG",
            "Võ Đình HH", "Đặng Thị II", "Bùi Quang KK", "Đỗ Hữu LL",
            "Ngô Thanh MM", "Dương Văn NN", "Lý Thị OO", "Vũ Xuân PP"
        };
    }

    /** Base demand rates per region (orders per tick at baseline) */
    public static Map<String, Double> baseDemandRates() {
        return Map.ofEntries(
            Map.entry("q1", 0.35),
            Map.entry("q3", 0.25),
            Map.entry("bt", 0.30),
            Map.entry("pn", 0.18),
            Map.entry("tb", 0.22),
            Map.entry("td", 0.20),
            Map.entry("q7", 0.15),
            Map.entry("nb", 0.08),
            Map.entry("q5", 0.15),
            Map.entry("q10", 0.18),
            Map.entry("gv", 0.20),
            Map.entry("q4", 0.12)
        );
    }

    /** Hour-of-day demand multipliers (0-23) simulating lunch/dinner peaks */
    public static double hourlyMultiplier(int hour) {
        return switch (hour) {
            case 6, 7 -> 0.8;
            case 8, 9 -> 1.0;
            case 10 -> 1.2;
            case 11, 12, 13 -> 1.8; // lunch peak
            case 14, 15 -> 1.0;
            case 16 -> 1.1;
            case 17, 18, 19 -> 2.0; // dinner peak
            case 20, 21 -> 1.5;
            case 22, 23 -> 0.8;
            default -> 0.4;
        };
    }

    public record TrafficCorridor(String id, String name, GeoPoint from, GeoPoint to) {}
}
