package com.routechain.api.dto;

import com.routechain.backend.offer.OfferBrokerService;

import java.util.List;

public record DriverRealtimeSnapshot(
        String driverId,
        List<OfferBrokerService.OfferView> offers,
        DriverActiveTaskView activeTask,
        LiveMapSnapshot mapSnapshot
) {}
