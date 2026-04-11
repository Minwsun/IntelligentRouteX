package com.routechain.api.controller;

import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.dto.UserOrderRequest;
import com.routechain.api.dto.UserOrderResponse;
import com.routechain.api.dto.UserQuoteRequest;
import com.routechain.api.dto.UserQuoteResponse;
import com.routechain.api.dto.LiveMapSnapshot;
import com.routechain.api.dto.NearbyDriverView;
import com.routechain.api.dto.TripTrackingView;
import com.routechain.api.dto.WalletBalanceView;
import com.routechain.api.dto.WalletTransactionView;
import com.routechain.api.service.RuntimeBridge;
import com.routechain.api.service.UserOrderingService;
import com.routechain.data.service.WalletQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/user")
public class UserOrderController {
    private final UserOrderingService userOrderingService;
    private final RuntimeBridge runtimeBridge;
    private final WalletQueryService walletQueryService;
    private final ActorAccessGuard actorAccessGuard;

    public UserOrderController(UserOrderingService userOrderingService,
                               RuntimeBridge runtimeBridge,
                               WalletQueryService walletQueryService,
                               ActorAccessGuard actorAccessGuard) {
        this.userOrderingService = userOrderingService;
        this.runtimeBridge = runtimeBridge;
        this.walletQueryService = walletQueryService;
        this.actorAccessGuard = actorAccessGuard;
    }

    @PostMapping("/quotes")
    public UserQuoteResponse quote(@Valid @RequestBody UserQuoteRequest request) {
        actorAccessGuard.requireCustomer(request.customerId());
        return userOrderingService.quote(request);
    }

    @PostMapping("/orders")
    public UserOrderResponse createOrder(@Valid @RequestBody UserOrderRequest request,
                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        actorAccessGuard.requireCustomer(request.customerId());
        return userOrderingService.createOrder(request, idempotencyKey);
    }

    @GetMapping("/orders/{orderId}")
    public UserOrderResponse getOrder(@PathVariable String orderId) {
        UserOrderResponse response = userOrderingService.order(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        actorAccessGuard.requireCustomer(response.customerId());
        return response;
    }

    @PostMapping("/orders/{orderId}/cancel")
    public UserOrderResponse cancel(@PathVariable String orderId,
                                    @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestBody(required = false) Map<String, String> body) {
        String reason = body == null ? null : body.getOrDefault("reason", "user_cancelled");
        UserOrderResponse response = userOrderingService.cancel(orderId, reason, idempotencyKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        actorAccessGuard.requireCustomer(response.customerId());
        return response;
    }

    @GetMapping("/orders/{orderId}/tracking")
    public TripTrackingView tracking(@PathVariable String orderId) {
        TripTrackingView response = runtimeBridge.tripTracking(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
        actorAccessGuard.requireCustomer(response.customerId());
        return response;
    }

    @GetMapping("/map-snapshot")
    public LiveMapSnapshot mapSnapshot(@RequestParam String customerId,
                                       @RequestParam(required = false) String orderId) {
        actorAccessGuard.requireCustomer(customerId);
        return runtimeBridge.userMapSnapshot(customerId, orderId);
    }

    @GetMapping("/nearby-drivers")
    public List<NearbyDriverView> nearbyDrivers(@RequestParam double lat,
                                                @RequestParam double lng,
                                                @RequestParam(defaultValue = "6") int limit) {
        return runtimeBridge.nearbyDrivers(lat, lng, limit);
    }

    @GetMapping("/wallet")
    public WalletBalanceView wallet(@RequestParam String customerId) {
        actorAccessGuard.requireCustomer(customerId);
        return walletQueryService.userWallet(customerId);
    }

    @GetMapping("/wallet/transactions")
    public List<WalletTransactionView> walletTransactions(@RequestParam String customerId,
                                                          @RequestParam(defaultValue = "20") int limit) {
        actorAccessGuard.requireCustomer(customerId);
        return walletQueryService.userTransactions(customerId, Math.max(1, Math.min(100, limit)));
    }
}
