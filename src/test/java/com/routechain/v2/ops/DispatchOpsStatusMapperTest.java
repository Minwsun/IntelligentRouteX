package com.routechain.v2.ops;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatchOpsStatusMapperTest {

    @Test
    void tomTomStatusesStayBounded() {
        assertEquals("disabled", DispatchOpsStatusMapper.tomTomStatus(false, false, List.of()));
        assertEquals("missing-api-key", DispatchOpsStatusMapper.tomTomStatus(true, false, List.of()));
        assertEquals("ok", DispatchOpsStatusMapper.tomTomStatus(true, true, List.of()));
        assertEquals("auth-or-quota-risk", DispatchOpsStatusMapper.tomTomStatus(true, true, List.of("tomtom-timeout")));
    }

    @Test
    void openMeteoStatusesStayBounded() {
        assertEquals("disabled", DispatchOpsStatusMapper.openMeteoStatus(false, List.of()));
        assertEquals("ok", DispatchOpsStatusMapper.openMeteoStatus(true, List.of()));
        assertEquals("fallback-only", DispatchOpsStatusMapper.openMeteoStatus(true, List.of("open-meteo-stale")));
    }
}
