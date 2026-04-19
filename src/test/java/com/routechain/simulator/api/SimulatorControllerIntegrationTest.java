package com.routechain.simulator.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import com.routechain.api.RouteChainApiApplication;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = RouteChainApiApplication.class)
@AutoConfigureMockMvc
class SimulatorControllerIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void simulatorCatalogAndRunEndpointsAreReachable() throws Exception {
        mockMvc.perform(get("/api/simulator/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioCatalogId").value("hcm-calendar-slice-v1"))
                .andExpect(jsonPath("$.canonicalSlices.length()").value(60));

        mockMvc.perform(post("/api/simulator/runs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "scenarioCatalogId": "hcm-calendar-slice-v1",
                                  "runMode": "SINGLE_SLICE",
                                  "monthRegime": "MAY",
                                  "dayType": "WEEKDAY",
                                  "timeBucket": "LUNCH",
                                  "stressModifiers": [],
                                  "weatherMode": "AUTO",
                                  "trafficMode": "AUTO",
                                  "seed": 99,
                                  "tickRate": "PT30S",
                                  "parallelWorldCount": 1,
                                  "trafficEnabled": true,
                                  "weatherEnabled": true,
                                  "merchantBacklogEnabled": true,
                                  "driverMicroMobilityEnabled": true,
                                  "harvestLoggingEnabled": true,
                                  "teacherTraceLoggingEnabled": true
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }
}
