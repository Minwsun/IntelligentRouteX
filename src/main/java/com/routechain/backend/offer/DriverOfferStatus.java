package com.routechain.backend.offer;

/**
 * Driver-facing offer lifecycle state.
 */
public enum DriverOfferStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    LOST
}
