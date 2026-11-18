# Agentic FX Analyst Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an agentic FX analyst to Prism — a bounded tool-calling loop where an LLM investigates the user's last 30 settled card transactions over read-only tools and submits ranked, evidence-cited FX-savings findings, with the live tool-trace streamed to the UI.

**Architecture:** Hexagonal (ports & adapters). A pure domain core (`FxAnalystAgent` + ports `LlmClient`, `AgentTool`, `AgentTraceSink`) runs the loop; adapters supply I/O (OpenRouter via `RestClient`, SSE streaming, and tool bridges onto the existing `TransactionService`). Every monetary number originates in the deterministic `FxCalculator` via tools; the model only orchestrates and narrates.

**Tech Stack:** Java 21, Spring Boot 4.1.2 (Spring MVC / servlet), Jackson, Spring `RestClient` (no new deps), H2. Frontend React 19 / Vite / Tailwind v4 / Vitest.

**Spec:** `docs/superpowers/specs/2026-11-18-agentic-fx-analyst-design.md`

## Global Constraints

- Java 21; Spring Boot 4.1.2; Spring MVC (servlet) — not WebFlux.
- OpenRouter HTTP via Spring `RestClient`; **no new Gradle dependencies**.
- Tools are **read-only**; no SQL, no write path, no model-controlled URLs. Single egress base URL `https://openrouter.ai/api/v1`.
- **Every financial figure comes from `FxCalculator` via `TransactionService`; the model never computes numbers.**
- Hard caps: loop **≤ 6 rounds** (`prism.ai.max-rounds`), `list_recent_transactions` limit clamped to `[1,30]`, bounded `max_tokens`, connect/read timeouts.
- `OPENROUTER_API_KEY` from env only; **never logged, never sent in the trace stream**; `Authorization` header redacted from any logging.
- Default model `anthropic/claude-sonnet-4.5`; `prism.ai.model` configurable (cheaper slug for dev/test).
- No explanatory code comments / Javadoc (user preference); keep log/exception/test strings.
- Backend tests: JUnit5 (`assertEquals` + `compareTo` for BigDecimal), MockMvc field named `mvc`, `MockRestServiceServer` for `RestClient`. Frontend tests: Vitest.
- New code under `com.prism.analyst` (`domain`, `adapter.web`, `adapter.llm`, `adapter.tools`, `config`).

## Review Focus

- **Missing/blank `OPENROUTER_API_KEY`** → typed "AI not configured" result and a disabled UI state, never a 500. (Task 4 adapter, Task 6 endpoint + Task 7 UI.)
- **Model emits a tool call with invalid args** (`limit=999`, unknown corridor, non-existent tx id) → safe structured tool-error the model can recover from, no exception thrown. (Tasks 2, 3.)
- **Model never calls `submit_findings` within 6 rounds** → one forced final request, then the loop terminates; no infinite loop / unbounded spend. (Task 5.)
- **OpenRouter 429 / 402 / timeout mid-run** → retry-with-backoff on 429, typed messages for 402 (credits) and timeout, stream completes with an `error` event (no hung SSE). (Task 4 + Task 6.)
- **Model cites an `evidenceTxId` that does not exist** → verified against the repository and dropped before the UI renders it; a hallucinated id never shows as a real citation. (Task 3.)

---

