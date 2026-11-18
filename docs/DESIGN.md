# Design Doc — "Prism": FX Transparency for Corporate Card Spend

**Author:** Lucas Alves Teixeira (with Claude Code)
**Date:** 2026-11-17
**Context:** Jeeves Stage 2 — Vibe Coding Challenge (Senior Software Engineer)
**Status:** ✅ Built & delivered (base dashboard + estimate-at-swipe + **agentic FX analyst**, §9)

> Working name **Prism** — a prism splits one beam of white light into its spectrum, exactly
> what this tool does to a single "settled amount": split it back into mid-market rate + hidden
> markup. Neutral, non-Jeeves branding (we build the concept, not an impersonation).
> Alternatives if you dislike it: *ClearSpread*, *TrueFX*, *Daylight*. — _your call_

---

## 1. Intent & why

Jeeves's most repeated user complaint is **FX / fee opacity**: businesses can't see the true cost
of a foreign-currency card transaction. Prism is a focused finance-admin tool that makes that cost
**visible and itemized**, and shows what a **stablecoin-corridor** settlement (Jeeves's own Bridge
infrastructure) would have saved.

**Why it's opaque in the first place** (the thesis the whole build expresses):
1. **Timing** — the real cost in the funding currency only locks at **settlement (T+1–T+3)**, not at
   swipe. Users see an estimate, then a different final number.
2. **No decomposition** — the settled amount bundles mid-market rate + markup into one figure; nothing
   itemizes `merchant amount × mid-market` vs. `charged` = **hidden FX cost**.

Prism attacks both: it **decomposes** every transaction against a mid-market benchmark, and it models
the **timing** by revealing the true cost live as a transaction moves `authorized → settling → settled`.

## 2. Goals / non-goals / anti-goals

**Goals**
- Per-transaction FX decomposition: merchant amount, mid-market cost, applied rate, **markup (bps)**, **hidden FX cost**.
- Aggregate **"FX leakage"** for a controller: total hidden cost, effective markup, breakdown by corridor.
- **Live settlement reveal** via SSE: transaction lands as "pending, estimated", flips to "settled, true cost revealed".
- **Stablecoin-corridor savings**: what each transaction / the period would cost settled via stablecoin vs. card FX.
- Runs locally with one clear README; demoable in a 5-minute video.

**Non-goals** (explicitly out of scope for the time box)
- Real Jeeves API integration (none is public), real card-network authorization, real bank data.
- Auth / login / multi-tenant / RBAC.
- Durable persistence beyond in-memory H2; accounting/ERP export; deployment/hosting.

**Anti-goals** (would make it a failure even if it "works")
- Impersonating Jeeves branding or presenting mock numbers as real Jeeves data.
- A sprawling half-built platform (the brief warns against "a huge problem solved halfway").
- A demo that depends on a live external API and can break on camera.

## 3. Constraints
- ~2.5 days, solo + AI assistance. Must run locally; no deploy.
- Deterministic demo (bundled FX snapshot, seeded data).
- Two processes: Spring Boot backend (`:8080`) + Vite dev server (`:5173`, proxying `/api`).

## 4. Approaches considered
- **A — Static transparency dashboard.** Decomposition + aggregates over seeded settled transactions, no live updates. Safe, fast, but ignores the timing root cause and demos flat.
- **B — Transparency + simulated real-time settlement.** *(CHOSEN)* A, plus a settlement simulator that pushes `authorized → settling → settled` over SSE, revealing true FX at settlement. Strongest story; real-time status *is* the transparency mechanism. Degrades gracefully to A if the live piece runs long.
- **C — Full platform.** Auth, multi-company, card-network sim, ERP export, Postgres. Rejected: too big for the time box; violates the "focused problem solved well" guidance.

**Chosen: B.**

## 5. The cost model (the engineering core — must be defensible)

For a purchase of amount `A` in currency `P`, funding currency `F`, mid-market rate `m` (P→F):
- `midMarketCost = A × m`
- Jeeves applied rate = `m × (1 + markup)`, where `markup` is a per-corridor spread (LATAM wider than EUR/GBP).
  - `settledCost = A × m × (1 + markup)` — models bundled network rate + Jeeves FX fee.
  - `hiddenFxCost = settledCost − midMarketCost = A × m × markup`
  - `markupBps = markup × 10_000`
- Stablecoin corridor: `stablecoinCost = A × m × (1 + c)`, small corridor fee `c` (e.g. 20–50 bps).
  - `stablecoinSavings = settledCost − stablecoinCost = A × m × (markup − c)`

**Timing split (the live reveal):**
- At **authorization** we know only `midRateAtAuth` (drifts slightly from settlement) → show an **estimated range**:
  `estCost ≈ A × midRateAtAuth × (1 + expectedMarkupBand)`.
- At **settlement** the actual `midRateAtSettlement` + true `markup` are applied → reveal `settledCost`,
  `hiddenFxCost`, `markupBps`. The gap between estimate and settled *is* the "surprise" users complain about.

## 6. Architecture (decided)

