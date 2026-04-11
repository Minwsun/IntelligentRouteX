package com.routechain.api.controller;

import com.routechain.api.dto.*;
import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.DriverOperationsService;
import com.routechain.data.service.WalletQueryService;
import com.routechain.backend.offer.DriverSessionState;
import com.routechain.backend.offer.OfferBrokerService;
import com.routechain.backend.offer.OfferDecision;
import com.routechain.domain.Order;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/v1/driver")
public class DriverController {
    private final DriverOperationsService driverOperationsService;
    private final WalletQueryService walletQueryService;
    private final ActorAccessGuard actorAccessGuard;

    public DriverController(DriverOperationsService driverOperationsService,
                            WalletQueryService walletQueryService,
                            ActorAccessGuard actorAccessGuard) {
        this.driverOperationsService = driverOperationsService;
        this.walletQueryService = walletQueryService;
        this.actorAccessGuard = actorAccessGuard;
    }

    @PostMapping("/session/login")
    public DriverSessionState login(@Valid @RequestBody DriverLoginRequest request) {
        actorAccessGuard.requireDriver(request.driverId());
        return driverOperationsService.login(request);
    }

    @PostMapping("/session/heartbeat")
    public DriverSessionState heartbeat(@Valid @RequestBody DriverHeartbeatRequest request) {
        actorAccessGuard.requireDriver(request.driverId());
        return driverOperationsService.heartbeat(request.driverId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver session not found"));
    }

    @PatchMapping("/availability")
    public DriverSessionState availability(@RequestParam String driverId,
                                           @Valid @RequestBody DriverAvailabilityUpdate request) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.setAvailability(driverId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver session not found"));
    }

    @PostMapping("/location")
    public DriverSessionState location(@RequestParam String driverId,
                                       @Valid @RequestBody DriverLocationUpdate request) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.updateLocation(driverId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Driver session not found"));
    }

    @GetMapping("/offers")
    public List<OfferBrokerService.OfferView> offers(@RequestParam String driverId) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.offers(driverId);
    }

    @GetMapping("/tasks/active")
    public DriverActiveTaskView activeTask(@RequestParam String driverId) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.activeTask(driverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active task not found"));
    }

    @GetMapping("/map-snapshot")
    public LiveMapSnapshot mapSnapshot(@RequestParam String driverId) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.liveMap(driverId);
    }

    @PostMapping("/offers/{offerId}/accept")
    public OfferDecision accept(@RequestParam String driverId,
                                @PathVariable String offerId,
                                @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.accept(driverId, offerId, idempotencyKey);
    }

    @PostMapping("/offers/{offerId}/decline")
    public OfferDecision decline(@RequestParam String driverId,
                                 @PathVariable String offerId,
                                 @RequestBody(required = false) DriverOfferDecisionRequest request) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.decline(driverId, offerId, request == null ? null : request.reason());
    }

    @PostMapping("/tasks/{taskId}/status")
    public Order updateTaskStatus(@PathVariable String taskId,
                                  @RequestParam(required = false) String driverId,
                                  @Valid @RequestBody DriverTaskStatusUpdate request) {
        String resolvedDriverId = (driverId != null && !driverId.isBlank())
                ? driverId
                : actorAccessGuard.currentSubject();
        actorAccessGuard.requireDriver(resolvedDriverId);
        return driverOperationsService.updateTaskStatus(resolvedDriverId, taskId, request)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    @GetMapping("/copilot")
    public List<?> copilot(@RequestParam String driverId) {
        actorAccessGuard.requireDriver(driverId);
        return driverOperationsService.copilot(driverId);
    }

    @GetMapping("/wallet")
    public WalletBalanceView wallet(@RequestParam String driverId) {
        actorAccessGuard.requireDriver(driverId);
        return walletQueryService.driverWallet(driverId);
    }
}
