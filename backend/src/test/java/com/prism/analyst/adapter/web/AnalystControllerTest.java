package com.prism.analyst.adapter.web;

import com.prism.analyst.config.AnalystProperties;
import com.prism.analyst.domain.AgentEvent;
import com.prism.analyst.domain.AgentTraceSink;
import com.prism.analyst.domain.AiNotConfiguredException;
import com.prism.analyst.domain.AnalystReport;
import com.prism.analyst.domain.FxAnalystAgent;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalystControllerTest {

    private final ObjectMapper mapper = JsonMapper.builder().build();
    private final FxAnalystAgent agent = mock(FxAnalystAgent.class);
    private final Executor inlineExecutor = Runnable::run;
    private final AnalystProperties props = configuredProps();
    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new AnalystController(agent, inlineExecutor, mapper, props))
            .build();

    private AnalystProperties configuredProps() {
        AnalystProperties p = new AnalystProperties();
        p.setApiKey("sk-test");
        return p;
    }

    private AnalystReport sampleReport() {
        return new AnalystReport("You lost $42", new BigDecimal("42.00"),
                List.of(new AnalystReport.Finding("BRL markup", "high", new BigDecimal("42.00"),
                        "BRL", List.of(), "route via stablecoin")));
    }

    @Test
    void streamEmitsFinalEvent() throws Exception {
        AnalystReport report = sampleReport();
        doAnswer(invocation -> {
            AgentTraceSink sink = invocation.getArgument(1);
            sink.emit(new AgentEvent.RoundStart(1));
            sink.emit(new AgentEvent.Final(report));
            return report;
        }).when(agent).run(any(), any());

        MvcResult started = mvc.perform(get("/api/analyst/stream")).andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(started)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("final"), () -> "no final event in: " + body);
        assertTrue(body.contains("You lost $42"), () -> "no headline in: " + body);
    }

    @Test
    void statusReportsConfiguredWhenKeyPresent() throws Exception {
        mvc.perform(get("/api/analyst/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true));
    }

    @Test
    void aiNotConfiguredEmitsErrorEvent() throws Exception {
        doThrow(new AiNotConfiguredException("no key")).when(agent).run(any(), any());

        MvcResult started = mvc.perform(get("/api/analyst/stream")).andExpect(request().asyncStarted()).andReturn();
        String body = mvc.perform(asyncDispatch(started))
                .andReturn().getResponse().getContentAsString();

        assertTrue(body.contains("error"), () -> "no error event in: " + body);
        assertTrue(body.toLowerCase().contains("not configured"), () -> "no friendly message in: " + body);
    }
}
