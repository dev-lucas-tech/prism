# Agentic FX Analyst — design

- Date: 2026-11-18
- Status: **Approved** 2026-11-18 (window = last 30 tx; default model `anthropic/claude-sonnet-4.5`,
  cheaper slug for test runs). Next step: implementation plan.
- Feature owner: Lucas. Reviewer pass: self (builder-check inline; no subagent per harness policy).

## Intent & why

Prism today reveals hidden FX cost on a single card receipt and in dashboard
aggregates. That is strong **product thinking**, but a thin **engineering** and
**AI** showcase for the Jeeves Phase-2 Vibe Coding Challenge. The challenge
scorecard has five axes (Product thinking, Engineering, Decision-making, Use of
tools, Communication). Prism currently scores well on Product but leaves **"Use
of tools — smart use of AI"** essentially blank and keeps Engineering light.

This feature adds an **agentic FX analyst**: a backend tool-calling agent that
investigates the user's recent card activity over a real multi-step tool loop
and returns a ranked, evidence-cited set of FX-savings findings. It fills the AI
axis, deepens the engineering story (agent orchestration, tool dispatch,
structured I/O, live streaming, resilience), and stays true to Prism's thesis:
make hidden FX **visible** → now also **actionable**. It reuses the candidate's
own proven OpenRouter integration pattern (from `gift-genius-hub`) ported to
Java/Spring — a defensible "smart use of tools" talking point (the brief allows
boilerplate reuse; the Prism project itself is new).

## Goals

- A **real multi-step tool-calling loop** (LLM ↔ backend tools), not a single-shot prompt.
- The agent reasons over the **last 30 settled transactions** and returns ranked
  findings + recommended actions, each **citing the specific transactions/corridors** it used.
- **All monetary figures come from Prism's deterministic `FxCalculator` via tools;
  the model never invents numbers** — it orchestrates, prioritizes, and narrates.
- **Live-stream the agent's tool-trace** (which tool, which args, what returned) to the
  UI, so the loop is visibly real in the ~5-minute demo.
- Robust: retry/backoff, handle 402/429/timeouts, a **bounded loop** (max rounds), graceful degradation.
- **Hexagonal (ports & adapters) architecture, SOLID throughout, security by design** — so every
  design choice is defensible under interview scrutiny ("how could you forget about X?").
- Runs locally with one env var (`OPENROUTER_API_KEY`); clear README.

## Non-goals

- No free-form chat UI or conversation history (one analysis run per click, with an optional one-shot question).
- No persistence of analyses, no auth, no multi-tenant.
- No new transaction data model; reuse the existing seed (45 settled tx, 5 corridors).
- No agent that writes/mutates data — **read-only tools only**.

## Anti-goals (a failure even if it technically runs)

- The model emitting numbers that disagree with Prism's own math — this would erode
  the entire transparency thesis. Guardrail: **numbers only via tools**.
- A loop that runs away (unbounded tool calls / token burn / cost). Guardrail:
  **hard cap on rounds** + max transactions returned per call.
- A demo that silently hangs on an API hiccup. Guardrail: timeouts + a **visible error state**.
- Scope creep into "a fuller fintech." The brief explicitly rewards focus:
  *"a focused problem solved well beats a huge one solved halfway."*

## Constraints

- Stack fixed: **Java 21, Spring Boot 4.1.2, Spring MVC (servlet), H2**. Frontend **React 19 / Vite / Tailwind v4**.
- SSE infra exists (`SseService`, `SseEmitter(Long.MAX_VALUE)`), but it is a **broadcast fan-out**;
  an agent run needs a **per-request** stream instead.
- HTTP to OpenRouter: Spring **`RestClient`** (ships with the webmvc starter — no new deps). Jackson available.
- Deadline **~1 day** (due ~2026-11-19). Must stay demo-safe.
- Uses the candidate's **OpenRouter credits** (paid model). Cost per run ≈ pennies; keep bounded.

## Approaches considered

- **A — grounded single-shot advisor.** Pre-inject computed data, one LLM call, strict-JSON out.
  Safest and cheapest, but not a real agent loop; weaker engineering story. **Rejected** — under-sells; a loop is the point.
- **B — true tool-calling agent loop. → CHOSEN.** LLM given read-only tools; multi-round
  investigate → drill → conclude. Strongest Engineering + AI story. The usual risk (free-model
  tool-calling is flaky) is **removed** by using a strong paid model on the candidate's own credits.
- **C — chat copilot over spend.** A grounded chat box. **Rejected** — cliché ("I added a chatbot"),
  hides the engineering, weakest differentiation.

Chosen: **B**.

## The design (decisions made)

### Architecture — hexagonal (ports & adapters)

The agent is a **domain core** that depends only on abstractions (ports); all I/O lives in adapters.
This keeps the orchestration logic pure and testable with fakes, and makes the LLM provider swappable.

```
                 driving (inbound) adapter                 domain core                     driven (outbound) adapters
  HTTP/SSE  ──▶  AnalystController (web)  ──▶  FxAnalystAgent (orchestration)  ──▶  LlmClient (port) ──▶ OpenRouterLlmAdapter (RestClient)
                                               │  uses AgentToolRegistry (port) ──▶  FxAnalystTools  ──▶ TransactionService (existing domain)
                                               └─ emits AgentEvent to a TracePort ──▶ SseTraceAdapter (per-request SseEmitter)
```

