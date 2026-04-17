package com.routechain.v2.selector;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class SelectorObjectiveScaler {
    private final int scaleFactor;

    SelectorObjectiveScaler(int scaleFactor) {
        if (scaleFactor <= 0) {
            throw new IllegalArgumentException("scaleFactor must be positive");
        }
        this.scaleFactor = scaleFactor;
    }

    long scale(double selectionScore) {
        BigDecimal scaled = BigDecimal.valueOf(selectionScore)
                .multiply(BigDecimal.valueOf(scaleFactor))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal max = BigDecimal.valueOf(Long.MAX_VALUE);
        BigDecimal min = BigDecimal.valueOf(Long.MIN_VALUE);
        if (scaled.compareTo(max) > 0 || scaled.compareTo(min) < 0) {
            throw new IllegalArgumentException("selectionScore is too large to scale safely");
        }
        return scaled.longValueExact();
    }
}
