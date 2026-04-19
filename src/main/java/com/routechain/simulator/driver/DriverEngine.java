package com.routechain.simulator.driver;

import com.routechain.domain.GeoPoint;
import com.routechain.simulator.geo.CorridorDefinition;
import com.routechain.simulator.geo.HcmGeoCatalog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class DriverEngine {
    private final HcmGeoCatalog geoCatalog;

    public DriverEngine(HcmGeoCatalog geoCatalog) {
        this.geoCatalog = geoCatalog;
    }

    public List<SimDriver> initialDrivers(Instant worldStart) {
        List<SimDriver> drivers = new ArrayList<>();
        int index = 1;
        for (CorridorDefinition corridor : geoCatalog.corridors()) {
            GeoPoint start = corridor.path().get(0);
            GeoPoint end = corridor.path().get(corridor.path().size() - 1);
            drivers.add(new SimDriver("driver-%02d".formatted(index++), start, SimDriverStatus.IDLE, worldStart, List.of()));
            drivers.add(new SimDriver("driver-%02d".formatted(index++), end, SimDriverStatus.IDLE, worldStart, List.of()));
        }
        return List.copyOf(drivers);
    }

    public void driftIdleDrivers(List<SimDriver> drivers, long tickIndex, boolean enabled) {
        if (!enabled) {
            return;
        }
        for (int index = 0; index < drivers.size(); index++) {
            SimDriver driver = drivers.get(index);
            if (driver.status() != SimDriverStatus.IDLE) {
                continue;
            }
            double latShift = ((tickIndex + index) % 3L - 1L) * 0.0002;
            double lngShift = ((tickIndex + index * 2L) % 3L - 1L) * 0.0002;
            driver.currentLocation(new GeoPoint(
                    driver.currentLocation().latitude() + latShift,
                    driver.currentLocation().longitude() + lngShift));
        }
    }
}
