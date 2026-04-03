package com.routechain.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletTransactionView(
        String transactionId,
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
) {}
