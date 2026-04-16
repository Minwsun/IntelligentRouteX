package com.routechain.v2;

import com.routechain.config.RouteChainDispatchV2Properties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RouteChainDispatchV2PropertiesTest {

    @Test
    void exposesBootstrapDefaults() {
        RouteChainDispatchV2Properties properties = RouteChainDispatchV2Properties.defaults();
        assertEquals(Duration.ofSeconds(30), properties.getTick());
        assertEquals(Duration.ofSeconds(45), properties.getBuffer().getHoldWindow());
        assertEquals(24, properties.getCluster().getMaxSize());
        assertEquals(5, properties.getBundle().getMaxSize());
        assertEquals(12, properties.getBundle().getTopNeighbors());
        assertEquals(16, properties.getBundle().getBeamWidth());
        assertEquals(3, properties.getCandidate().getMaxAnchors());
        assertEquals(8, properties.getCandidate().getMaxDrivers());
        assertEquals(4, properties.getCandidate().getMaxRouteAlternatives());
        assertEquals(22.0, properties.getContext().getBaselineSpeedKph());
        assertEquals(1.28, properties.getContext().getHeavyRainMultiplier());
        assertEquals(1.07, properties.getContext().getLightRainMultiplier());
        assertEquals(8, properties.getContext().getTomtomRefineBudgetPerTick());
        assertEquals(Duration.ofMinutes(15), properties.getContext().getFreshness().getWeatherMaxAge());
        assertEquals(Duration.ofMinutes(10), properties.getContext().getFreshness().getTrafficMaxAge());
        assertEquals(Duration.ofMinutes(30), properties.getContext().getFreshness().getForecastMaxAge());
        assertEquals(Duration.ofMillis(150), properties.getContext().getTimeouts().getEtaMlTimeout());
        assertFalse(properties.isEnabled());
        assertFalse(properties.isMlEnabled());
        assertFalse(properties.isSidecarRequired());
        assertFalse(properties.isSelectorOrtoolsEnabled());
        assertTrue(properties.isWarmStartEnabled());
        assertTrue(properties.isHotStartEnabled());
        assertFalse(properties.isTomtomEnabled());
        assertTrue(properties.isOpenMeteoEnabled());
    }
}