**Backend — Java 21 + Spring Boot 3**
- `Transaction` entity (H2 + Spring Data JPA): employee, merchant, merchantCountry, purchaseCurrency, purchaseAmount, fundingCurrency, status, authorizedAt, settledAt, + FX fields from §5.
- `FxRateService` — bundled **mid-market rate snapshot** (static map of pairs) with tiny deterministic drift between auth and settlement.
- `MarkupPolicy` — per-corridor markup bps + stablecoin corridor fee (config/constants).
- `SettlementSimulator` — scheduled component advancing `AUTHORIZED → SETTLING → SETTLED` over a short demo timeline; computes settled FX; emits an event per transition.
- `SseService` — holds `SseEmitter`s; broadcasts transaction create/update events.
- REST API:
  - `GET /api/transactions` — list with decomposition
  - `GET /api/transactions/{id}` — detail
  - `GET /api/summary` — aggregates (leakage, effective markup, stablecoin savings, by corridor)
  - `GET /api/stream` — **SSE** live feed
  - `POST /api/simulate` — inject a live transaction (demo control)
- `SeedDataLoader` — realistic historical SETTLED set (for aggregates) across corridors.

**Frontend — React + TS + Vite + Tailwind**
- **Dashboard**: KPI tiles (total spend, **FX leakage**, effective markup bps, **stablecoin savings**); bar chart of hidden cost by corridor (Recharts); **live feed** (EventSource/SSE) where transactions appear "Pending — est. $X±" and flip to "Settled — $Y · hidden $Z · NNN bps"; transaction table with decomposition columns.
- **Detail drawer**: full §5 breakdown + authorized→settled timeline + stablecoin alternative.
- **"Simulate a purchase"** button → `POST /api/simulate` for the live on-camera moment.

**Seed corridors:** USD-funded cards spending in **MXN, BRL, COP, EUR, GBP** (Jeeves's real issuing markets). Markup wider for LATAM corridors, tighter for EUR/GBP — realistic and makes the stablecoin savings story vivid for LATAM.

## 7. Builder decisions (resolved in the shipped build)
Open at design time, now settled:
- **Markup per corridor + stablecoin fee** — `MarkupPolicy` (wider LATAM spreads, tighter EUR/GBP).
- **Settlement timing** — seconds-scale `@Scheduled` advance for an on-camera reveal.
- **Seed dataset** — realistic settled history across corridors in `SeedDataLoader`.
- **Charts** — Recharts (`CorridorChart`).
- **README + run scripts** — `README.md` + `run-dev.sh` / `run-dev.ps1`.

## 8. Deliverable mapping (so nothing is missed)
- **Code + README** → this repo.
- **~5-min video** → camera + screen; demo centerpiece = the live settlement reveal.
- **1-page write-up** → `docs/WRITEUP.md`.


## 9. Agentic FX Analyst (the AI feature)

Prism's base feature *shows* hidden FX; the analyst makes it *actionable*. It is a **real multi-step
tool-calling agent**: given a question (default "where am I losing most to FX?"), a model runs a bounded
loop over read-only tools and ends by calling a terminal `submit_findings` tool whose JSON schema *is*
the final report. The loop's tool-trace streams live to the UI over SSE.

**Why this, and why it's defensible:**
- **Deterministic money.** Every figure comes from the existing `FxCalculator` via tools — the model
  orchestrates and narrates but never computes a number. This protects the transparency thesis: the AI
  can't contradict Prism's own math. (A single-shot "advisor" prompt was considered and rejected as a
  weaker engineering story; a chatbot was rejected as cliché and low-signal.)
- **Terminal tool, not `response_format`.** Mixing OpenAI `tools` with `response_format: json_schema`
  conflicts on several providers, so the agent terminates when it calls `submit_findings`. Its last tool
  call *is* the report — clean to show on camera and provider-portable.

**Architecture — hexagonal (ports & adapters).** The domain core (`FxAnalystAgent`, the loop, `AgentEvent`,
`AnalystReport`) depends only on ports: `LlmClient`, `AgentTool`/`AgentToolRegistry`, `AgentTraceSink`,
`FindingsSubmission`. Adapters supply all I/O — `OpenRouterLlmAdapter` (RestClient), `SseTraceAdapter`
(per-request `SseEmitter`), and the three tool bridges onto `TransactionService`. The core is unit-tested
against a fake `LlmClient`; SOLID throughout (e.g. OCP — new tools plug into the registry without touching
the loop; DIP — the domain knows only abstractions).

**Tools (read-only).** `get_spend_summary` (entry point) → `list_recent_transactions(limit≤30, corridor?,
sinceDays?)` → `get_transaction_breakdown(id)`; plus the terminal `submit_findings`.

**Security by design.** Key from env only, never logged or streamed; read-only allowlisted tool surface
(no SQL, no write path → prompt injection has no action surface); tool args validated/clamped; a single
fixed egress URL (no SSRF); a bounded loop (6 rounds) with timeouts and retry/backoff on 429; the final
report is schema-validated and every cited `evidenceTxId` is verified against the repository before the UI
renders it; errors are typed and generic (no stack traces, no provider bodies).

**Config.** `prism.ai.*` in `application.properties`; default model `openai/gpt-4o-mini` (cheap, reliable
tool-calling), swappable (e.g. `anthropic/claude-sonnet-4.5`). `GET /api/analyst/stream?q=` streams a run;
`GET /api/analyst/status` reports whether a key is configured so the UI pre-disables the panel.

---

> **Implementation note:** built on Spring Boot 4.1 / Java 21 (Boot 3.3.x from this doc was retired from Spring Initializr in this environment; the web/JPA/validation/SSE APIs used are unchanged). Note Boot 4.1 ships **Jackson 3** (`tools.jackson.*`). Product name kept as **Prism**.
