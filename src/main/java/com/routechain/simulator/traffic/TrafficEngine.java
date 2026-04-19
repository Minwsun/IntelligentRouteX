package com.routechain.simulator.traffic;

import com.routechain.simulator.calendar.StressModifier;
import com.routechain.simulator.calendar.TimeBucket;
import com.routechain.simulator.calendar.TrafficMode;
import com.routechain.simulator.geo.CorridorDefinition;
import com.routechain.simulator.geo.HcmGeoCatalog;
import com.routechain.simulator.runtime.SimulatorRunConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class TrafficEngine {
    private final HcmGeoCatalog geoCatalog;

    public TrafficEngine(HcmGeoCatalog geoCatalog) {
        this.geoCatalog = geoCatalog;
    }

    public TrafficSnapshot snapshot(SimulatorRunConfig config,
                                    TimeBucket timeBucket,
                                    List<StressModifier> stressModifiers,
                                    double weatherPenaltyMultiplier,
                                    Instant observedAt) {
        double baseMultiplier = switch (timeBucket) {
            case LUNCH -> 0.86;
            case DINNER -> 0.78;
            case LATE_NIGHT -> 1.05;
        };
        if (!config.trafficEnabled()) {
            baseMultiplier = 1.0;
        }
        if (config.trafficMode() == TrafficMode.RUSH) {
            baseMultiplier *= 0.82;
        } else if (config.trafficMode() == TrafficMode.SHOCK || stressModifiers.contains(StressModifier.TRAFFIC_SHOCK)) {
            baseMultiplier *= 0.7;
        }
        baseMultiplier /= weatherPenaltyMultiplier;
        Map<String, Double> corridors = new LinkedHashMap<>();
        for (CorridorDefinition corridor : geoCatalog.corridors()) {
            double corridorMultiplier = baseMultiplier;
            if ("arterial".equals(corridor.className())) {
                corridorMultiplier *= 0.94;
            }
            corridors.put(corridor.corridorId(), corridorMultiplier);
        }
        double congestion = Math.max(0.0, Math.min(1.0, 1.0 - baseMultiplier));
        return new TrafficSnapshot(observedAt, baseMultiplier, congestion, config.trafficMode().wireName(), Map.copyOf(corridors));
    }
}
