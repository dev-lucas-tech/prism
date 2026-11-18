# Prism — Sequence Diagrams

Three flows: the dashboard load, the live settlement reveal, and the agentic AI analyst loop.
GitHub renders Mermaid natively; for slides, paste any block into https://mermaid.live to export PNG/SVG.

---

## 1. Dashboard load — FX decomposition + aggregates

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as Frontend (React)
    participant TxC as TransactionController
    participant Svc as TransactionService
    participant Fx as FxCalculator (core)

    User->>UI: Open dashboard
    UI->>TxC: GET /api/summary
    TxC->>Svc: aggregate()
    Svc->>Fx: decompose(tx) per corridor
    Fx-->>Svc: hiddenFxCost, markupBps, stablecoinSavings
    Svc-->>TxC: summary (leakage, by-corridor)
    TxC-->>UI: 200 summary
    UI->>TxC: GET /api/transactions
    TxC-->>UI: 200 list (with decomposition)
    UI->>TxC: GET /api/analyst/status
    TxC-->>UI: { configured: true/false } (gates AI panel)
```

---

## 2. Simulate a purchase → live settlement reveal (SSE)

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as Frontend (React)
    participant TxC as TransactionController
    participant Sim as SettlementSimulator
    participant Svc as TransactionService
    participant Fx as FxCalculator (core)
    participant SSE as SseService

    UI->>SSE: GET /api/stream (EventSource, subscribe)
    User->>UI: Click "Simulate a purchase"
    UI->>TxC: POST /api/simulate
    TxC->>Svc: create(AUTHORIZED, est. range)
    Svc->>SSE: broadcast(created)
    SSE-->>UI: event: Pending — est. $X
    loop authorized → settling → settled (@Scheduled)
        Sim->>Svc: advance status
        Svc->>Fx: compute true cost at settlement
        Fx-->>Svc: settledCost, hiddenFxCost, bps
        Svc->>SSE: broadcast(updated)
        SSE-->>UI: event: Settled — true cost + hidden FX
    end
```

---

## 3. Agentic FX Analyst — bounded tool-calling loop, streamed

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant UI as Frontend (React)
    participant AC as AnalystController
    participant Agent as FxAnalystAgent (core)
    participant Tools as Read-only Tools
    participant Svc as TransactionService
    participant Fx as FxCalculator (core)
    participant LLM as OpenRouterLlmAdapter
    participant OR as OpenRouter API

    User->>UI: Click "Analyze recent activity"
    UI->>AC: GET /api/analyst/stream?q=... (EventSource)
    AC->>Agent: run(question, traceSink)
    loop up to maxRounds (6), until submit_findings
        Agent->>LLM: chat(messages + tool specs)
        LLM->>OR: POST /chat/completions
        OR-->>LLM: tool_call (e.g. get_spend_summary)
        LLM-->>Agent: ToolCall
        Agent-->>AC: AgentEvent: ToolCall
        AC-->>UI: SSE trace entry (live)
        Agent->>Tools: execute (clamped, read-only)
        Tools->>Svc: query transactions
        Svc->>Fx: deterministic figures
        Fx-->>Tools: numbers (model never computes these)
        Tools-->>Agent: tool result
        Agent-->>AC: AgentEvent: ToolResult
        AC-->>UI: SSE trace entry
    end
    Note over Agent: Terminal submit_findings call = the report
    Agent->>Agent: schema-validate + verify each evidenceTxId exists
    Agent-->>AC: AgentEvent: Final(AnalystReport)
    AC-->>UI: SSE final report (ranked, evidence-cited)
    alt not configured / upstream error
        AC-->>UI: SSE typed, generic error (no stack / provider body)
    end
```

---

### Talking points

- **Deterministic money** — every figure flows `Tools → TransactionService → FxCalculator`; the model only orchestrates, ranks and narrates.
- **Hexagonal boundary** — `FxAnalystAgent` (core) depends only on ports (`LlmClient`, `AgentTool`, `AgentTraceSink`, `FindingsSubmission`); `OpenRouterLlmAdapter` and the SSE controller are adapters.
- **Security by design** — read-only clamped tools, single egress URL, bounded loop, schema-validated output, evidence-id verification, typed generic errors.