### Task 1: Tool port, registry, and `get_spend_summary`

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/domain/AgentTool.java`
- Create: `backend/src/main/java/com/prism/analyst/domain/AgentToolRegistry.java`
- Create: `backend/src/main/java/com/prism/analyst/adapter/tools/SpendSummaryTool.java`
- Test: `backend/src/test/java/com/prism/analyst/adapter/tools/SpendSummaryToolTest.java`

**Interfaces:**
- Consumes: existing `TransactionService.summary() -> SummaryDto`.
- Produces:
  - `interface AgentTool { String name(); com.fasterxml.jackson.databind.JsonNode schema(); JsonNode invoke(JsonNode args); }`
  - `class AgentToolRegistry { AgentToolRegistry(List<AgentTool> tools); Optional<AgentTool> find(String name); List<AgentTool> all(); }`
  - `SpendSummaryTool implements AgentTool` with `name() == "get_spend_summary"`.

- [ ] **Step 1: Write the failing test**

```java
// SpendSummaryToolTest (plain unit test; construct SpendSummaryTool with a TransactionService whose summary() returns a fixed SummaryDto)
@Test void returnsTotalsAndPerCorridorRows() {
  JsonNode out = tool.invoke(mapper.createObjectNode());
  assertEquals("get_spend_summary", tool.name());
  assertEquals(0, new BigDecimal(out.get("totalHiddenFxCost").asText()).compareTo(expectedHidden));
  assertTrue(out.get("corridors").isArray());
  assertEquals(expectedCorridorCount, out.get("corridors").size());
}
@Test void schemaIsObjectWithNoRequiredArgs() {
  assertEquals("object", tool.schema().get("type").asText());
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.adapter.tools.SpendSummaryToolTest"` → FAIL (classes not defined).
- [ ] **Step 3: Implement** `AgentTool`, `AgentToolRegistry`, and `SpendSummaryTool.invoke` (map `SummaryDto` → JSON: `totalSpend`, `totalHiddenFxCost`, `effectiveMarkupBps`, `totalStablecoinSavings`, `corridors[]{corridor,count,spend,hiddenFxCost,savings}`). Inject a shared Jackson `ObjectMapper`.
- [ ] **Step 4: Run test to verify it passes** — same command → PASS.
- [ ] **Step 5: Commit** — `git add backend/src/.../analyst/domain/AgentTool.java backend/src/.../AgentToolRegistry.java backend/src/.../tools/SpendSummaryTool.java backend/src/test/.../SpendSummaryToolTest.java && git commit -m "feat(analyst): tool port, registry, and get_spend_summary tool"`

---

### Task 2: Read tools `list_recent_transactions` + `get_transaction_breakdown` (with arg validation)

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/adapter/tools/ListRecentTransactionsTool.java`
- Create: `backend/src/main/java/com/prism/analyst/adapter/tools/GetTransactionBreakdownTool.java`
- Test: `backend/src/test/java/com/prism/analyst/adapter/tools/ReadToolsTest.java`

**Interfaces:**
- Consumes: `AgentTool` (Task 1); `TransactionService.list() -> List<TransactionDto>`, `getById(String) -> TransactionDto`; `Corridor` enum.
- Produces: tools named `"list_recent_transactions"` (args: `limit?`, `corridor?`, `sinceDays?`) and `"get_transaction_breakdown"` (args: `id`).

- [ ] **Step 1: Write the failing test**

```java
@Test void listClampsLimitToThirtyAndDefaultsToThirty() { /* request limit=999 -> at most 30 rows; no limit -> <=30 */ }
@Test void listRejectsUnknownCorridorWithToolError() {
  JsonNode out = listTool.invoke(args("corridor","XYZ"));
  assertTrue(out.has("error"));           // safe, recoverable — no exception
}
@Test void listFiltersByCorridorAndSinceDays() { /* only matching corridor; sinceDays bounds window */ }
@Test void breakdownReturnsHiddenFxForKnownId() {
  assertEquals(0, new BigDecimal(breakdownTool.invoke(args("id", knownId)).get("hiddenFxCost").asText()).compareTo(expected));
}
@Test void breakdownUnknownIdReturnsToolErrorNotException() {
  assertTrue(breakdownTool.invoke(args("id","nope")).has("error"));
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.adapter.tools.ReadToolsTest"` → FAIL.
- [ ] **Step 3: Implement** both tools. `list`: clamp `limit` into `[1,30]` (default 30), validate `corridor` against `Corridor` enum (invalid → `{"error":...}`), filter `sinceDays` against `authorizedAt`, map to compact rows (`id,date,merchant,corridor,purchaseAmount,settledCost,hiddenFxCost,markupBps`). `breakdown`: catch the `ResponseStatusException` NOT_FOUND from `getById` and return `{"error":"transaction not found"}`; else full breakdown fields.
- [ ] **Step 4: Run test to verify it passes** — PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(analyst): read tools with clamped/validated args"`

---

### Task 3: Final report records + terminal `submit_findings` tool + evidence verification

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/domain/AnalystReport.java` (record + nested `Finding`)
- Create: `backend/src/main/java/com/prism/analyst/adapter/tools/SubmitFindingsTool.java`
- Test: `backend/src/test/java/com/prism/analyst/adapter/tools/SubmitFindingsToolTest.java`

**Interfaces:**
- Consumes: `AgentTool` (Task 1); `TransactionRepository.existsById(String)` (add if absent) or `findById`.
- Produces:
  - `record Finding(String title, String severity, BigDecimal dollarsAtStake, String corridor, List<String> evidenceTxIds, String recommendation)`
  - `record AnalystReport(String headline, BigDecimal totalAddressable, List<Finding> findings)`
  - `SubmitFindingsTool` — `name() == "submit_findings"`, `schema()` is the report schema, and `parse(JsonNode args) -> AnalystReport` that **drops any `evidenceTxId` not present in the repository**.

- [ ] **Step 1: Write the failing test**

```java
@Test void parsesReportFromArgs() { AnalystReport r = tool.parse(validArgsJson); assertEquals(2, r.findings().size()); }
@Test void dropsUnknownEvidenceTxIds() {
  AnalystReport r = tool.parse(argsWithOneRealOneFakeId);   // real = seed id, fake = "ghost-1"
  assertEquals(List.of(realId), r.findings().get(0).evidenceTxIds());
}
@Test void malformedArgsThrowReportParseException() {
  assertThrows(ReportParseException.class, () -> tool.parse(garbageJson));
}
@Test void schemaMarksFindingsRequired() { assertTrue(tool.schema().toString().contains("findings")); }
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.adapter.tools.SubmitFindingsToolTest"` → FAIL.
- [ ] **Step 3: Implement** the records, `SubmitFindingsTool` (schema + `parse` that validates structure, throws `ReportParseException` on malformed args, and filters `evidenceTxIds` through `repository.existsById`). `invoke` may delegate to `parse` and return the normalized JSON; the orchestrator (Task 5) treats this tool name as terminal.
- [ ] **Step 4: Run test to verify it passes** — PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(analyst): report records + terminal submit_findings with evidence verification"`

---

### Task 4: `LlmClient` port + `OpenRouterLlmAdapter` + config properties

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/domain/LlmClient.java` (+ chat DTOs `ChatMessage`, `ChatRequest`, `ChatResponse`, `LlmToolCall`, `ToolSpec`)
- Create: `backend/src/main/java/com/prism/analyst/adapter/llm/OpenRouterLlmAdapter.java`
- Create: `backend/src/main/java/com/prism/analyst/config/AnalystProperties.java`
- Create: `backend/src/main/java/com/prism/analyst/domain/AiNotConfiguredException.java`, `AiUnavailableException.java`
- Test: `backend/src/test/java/com/prism/analyst/adapter/llm/OpenRouterLlmAdapterTest.java`

**Interfaces:**
- Consumes: Spring `RestClient`, `AnalystProperties` (`apiKey`, `model`, `baseUrl`, `maxTokens`, `maxRounds`, `connectTimeoutMs`, `readTimeoutMs`).
- Produces:
  - `interface LlmClient { ChatResponse complete(ChatRequest request); }`
  - `ChatRequest(List<ChatMessage> messages, List<ToolSpec> tools)`; `ChatResponse(String assistantContent, List<LlmToolCall> toolCalls)`; `LlmToolCall(String id, String name, JsonNode arguments)`.
  - `OpenRouterLlmAdapter implements LlmClient` — maps to/from OpenAI-compatible `chat/completions` with `tools`.

- [ ] **Step 1: Write the failing test** (use `MockRestServiceServer.bindTo(RestClient.builder())`)

```java
@Test void mapsAssistantToolCallsFromResponse() { /* server returns choices[0].message.tool_calls -> assert ChatResponse.toolCalls */ }
@Test void missingApiKeyThrowsAiNotConfigured() {
  AnalystProperties p = props(null); // no key
  assertThrows(AiNotConfiguredException.class, () -> new OpenRouterLlmAdapter(builder, p).complete(req));
}
@Test void retriesOnceOn429ThenSucceeds() { server.expect(...).andRespond(429); server.expect(...).andRespond(ok); /* assert success */ }
@Test void http402ThrowsAiUnavailableWithCreditsMessage() { /* 402 -> AiUnavailableException, message mentions credits */ }
@Test void doesNotLogOrEchoAuthorizationHeader() { /* assert request carried Bearer header to server; adapter never returns it */ }
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.adapter.llm.OpenRouterLlmAdapterTest"` → FAIL.
- [ ] **Step 3: Implement** `AnalystProperties` (`@ConfigurationProperties("prism.ai")`, `apiKey` bound from `${OPENROUTER_API_KEY:}`), the port + DTOs, and `OpenRouterLlmAdapter`: build `RestClient` with base URL + timeouts; throw `AiNotConfiguredException` when key blank; POST `chat/completions` with `model`, `messages`, `tools`, `max_tokens`; retry once on 429 with a short backoff; map 402 → `AiUnavailableException("credits")`, timeouts/5xx → `AiUnavailableException`; parse `tool_calls`/`content`. Fence-stripping of tool-call `arguments` only if needed.
- [ ] **Step 4: Run test to verify it passes** — PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(analyst): LlmClient port + OpenRouter adapter + config"`

---

### Task 5: `FxAnalystAgent` orchestration loop + trace sink port (domain core)

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/domain/AgentTraceSink.java`
- Create: `backend/src/main/java/com/prism/analyst/domain/AgentEvent.java` (sealed: `RoundStart`, `ToolCall`, `ToolResult`, `Final`, `ErrorEvent`)
- Create: `backend/src/main/java/com/prism/analyst/domain/FxAnalystAgent.java`
- Test: `backend/src/test/java/com/prism/analyst/domain/FxAnalystAgentTest.java`

**Interfaces:**
- Consumes: `LlmClient` (Task 4), `AgentToolRegistry` + `AgentTool` (Task 1), `SubmitFindingsTool`/`AnalystReport` (Task 3), `AnalystProperties.maxRounds`.
- Produces: `class FxAnalystAgent { FxAnalystAgent(LlmClient, AgentToolRegistry, SubmitFindingsTool, AnalystProperties, ObjectMapper); AnalystReport run(String question, AgentTraceSink sink); }` — terminates when the model calls `submit_findings`, or after `maxRounds` via one forced final request.

- [ ] **Step 1: Write the failing test** (fake `LlmClient` scripted per call; capturing `AgentTraceSink`)

```java
@Test void happyPathDispatchesToolsThenReturnsReport() {
  // fake Llm: round1 -> call get_spend_summary; round2 -> call get_transaction_breakdown; round3 -> call submit_findings
  AnalystReport r = agent.run(null, sink);
  assertEquals(expectedHeadline, r.headline());
  assertTrue(sink.names().contains("tool_call"));
  assertTrue(sink.names().contains("final"));
}
@Test void stopsAtMaxRoundsWithForcedFinal() {
  // fake Llm: always calls get_spend_summary, never submits; maxRounds=3
  AnalystReport r = agent.run(null, sink);     // forced final call returns a report
  assertNotNull(r);
  assertTrue(sink.roundCount() <= 3);
}
@Test void unknownToolEmitsRecoverableErrorResult() {
  // fake Llm calls "do_evil" once, then submit_findings
  agent.run(null, sink);
  assertTrue(sink.toolResults().stream().anyMatch(s -> s.contains("unknown tool")));
}
@Test void malformedSubmitEmitsErrorEvent() { /* submit with garbage args -> ErrorEvent, run throws/returns null per contract */ }
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.domain.FxAnalystAgentTest"` → FAIL.
- [ ] **Step 3: Implement** the loop: seed system + user message (default question when null), expose registry tool specs + `submit_findings`; each round emit `RoundStart`, call `LlmClient.complete`, for each returned tool call emit `ToolCall`, dispatch (unknown → `{"error":"unknown tool"}` appended as tool result), emit `ToolResult`; if `submit_findings` called → `parse` via `SubmitFindingsTool`, emit `Final`, return report; on reaching `maxRounds` send one final forced request; on `ReportParseException` emit `ErrorEvent`. No real I/O in this class.
- [ ] **Step 4: Run test to verify it passes** — PASS.
- [ ] **Step 5: Commit** — `git commit -m "feat(analyst): tool-calling orchestration loop with bounded rounds"`

---

### Task 6: Web adapter — SSE endpoint, trace adapter, Spring wiring, README

**Files:**
- Create: `backend/src/main/java/com/prism/analyst/adapter/web/AnalystController.java`
- Create: `backend/src/main/java/com/prism/analyst/adapter/web/SseTraceAdapter.java`
- Create: `backend/src/main/java/com/prism/analyst/config/AnalystConfig.java` (RestClient.Builder, ObjectMapper, bounded executor, `@EnableConfigurationProperties`)
- Modify: `backend/src/main/resources/application.yaml` (`prism.ai.*` defaults)
- Modify: `prism/README.md` (OPENROUTER_API_KEY, model config, cheap test model, how to run the analyst)
- Test: `backend/src/test/java/com/prism/analyst/adapter/web/AnalystControllerTest.java`

**Interfaces:**
- Consumes: `FxAnalystAgent` (Task 5), `AgentTraceSink` (Task 5), `AnalystProperties`.
- Produces: `GET /api/analyst/stream?q={optional}` → `SseEmitter`, events named `round_start|tool_call|tool_result|final|error`; agent runs on a bounded executor; emitter completed at end.

- [ ] **Step 1: Write the failing test** (`@WebMvcTest(AnalystController.class)` with a mocked/stubbed `FxAnalystAgent`, MockMvc field `mvc`, async dispatch)

```java
@Test void streamEmitsFinalEvent() throws Exception {
  // stub agent.run(..) to emit a round + final via the passed sink and return a report
  MvcResult res = mvc.perform(get("/api/analyst/stream")).andExpect(request().asyncStarted()).andReturn();
  mvc.perform(asyncDispatch(res)).andExpect(status().isOk())
     .andExpect(content().string(containsString("event:final")));
}
@Test void aiNotConfiguredEmitsErrorEvent() {
  // stub agent.run to throw AiNotConfiguredException -> content contains event:error and "not configured"
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew test --tests "com.prism.analyst.adapter.web.AnalystControllerTest"` → FAIL.
- [ ] **Step 3: Implement** `SseTraceAdapter` (`AgentTraceSink` → `SseEmitter.send(event().name(...).data(...))`), `AnalystController` (create emitter, submit agent run to the executor, map `AiNotConfiguredException`/`AiUnavailableException` → `error` event, always `complete()`), `AnalystConfig` beans, `application.yaml` defaults (`model`, `max-rounds: 6`, `max-tokens`, timeouts, `base-url`), and README section.
- [ ] **Step 4: Run tests to verify they pass** — `./gradlew test` (full) → BUILD SUCCESSFUL, no regressions.
- [ ] **Step 5: Commit** — `git commit -m "feat(analyst): SSE endpoint, trace adapter, wiring, README"`

---

### Task 7: Frontend — FX Analyst panel (live trace + findings)

**Files:**
- Create: `frontend/src/api/analyst.ts` (adapter: open the SSE stream; parse to typed events)
- Create: `frontend/src/hooks/useAnalyst.ts` (orchestration: run state from the adapter)
- Create: `frontend/src/components/AnalystPanel.tsx` (presentation)
- Modify: `frontend/src/App.tsx` (mount the panel)
- Test: `frontend/src/hooks/useAnalyst.test.ts`, `frontend/src/components/AnalystPanel.test.tsx`

**Architecture:** layered adapter → hook → presentation (client mirror of ports & adapters).

**Interfaces:**
- Consumes: `GET /api/analyst/stream?q=` SSE events (`round_start|tool_call|tool_result|final|error`).
- Produces:
  - adapter `openAnalystStream(question: string | undefined, handlers: {onTrace,onFinal,onError}) => () => void` (returns close fn);
  - hook `useAnalyst()` → `{ status, trace, report, error, run(question?), reset() }`;
  - `AnalystPanel` React component (renders hook state only).

- [ ] **Step 1: Write the failing test** (mock `EventSource`)

```ts
it("renders the live tool-trace then the final findings cards", async () => {
  // dispatch round_start, tool_call(get_spend_summary), final({headline, findings:[...]})
  // assert trace rows appear, then a finding card with its $ at stake + evidence chips
});
it("shows a disabled/error state on an error event", async () => {
  // dispatch error("AI not configured") -> assert friendly message, no crash
});
```

- [ ] **Step 2: Run test to verify it fails** — `npx vitest run src/components/AnalystPanel.test.tsx` → FAIL.
- [ ] **Step 3: Implement** `analyst.ts` (`EventSource`, parse each event, `onTrace`/`onFinal`/`onError`, close on final/error) and `AnalystPanel.tsx` ("Analyze recent activity" button + optional question input, live trace timeline, findings cards reusing money/bps formatters, error state). Mount in `App.tsx`.
- [ ] **Step 4: Run tests to verify they pass** — `npx vitest run` → all pass; `npm run build` → clean (pre-existing Recharts warning only).
- [ ] **Step 5: Commit** — `git commit -m "feat(frontend): FX Analyst panel with live trace and findings"`

---

### Final verification (not a task; manual)

- One real end-to-end run against OpenRouter with a cheap model (`prism.ai.model` override) and `OPENROUTER_API_KEY` set: start backend + frontend, click **Analyze recent activity**, confirm the live trace streams and findings render; then one run on the Sonnet default for the demo.
- Full `./gradlew test` green + frontend `vitest run` green + `npm run build` clean.
