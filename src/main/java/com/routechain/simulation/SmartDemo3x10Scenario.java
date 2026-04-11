package com.routechain.simulation;

import com.routechain.domain.Enums.WeatherProfile;
import com.routechain.domain.GeoPoint;

import java.util.List;

/**
 * Canonical seeded JavaFX smart demo scenario shared by the desktop preset and
 * validation lanes.
 */
public final class SmartDemo3x10Scenario {
    private static final JavaFxDemoScenarioSpec SPEC = new JavaFxDemoScenarioSpec(
            "javafx-smart-demo-3x10",
            42L,
            18,
            15,
            900,
            0,
            0.0,
            0.45,
            WeatherProfile.CLEAR,
            List.of(
                    new JavaFxDemoScenarioSpec.ManualDriverSpawn("compact-cluster-driver", new GeoPoint(10.77685, 106.69982)),
                    new JavaFxDemoScenarioSpec.ManualDriverSpawn("corridor-midpoint-driver", new GeoPoint(10.78192, 106.68854)),
                    new JavaFxDemoScenarioSpec.ManualDriverSpawn("future-landing-driver", new GeoPoint(10.76894, 106.71382))
            ),
            List.of(
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("compact-batch-1",
                            new GeoPoint(10.77610, 106.69935), new GeoPoint(10.78120, 106.68890), 32000, 24),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("compact-batch-2",
                            new GeoPoint(10.77642, 106.70012), new GeoPoint(10.78205, 106.68970), 30000, 24),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("compact-batch-3",
                            new GeoPoint(10.77574, 106.69864), new GeoPoint(10.78042, 106.68780), 31000, 23),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("compact-batch-4",
                            new GeoPoint(10.77730, 106.70110), new GeoPoint(10.78310, 106.69120), 34000, 26),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("tempting-bad-batch-1",
                            new GeoPoint(10.77390, 106.70460), new GeoPoint(10.75480, 106.72690), 29000, 28),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("tempting-bad-batch-2",
                            new GeoPoint(10.77425, 106.70518), new GeoPoint(10.75390, 106.72785), 29500, 29),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("tempting-bad-batch-3",
                            new GeoPoint(10.77280, 106.70355), new GeoPoint(10.75560, 106.72495), 28500, 28),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("future-landing-1",
                            new GeoPoint(10.76955, 106.71320), new GeoPoint(10.78490, 106.71040), 33500, 25),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("future-landing-2",
                            new GeoPoint(10.76820, 106.71255), new GeoPoint(10.78610, 106.70910), 34500, 25),
                    new JavaFxDemoScenarioSpec.ManualOrderSpawn("future-landing-3",
                            new GeoPoint(10.77005, 106.71425), new GeoPoint(10.78535, 106.71155), 33800, 25)
            )
    );

    private SmartDemo3x10Scenario() { }

    public static JavaFxDemoScenarioSpec spec() {
        return SPEC;
    }
}
