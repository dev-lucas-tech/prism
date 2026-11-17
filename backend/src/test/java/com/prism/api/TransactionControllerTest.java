package com.prism.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    MockMvc mvc;

    @Test
    void getTransactions_returnsDecomposedList() throws Exception {
        mvc.perform(get("/api/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("SETTLED"))
                .andExpect(jsonPath("$[0].hiddenFxCost").exists())
                .andExpect(jsonPath("$[0].effectiveMarkupBps").exists());
    }

    @Test
    void summary_aggregatesHiddenCostAndSavings() throws Exception {
        mvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalHiddenFxCost").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.totalStablecoinSavings").value(greaterThan(0.0)))
                .andExpect(jsonPath("$.corridorBreakdown.length()").value(5));
    }

    @Test
    void getTransaction_unknownId_returns404() throws Exception {
        mvc.perform(get("/api/transactions/does-not-exist"))
                .andExpect(status().isNotFound());
    }
}
