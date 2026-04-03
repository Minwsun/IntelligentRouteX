package com.routechain.api.controller;

import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.dto.UserOrderResponse;
import com.routechain.api.dto.UserQuoteRequest;
import com.routechain.api.dto.UserQuoteResponse;
import com.routechain.api.dto.WalletBalanceView;
import com.routechain.api.dto.WalletTransactionView;
import com.routechain.api.service.UserOrderingService;
import com.routechain.data.service.WalletQueryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
public class UserOrderController {
    private final UserOrderingService userOrderingService;
    private final WalletQueryService walletQueryService;

    public UserOrderController(UserOrderingService userOrderingService,
                               WalletQueryService walletQueryService) {
        this.userOrderingService = userOrderingService;
        this.walletQueryService = walletQueryService;
    }

    @PostMapping("/quotes")
    public UserQuoteResponse quote(@Valid @RequestBody UserQuoteRequest request) {
        return userOrderingService.quote(request);
    }

    @PostMapping("/orders")
    public UserOrderResponse createOrder(@Valid @RequestBody UserOrderRequest request,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return userOrderingService.createOrder(request, idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<UserOrderResponse> getOrder(@PathVariable String orderId) {
        return userOrderingService.order(orderId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<UserOrderResponse> cancel(@PathVariable String orderId,
                                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.getOrDefault("reason", "user_cancelled");
        return userOrderingService.cancel(orderId, reason, idempotencyKey)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/orders/{orderId}/tracking")
    public ResponseEntity<UserOrderResponse> tracking(@PathVariable String orderId) {
        return getOrder(orderId);
    }

    @GetMapping("/wallet")
    public WalletBalanceView wallet(@RequestParam String customerId) {
        return walletQueryService.userWallet(customerId);
    }

    @GetMapping("/wallet/transactions")
    public List<WalletTransactionView> walletTransactions(@RequestParam String customerId,
                                                          @RequestParam(defaultValue = "20") int limit) {
        return walletQueryService.userTransactions(customerId, Math.max(1, Math.min(100, limit)));
    }
}