- **Ports (interfaces owned by the domain):**
  - `LlmClient` — `complete(ChatRequest) -> ChatResponse`. The only thing the agent knows about an LLM.
    Adapter: `OpenRouterLlmAdapter` (RestClient, auth, retries, JSON mapping). Nothing OpenRouter-specific leaks into the core.
  - `AgentTool` — `name()`, `jsonSchema()`, `invoke(JsonNode args) -> JsonNode`. Implemented by the three
    read-only tools; registered in an `AgentToolRegistry`.
  - `AgentTraceSink` — `emit(AgentEvent)`. Adapter `SseTraceAdapter` pushes to the per-request emitter;
    a `NoOpTraceSink` is used by the non-streaming/test path.
- **Domain core** (`com.prism.analyst.domain`): `FxAnalystAgent`, the loop/round model, `AgentEvent`,
  the final-report record. No Spring-web, no RestClient, no SSE types here → unit-testable with a fake `LlmClient`.
- **Adapters** (`com.prism.analyst.adapter.*`): web (controller + SSE), llm (OpenRouter), tools (bridge to `TransactionService`).
- The tools reuse the **existing** `TransactionService`/`FxCalculator` domain rather than re-implementing math,
  so there is a single source of financial truth (the deterministic-numbers guarantee is structural, not a convention).

### SOLID (how each principle shows up)

- **S** — one reason to change per unit: `FxAnalystAgent` orchestrates only; `OpenRouterLlmAdapter` owns
  transport; each `AgentTool` owns one capability; `SseTraceAdapter` owns streaming; report assembly is its own mapper.
- **O** — the loop is **open for extension, closed for modification**: new tools plug into `AgentToolRegistry`
  with no change to the loop; a new provider is a new `LlmClient` adapter.
- **L** — any `LlmClient`/`AgentTool` implementation (real or fake) is substitutable; tests swap in fakes with no special-casing.
- **I** — narrow, role-specific ports (`LlmClient`, `AgentTool`, `AgentTraceSink`) instead of one fat "AI service" interface.
- **D** — the domain depends on **abstractions**; concrete RestClient/SSE/OpenRouter live in adapters wired by Spring config.

### Security by design (threat-model notes)

- **Secrets:** `OPENROUTER_API_KEY` from env only — never in source, logs, or the trace stream. The
  `Authorization` header is set in the adapter and redacted from any debug logging. Tool results and prompts
  sent to the model carry no secrets.
- **Prompt-injection / tool-abuse containment:** the model can reach data **only** through three allowlisted,
  **read-only** tools — no arbitrary SQL, no free-form query, no write path. Seed/merchant text can't escalate
  into an action because there is no action surface.
- **Tool-argument validation (defense in depth):** `limit` is clamped to `[1,30]`; `corridor` must parse to the
  `Corridor` enum or the call is rejected; `id` must resolve to an existing transaction (else a safe "not found"
  tool error, not an exception); `sinceDays` bounded. Invalid args return a structured tool error the model can recover from.
- **Egress / SSRF:** the LLM adapter calls exactly one fixed base URL (`https://openrouter.ai/api/v1`);
  no model-controlled URLs, no fetching arbitrary links.
- **Resource / cost DoS bounds:** hard caps on rounds (6), `max_tokens`, response size, and tx rows returned;
  connect/read timeouts. This bounds both runaway loops and spend.
- **Output trust:** the final report is **schema-validated**; every `evidenceTxId` the model cites is verified to
  exist before the UI renders it, so a hallucinated id can't surface as a real citation.
- **Error hygiene:** clients get typed, generic errors (AI-not-configured / busy / credits-exhausted) — never
  stack traces, provider bodies, or internal detail.
- **Data sensitivity:** seed employee names are synthetic; no real PII. Noted so the demo can't be misread as leaking data.

### Agent loop
- New `FxAnalystAgent` service runs an OpenAI-compatible `chat/completions` loop against OpenRouter.
- **System prompt:** role = FX savings analyst for a Jeeves cardholder; must base every figure on
  tool results; must end with the structured final answer.
- **Seed user message (default):** *"Analyze my last 30 card purchases and tell me where I'm losing
  the most to FX and what to do about it."* An optional user-supplied question overrides/extends the seed.
- **Loop:** send messages + tool schemas → model returns `tool_calls` → execute the requested tools →
  append results as `tool` messages → repeat. **Hard cap: 6 rounds** (configurable). On hitting the cap,
  do one final forced call instructing the model to submit its findings with what it has.
- **Termination via a terminal tool, not `response_format`.** The loop ends when the model calls
  `submit_findings(...)` — a dedicated tool whose JSON schema **is** the final report. This (a) keeps the
  whole run in one uniform `tools` mechanism (mixing OpenAI `tools` with `response_format: json_schema`
  conflicts on several providers), (b) makes termination explicit and inspectable, and (c) means the agent's
  *last tool call is the report* — a clean thing to show on camera. A fence-stripping/validation fallback
  guards against a malformed arguments blob.

