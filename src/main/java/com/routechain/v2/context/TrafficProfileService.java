package com.routechain.v2.context;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TrafficProfileService {
    private static final ZoneId ASIA_SAIGON = ZoneId.of("Asia/Saigon");
    private final Map<String, Double> cachedMultipliers = new ConcurrentHashMap<>();

    public double multiplier(String corridorId, Instant departureTime, double trafficIntensity) {
        Instant safeDeparture = departureTime == null ? Instant.EPOCH : departureTime;
        var zonedTime = safeDeparture.atZone(ASIA_SAIGON);
        boolean weekend = zonedTime.getDayOfWeek() == DayOfWeek.SATURDAY
                || zonedTime.getDayOfWeek() == DayOfWeek.SUNDAY;
        int slot = zonedTime.getHour() * 2 + (zonedTime.getMinute() / 30);
        String key = (corridorId == null ? "corridor-unknown" : corridorId)
                + ":" + (weekend ? "weekend" : "weekday")
                + ":" + slot;
        return cachedMultipliers.computeIfAbsent(key, ignored -> computeMultiplier(corridorId, weekend, slot, trafficIntensity));
    }

    public TrafficState classify(double trafficMultiplier, double congestionScore) {
        double effective = Math.max(trafficMultiplier, 1.0 + Math.max(0.0, congestionScore));
        if (effective >= 1.55) {
            return TrafficState.RED;
        }
        if (effective >= 1.35) {
            return TrafficState.HEAVY;
        }
        if (effective >= 1.15) {
            return TrafficState.MODERATE;
        }
        if (effective >= 1.00) {
            return TrafficState.LIGHT;
        }
        return TrafficState.FREEFLOW;
    }

    private double computeMultiplier(String corridorId, boolean weekend, int slot, double trafficIntensity) {
        int hour = slot / 2;
        double baseSlot = switch (hour) {
            case 0, 1, 2, 3, 4 -> 0.82;
            case 5, 6 -> 0.95;
            case 7, 8 -> 1.25;
            case 9, 10 -> 1.10;
            case 11, 12, 13 -> 1.12;
            case 14, 15 -> 1.02;
            case 16, 17, 18 -> 1.35;
            case 19, 20 -> 1.12;
            default -> 0.90;
        };
        if (weekend) {
            baseSlot = baseSlot * 0.92;
        }
        double corridorBias = corridorId == null ? 0.0 : ((Math.abs(corridorId.toLowerCase(Locale.ROOT).hashCode()) % 13) - 6) / 100.0;
        double intensityFactor = 0.85 + Math.max(0.0, trafficIntensity) * 0.55;
        return clamp(baseSlot * intensityFactor + corridorBias, 0.75, 2.20);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
