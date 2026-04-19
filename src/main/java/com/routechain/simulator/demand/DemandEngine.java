package com.routechain.simulator.demand;

import com.routechain.domain.GeoPoint;
import com.routechain.simulator.calendar.DayType;
import com.routechain.simulator.calendar.StressModifier;
import com.routechain.simulator.calendar.TimeBucket;
import com.routechain.simulator.merchant.MerchantEngine;
import com.routechain.simulator.merchant.MerchantPrepEstimate;
import com.routechain.simulator.merchant.SimMerchant;
import com.routechain.simulator.runtime.SimulatorRunConfig;
import com.routechain.simulator.weather.WeatherSnapshot;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

@Component
public class DemandEngine {
    private final MerchantEngine merchantEngine;

    public DemandEngine(MerchantEngine merchantEngine) {
        this.merchantEngine = merchantEngine;
    }

    public List<SimOrder> generateOrders(SimulatorRunConfig config,
                                         TimeBucket timeBucket,
                                         DayType dayType,
                                         List<StressModifier> stressModifiers,
                                         WeatherSnapshot weatherSnapshot,
                                         Instant worldTime,
                                         long tickSeed,
                                         int tickIndex) {
        int baseCount = switch (timeBucket) {
            case LUNCH -> 3;
            case DINNER -> 2;
            case LATE_NIGHT -> 1;
        };
        if (dayType == DayType.WEEKEND && timeBucket != TimeBucket.LATE_NIGHT) {
            baseCount += 1;
        }
        if (stressModifiers.contains(StressModifier.LIGHT_SUPPLY_SHORTAGE)) {
            baseCount += 1;
        }
        baseCount = (int) Math.max(0, Math.round(baseCount * weatherSnapshot.demandMultiplier()));
        if ((tickIndex % 2) == 1) {
            baseCount = Math.max(0, baseCount - 1);
        }
        List<SimMerchant> merchants = merchantEngine.merchants();
        SplittableRandom random = new SplittableRandom(tickSeed ^ worldTime.getEpochSecond());
        List<SimOrder> orders = new ArrayList<>();
        for (int index = 0; index < baseCount; index++) {
            SimMerchant merchant = merchants.get(random.nextInt(merchants.size()));
            MerchantPrepEstimate prep = merchantEngine.estimatePrep(config, stressModifiers, merchant, worldTime, random.nextLong());
            Instant readyAt = worldTime.plusSeconds(prep.prepSeconds());
            GeoPoint dropoff = new GeoPoint(
                    merchant.location().latitude() + random.nextDouble(-0.025, 0.025),
                    merchant.location().longitude() + random.nextDouble(-0.025, 0.025));
            String orderId = "order-%d-%02d".formatted(tickIndex, index);
            orders.add(new SimOrder(
                    orderId,
                    merchant.merchantId(),
                    merchant.location(),
                    dropoff,
                    worldTime,
                    readyAt,
                    30 + prep.backlogDepth() * 5,
                    SimOrderStatus.OPEN));
        }
        return List.copyOf(orders);
    }
}
