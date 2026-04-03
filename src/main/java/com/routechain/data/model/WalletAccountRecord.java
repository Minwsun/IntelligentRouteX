package com.routechain.data.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wallet balance aggregate for a user or driver.
 */
public record WalletAccountRecord(
        String walletAccountId,
        String ownerType,
        String ownerId,
        String currency,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        Instant createdAt,
        Instant updatedAt
) {
    public WalletAccountRecord {
        walletAccountId = walletAccountId == null || walletAccountId.isBlank()
                ? "wallet-unknown"
                : walletAccountId;
        ownerType = ownerType == null || ownerType.isBlank() ? "USER" : ownerType;
        ownerId = ownerId == null ? "" : ownerId;
        currency = currency == null || currency.isBlank() ? "VND" : currency;
        availableBalance = availableBalance == null ? BigDecimal.ZERO : availableBalance;
        pendingBalance = pendingBalance == null ? BigDecimal.ZERO : pendingBalance;
        createdAt = createdAt == null ? Instant.now() : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }
}
