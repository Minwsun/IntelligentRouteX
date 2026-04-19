package com.routechain.simulator.merchant;

import com.routechain.domain.GeoPoint;

public record SimMerchant(
        String merchantId,
        String name,
        GeoPoint location,
        long basePrepSeconds,
        String zoneClass) {
}
