package com.routechain.data.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wallet transaction journal row.
 */
public record WalletTransactionRecord(
        String transactionId,
        String walletAccountId,
        String ownerType,
        String ownerId,
        String direction,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String status,
        String referenceType,
        String referenceId,
        String description,
        Instant createdAt
) {
    public WalletTransactionRecord {
        transactionId = transactionId == null || transactionId.isBlank() ? "wallet-tx-unknown" : transactionId;
        walletAccountId = walletAccountId == null || walletAccountId.isBlank() ? "wallet-unknown" : walletAccountId;
        ownerType = ownerType == null || ownerType.isBlank() ? "USER" : ownerType;
        ownerId = ownerId == null ? "" : ownerId;
        direction = direction == null || direction.isBlank() ? "CREDIT" : direction;
        amount = amount == null ? BigDecimal.ZERO : amount;
        balanceAfter = balanceAfter == null ? BigDecimal.ZERO : balanceAfter;
        status = status == null || status.isBlank() ? "POSTED" : status;
        referenceType = referenceType == null ? "" : referenceType;
        referenceId = referenceId == null ? "" : referenceId;
        description = description == null ? "" : description;
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }
}
