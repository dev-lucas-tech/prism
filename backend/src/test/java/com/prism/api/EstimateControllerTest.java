package com.prism.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class EstimateControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void returnsEstimateForValidRequest() throws Exception {
        mvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "1800"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedCost").value(342.99))
                .andExpect(jsonPath("$.estimatedHiddenFxCost").value(9.99))
                .andExpect(jsonPath("$.effectiveMarkupBps").value(300))
                .andExpect(jsonPath("$.stablecoinCost").value(334.66));
    }

    @Test
    void rejectsUnknownCorridor() throws Exception {
        mvc.perform(get("/api/estimate").param("corridor", "ZZZ").param("amount", "100"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsZeroAmount() throws Exception {
        mvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeAmount() throws Exception {
        mvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "-5"))
                .andExpect(status().isBadRequest());
    }
}
