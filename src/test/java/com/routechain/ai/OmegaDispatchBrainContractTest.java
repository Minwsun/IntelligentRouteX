package com.routechain.ai;

import com.routechain.simulation.HcmcCityData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OmegaDispatchBrainContractTest {

    @Test
    void omegaShouldExposeDispatchBrainTools() {
        OmegaDispatchAgent agent = new OmegaDispatchAgent(HcmcCityData.createRegions());

        assertEquals("DispatchBrainAgent", agent.agentId());
        assertEquals(10, agent.describeTools().size());
    }
}
