package com.routechain.data.port;

import com.routechain.data.model.WalletAccountRecord;
import com.routechain.data.model.WalletTransactionRecord;

import java.util.List;
import java.util.Optional;

public interface WalletRepository {
    WalletAccountRecord ensureAccount(String ownerType, String ownerId, String currency);
    Optional<WalletAccountRecord> findAccount(String ownerType, String ownerId);
    List<WalletTransactionRecord> recentTransactions(String ownerType, String ownerId, int limit);
    void appendTransaction(WalletTransactionRecord transactionRecord);
}
