package com.routechain.v2.selector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SelectorObjectiveScalerTest {

    @Test
    void scalesScoresDeterministicallyAndRoundsHalfUp() {
        SelectorObjectiveScaler scaler = new SelectorObjectiveScaler(1_000);

        assertEquals(1_235L, scaler.scale(1.2346));
        assertEquals(1_235L, scaler.scale(1.2346));
        assertEquals(-1_235L, scaler.scale(-1.2346));
    }

    @Test
    void rejectsOverflowingScaledValues() {
        SelectorObjectiveScaler scaler = new SelectorObjectiveScaler(1_000);

        assertThrows(IllegalArgumentException.class, () -> scaler.scale(Double.MAX_VALUE));
    }
}
