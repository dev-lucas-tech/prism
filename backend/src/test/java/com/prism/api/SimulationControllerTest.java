package com.prism.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SimulationControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void simulate_validRequest_returns201Authorized() throws Exception {
        mvc.perform(post("/api/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corridor\":\"MXN\",\"amount\":1000,\"merchant\":\"Test Merchant\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.estimatedCost").exists());
    }

    @Test
    void simulate_invalidCorridor_returns400() throws Exception {
        mvc.perform(post("/api/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"corridor\":\"ATLANTIS\",\"amount\":1000,\"merchant\":\"Test\"}"))
                .andExpect(status().isBadRequest());
    }
}
