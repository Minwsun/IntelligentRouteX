package com.routechain.simulator.merchant;

public record MerchantPrepEstimate(
        long prepSeconds,
        int backlogDepth) {
}
