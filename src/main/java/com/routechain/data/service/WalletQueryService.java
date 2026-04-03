package com.routechain.data.service;

import com.routechain.api.dto.WalletBalanceView;
import com.routechain.api.dto.WalletTransactionView;
import com.routechain.data.model.WalletAccountRecord;
import com.routechain.data.model.WalletTransactionRecord;
import com.routechain.data.port.WalletRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Read-side wallet queries for user and driver apps.
 */
@Service
public class WalletQueryService {
    private final WalletRepository walletRepository;

    public WalletQueryService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    public WalletBalanceView userWallet(String customerId) {
        return toView(walletRepository.ensureAccount("USER", customerId, "VND"));
    }

    public WalletBalanceView driverWallet(String driverId) {
        return toView(walletRepository.ensureAccount("DRIVER", driverId, "VND"));
    }

    public List<WalletTransactionView> userTransactions(String customerId, int limit) {
        return walletRepository.recentTransactions("USER", customerId, limit).stream()
                .map(this::toView)
                .toList();
    }

    private WalletBalanceView toView(WalletAccountRecord account) {
        return new WalletBalanceView(
                account.walletAccountId(),
                account.ownerType(),
                account.ownerId(),
                account.currency(),
                account.availableBalance(),
                account.pendingBalance(),
                account.updatedAt()
        );
    }

    private WalletTransactionView toView(WalletTransactionRecord tx) {
        return new WalletTransactionView(
                tx.transactionId(),
                tx.ownerType(),
                tx.ownerId(),
                tx.direction(),
                tx.amount(),
                tx.balanceAfter(),
                tx.status(),
                tx.referenceType(),
                tx.referenceId(),
                tx.description(),
                tx.createdAt()
        );
    }
}
