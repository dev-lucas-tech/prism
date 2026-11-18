package com.prism.analyst.config;

import com.prism.analyst.adapter.llm.OpenRouterLlmAdapter;
import com.prism.analyst.adapter.tools.GetTransactionBreakdownTool;
import com.prism.analyst.adapter.tools.ListRecentTransactionsTool;
import com.prism.analyst.adapter.tools.SpendSummaryTool;
import com.prism.analyst.adapter.tools.SubmitFindingsTool;
import com.prism.analyst.domain.AgentToolRegistry;
import com.prism.analyst.domain.FxAnalystAgent;
import com.prism.analyst.domain.LlmClient;
import com.prism.transaction.TransactionService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;

@Configuration
@EnableConfigurationProperties(AnalystProperties.class)
public class AnalystConfig {

    @Bean
    public LlmClient llmClient(AnalystProperties props, ObjectMapper mapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeoutMs()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeoutMs()));
        RestClient.Builder builder = RestClient.builder().requestFactory(factory);
        return new OpenRouterLlmAdapter(builder, props, mapper);
    }

    @Bean
    public SubmitFindingsTool submitFindingsTool(TransactionService transactions, ObjectMapper mapper) {
        return new SubmitFindingsTool(transactions, mapper);
    }

    @Bean
    public AgentToolRegistry agentToolRegistry(TransactionService transactions, ObjectMapper mapper) {
        return new AgentToolRegistry(List.of(
                new SpendSummaryTool(transactions, mapper),
                new ListRecentTransactionsTool(transactions, mapper),
                new GetTransactionBreakdownTool(transactions, mapper)));
    }

    @Bean
    public FxAnalystAgent fxAnalystAgent(LlmClient llmClient, AgentToolRegistry registry,
                                         SubmitFindingsTool submitFindingsTool, AnalystProperties props,
                                         ObjectMapper mapper) {
        return new FxAnalystAgent(llmClient, registry, submitFindingsTool, props.getMaxRounds(), mapper);
    }

    @Bean("analystExecutor")
    public Executor analystExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(25);
        executor.setThreadNamePrefix("analyst-");
        executor.initialize();
        return executor;
    }
}
