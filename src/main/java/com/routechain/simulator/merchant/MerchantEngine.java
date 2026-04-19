package com.routechain.simulator.merchant;

import com.routechain.simulator.calendar.StressModifier;
import com.routechain.simulator.geo.GeoFeature;
import com.routechain.simulator.geo.HcmGeoCatalog;
import com.routechain.simulator.runtime.SimulatorRunConfig;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

@Component
public class MerchantEngine {
    private final HcmGeoCatalog geoCatalog;

    public MerchantEngine(HcmGeoCatalog geoCatalog) {
        this.geoCatalog = geoCatalog;
    }

    public List<SimMerchant> merchants() {
        List<SimMerchant> merchants = new ArrayList<>();
        for (GeoFeature merchant : geoCatalog.merchants()) {
            String zoneClass = String.valueOf(merchant.properties().get("zoneClass"));
            long basePrepSeconds = switch (zoneClass) {
                case "office" -> 720L;
                case "residential" -> 900L;
                default -> 810L;
            };
            merchants.add(new SimMerchant(
                    merchant.featureId(),
                    String.valueOf(merchant.properties().get("name")),
                    merchant.point(),
                    basePrepSeconds,
                    zoneClass));
        }
        return List.copyOf(merchants);
    }

    public MerchantPrepEstimate estimatePrep(SimulatorRunConfig config,
                                             List<StressModifier> stressModifiers,
                                             SimMerchant merchant,
                                             Instant worldTime,
                                             long seed) {
        SplittableRandom random = new SplittableRandom(seed ^ worldTime.getEpochSecond() ^ merchant.merchantId().hashCode());
        long prep = merchant.basePrepSeconds() + random.nextLong(60L, 240L);
        int backlogDepth = random.nextInt(0, 3);
        if (config.merchantBacklogEnabled() && stressModifiers.contains(StressModifier.MERCHANT_BACKLOG)) {
            prep += 420L;
            backlogDepth += 3;
        }
        return new MerchantPrepEstimate(prep, backlogDepth);
    }
}
