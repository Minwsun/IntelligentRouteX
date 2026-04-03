package com.routechain.api.controller;

import com.routechain.api.dto.*;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.data.service.WalletQueryService;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/driver")
public class DriverController {
    private final DriverOperationsService driverOperationsService;
    private final WalletQueryService walletQueryService;

    public DriverController(DriverOperationsService driverOperationsService,
                            WalletQueryService walletQueryService) {
        this.driverOperationsService = driverOperationsService;
        this.walletQueryService = walletQueryService;
    }

    @PostMapping("/session/login")
    public DriverSessionState login(@Valid @RequestBody DriverLoginRequest request) {
        return driverOperationsService.login(request);
    }

    @PostMapping("/session/heartbeat")
    public ResponseEntity<DriverSessionState> heartbeat(@Valid @RequestBody DriverHeartbeatRequest request) {
        return driverOperationsService.heartbeat(request.driverId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/availability")
    public ResponseEntity<DriverSessionState> availability(@RequestParam String driverId,
                                                           @Valid @RequestBody DriverAvailabilityUpdate request) {
        return driverOperationsService.setAvailability(driverId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/location")
    public ResponseEntity<DriverSessionState> location(@RequestParam String driverId,
                                                       @Valid @RequestBody DriverLocationUpdate request) {
        return driverOperationsService.updateLocation(driverId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/offers")
    public List<OfferBrokerService.OfferView> offers(@RequestParam String driverId) {
        return driverOperationsService.offers(driverId);
    }

    @PostMapping("/offers/{offerId}/accept")
    public OfferDecision accept(@RequestParam String driverId,
                                @PathVariable String offerId,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return driverOperationsService.accept(driverId, offerId, idempotencyKey);
    }

    @PostMapping("/offers/{offerId}/decline")
    public OfferDecision decline(@RequestParam String driverId,
                                 @PathVariable String offerId,
                                 @RequestBody(required = false) DriverOfferDecisionRequest request) {
        return driverOperationsService.decline(driverId, offerId, request == null ? null : request.reason());
    }

    @PostMapping("/tasks/{taskId}/status")
    public ResponseEntity<Order> updateTaskStatus(@PathVariable String taskId,
                                                  @Valid @RequestBody DriverTaskStatusUpdate request) {
        return driverOperationsService.updateTaskStatus(taskId, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/copilot")
    public List<?> copilot(@RequestParam String driverId) {
        return driverOperationsService.copilot(driverId);
    }

    @GetMapping("/wallet")
    public WalletBalanceView wallet(@RequestParam String driverId) {
        return walletQueryService.driverWallet(driverId);
    }
}
