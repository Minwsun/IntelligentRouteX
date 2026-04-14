package com.routechain.api.controller;

import com.routechain.api.dto.MerchantOrderView;
import com.routechain.api.security.ActorAccessGuard;
import com.routechain.api.service.RuntimeBridge;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/merchant")
public class MerchantController {
    private final RuntimeBridge runtimeBridge;
    private final ActorAccessGuard actorAccessGuard;

    public MerchantController(RuntimeBridge runtimeBridge,
                              ActorAccessGuard actorAccessGuard) {
        this.runtimeBridge = runtimeBridge;
        this.actorAccessGuard = actorAccessGuard;
    }

    @GetMapping("/orders")
    public List<MerchantOrderView> orders(@RequestParam String merchantId) {
        actorAccessGuard.requireMerchant(merchantId);
        return runtimeBridge.merchantOrders(merchantId);
    }
}
