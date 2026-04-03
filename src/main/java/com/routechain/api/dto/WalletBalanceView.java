package com.routechain.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record WalletBalanceView(
        String walletAccountId,
        String ownerType,
        String ownerId,
        String currency,
        BigDecimal availableBalance,
        BigDecimal pendingBalance,
        Instant updatedAt
) {}