### Tools (read-only; every number comes from `FxCalculator` via `TransactionService`)
1. `get_spend_summary()` → totals (spend, hidden FX, effective markup bps, stablecoin savings) +
   per-corridor rows (count, spend, hidden, savings). Cheap entry point. Maps to `TransactionService.summary()`.
2. `list_recent_transactions(limit=30, corridor?, sinceDays?)` → compact rows (id, date, merchant,
   corridor, purchaseAmount, settledCost, hiddenFxCost, markupBps). Maps to `TransactionService.list()`
   + filter/truncate. Default and max `limit` = 30; `sinceDays` lets the agent narrow by time window.
3. `get_transaction_breakdown(id)` → full `FxBreakdown` for one tx (incl. stablecoin savings,
   surprise-vs-estimate). Maps to `TransactionService.getById(id)`.
4. `submit_findings(report)` → **terminal** tool; its schema is the final report (headline,
   totalAddressable, findings[]: title, severity, dollarsAtStake, corridor, evidenceTxIds[], recommendation).
   Calling it ends the loop; the orchestrator validates it, verifies each `evidenceTxId` exists, and emits `final`.
- The three read tools are **read-only**; `submit_findings` has no side effects beyond ending the run.
- Dispatch via a small `AgentToolRegistry` (name → JSON schema → handler) so the loop code is generic
  and adding a tool later is trivial (OCP).

### Streaming (demo visibility)
- Controller endpoint returns a **per-request `SseEmitter`** (NOT the broadcast `SseService`).
- The agent runs on a worker thread and emits events: `round_start`, `tool_call` (name + args),
  `tool_result` (compact), `final` (the JSON report), `error`; then completes the emitter.
- Single round-trip: the UI opens the stream, watches the trace build, then renders the final report —
  no separate subscribe step.

### Model & config
- Default model **`anthropic/claude-sonnet-4.5`** (strong, reliable tool-calling + structured output),
  configurable via `prism.ai.model`. **For dev/test runs, point it at a cheaper slug** (e.g.
  `openai/gpt-4o-mini`, `anthropic/claude-3.5-haiku`, or `google/gemini-2.0-flash`) to keep iteration
  cost near zero; the final demo run uses the Sonnet default. Exact slugs verified against OpenRouter at build time.
- `OPENROUTER_API_KEY` read from env. If absent, the endpoint returns a clear "AI not configured"
  error and the UI shows a disabled state (app still runs without AI).
- Timeouts (connect/read), max rounds, and max tokens all in config with sane defaults.
- Resilience ported from giftwise: retry on 429 with backoff, explicit 402 (credits) message,
  fence-stripped JSON parse fallback.

### Frontend
- **Frontend architecture — layered (adapter → hook → presentation)**, the client-side mirror of the
  backend's ports & adapters, and consistent with Prism's existing `api/ hooks/ components/ lib/` layout:
  - **Adapter** (`api/analyst.ts`): transport only — opens the `EventSource`, parses raw SSE frames into
    typed domain events; no React.
  - **Orchestration** (`useAnalyst()` hook): owns run state (status, accumulating trace, final report,
    error), calls the adapter, exposes a clean state object — the client's "use-case" seam.
  - **Presentation** (`AnalystPanel.tsx` + small pure subcomponents): render hook state only; no transport.
  - Considered and rejected: Feature-Sliced Design (overkill for one feature); plain
    container/presentational (fine, but the hook seam tests better and matches the repo's `hooks/`).
- New **"FX Analyst"** panel/button on the dashboard: *"Analyze recent activity."* On click it opens the
  SSE stream, renders the **live tool-trace** (a compact timeline), then the **final findings cards**
  (severity, $ at stake, evidence-tx chips, recommendation). Optional text input for a custom question.
- Reuse existing money/bps formatters and card styling.

### Testing
- Backend: unit-test each tool handler (deterministic service output); unit-test the loop with a
  **mocked OpenRouter** (scripted `tool_calls` → assert dispatch order + final parse); test the loop
  cap and error paths. Match the repo's JUnit5 / MockMvc style.
- Frontend: component-test the panel rendering trace events + final cards from a mocked stream;
  disabled/error states.
- One **real E2E run** against OpenRouter is done manually for the demo (not in CI; needs the key).

## What's left to the builder

- Exact JSON schemas for tool params + the final report (to be pinned in the plan).
- Thread model for the SSE run (simple `@Async` or a small dedicated executor).
- Whether to add a 4th tool (`compare_corridor_alternatives`) — only if the loop actually needs it;
  default **no** (stablecoin savings already live in `FxBreakdown`).
- Prompt wording iteration during the manual E2E.

## Open calls handed back to the owner (confirm at approval)

1. **Scope window** = last **30 transactions** (with a `sinceDays` escape hatch), not a flat 15-day window — for a deterministic, demo-stable run. Correct me if you'd rather lead with the time window.
2. **Model** = `anthropic/claude-sonnet-4.5` by default (your credits, ~pennies/run), env-configurable. Swap if you prefer a different one.
