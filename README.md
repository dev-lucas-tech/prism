# Prism — FX Transparency for Corporate Card Spend

Prism makes the **true cost of every foreign-currency corporate-card transaction visible**: it
splits each settled amount back into its mid-market value and the hidden FX markup, aggregates the
monthly "FX leakage" a finance team actually pays, and shows what a stablecoin-corridor settlement
would have saved — revealing the real cost **live, at settlement**, the way it actually happens.

> Built for a vibe-coding challenge. The problem was chosen from real, recurring user complaints
> about FX/fee opacity in corporate-card products. Data here is synthetic; Prism is not affiliated
> with or connected to any real provider.

## The problem it solves

The most common complaint about multi-currency corporate cards is that **you can't see what FX you
actually paid**. There are two structural reasons:

1. **Timing.** On a foreign-currency purchase, the real cost in your funding currency is only locked
   at **settlement (T+1–T+3)** — not at swipe. You see an estimate, then a different final number.
2. **No decomposition.** The settled amount **bundles the mid-market rate and the markup into one
   figure**. Nothing itemizes `merchant amount × mid-market` vs. `what you were charged` = the
   **hidden FX cost**.

Prism attacks both: it **decomposes** every transaction against a mid-market benchmark, and it
models the **timing** by revealing the true cost live as a transaction moves
`authorized → settling → settled`. It also compares the card-network FX path to a **stablecoin
corridor**, where the markup collapses to a small corridor fee.

## What it does

- **Per-transaction decomposition** — merchant amount, mid-market cost, settled cost, markup (bps), and hidden FX cost.
- **FX leakage dashboard** — total hidden cost, effective markup, and stablecoin savings, with a by-corridor breakdown.
- **Live settlement feed** — simulate a purchase and watch it flip from *"Pending — estimated"* to *"Settled — true cost + hidden markup"* in real time over Server-Sent Events.
- **Detail drawer** — the full FX breakdown and the stablecoin-corridor alternative for any transaction.
- **AI FX Analyst (agentic)** — a bounded tool-calling loop where an LLM investigates recent spend over read-only tools and streams a live tool-trace plus ranked, evidence-cited savings findings. See below.

## Architecture

```
frontend (React + TS + Vite + Tailwind)        backend (Java 21 + Spring Boot)
  Dashboard ── GET /api/summary ───────────────►  TransactionController ─┐
            ── GET /api/transactions ──────────►                         ├─ TransactionService
            ── POST /api/simulate ─────────────►  SimulationController ──┤    └─ FxCalculator (core)
   LiveFeed ◄── SSE /api/stream ◄───────────────  SettlementSimulator ──┘    FxRateService / MarkupPolicy
                                                   (@Scheduled: authorized→settling→settled)   H2 (in-memory)
```

- **FX cost model** (`FxCalculator`) is pure and unit-tested — it is the defensible core.
- **SettlementSimulator** advances injected transactions through their lifecycle and broadcasts each
  change over **SSE**; the true settlement FX is computed only at the final step.
- **H2 in-memory** + a deterministic seed loader means it runs with **zero external setup**, and a
  **bundled mid-market rate snapshot** keeps the demo reproducible.

**Tech:** Java 21, Spring Boot 4 (web, data-jpa, validation), H2, Gradle, JUnit 5 + MockMvc ·
React 19, TypeScript, Vite, Tailwind v4, Recharts, Vitest + Testing Library.

## AI FX Analyst (agentic)

A backend **tool-calling agent** turns Prism's data into advice. Given a question (or the default
"where am I losing the most to FX?"), a strong LLM runs a bounded loop over three **read-only** tools —
`get_spend_summary` → `list_recent_transactions` → `get_transaction_breakdown` — then calls a terminal
`submit_findings` tool whose schema *is* the final report. The loop's tool-trace streams live to the UI
over SSE, and every dollar figure comes from Prism's deterministic `FxCalculator` (the model orchestrates
and narrates; it never invents numbers).

**Design notes (hexagonal / security):** the domain core (`FxAnalystAgent` + the `LlmClient`,
`AgentTool`, `AgentTraceSink`, `FindingsSubmission` ports) depends only on abstractions; OpenRouter, SSE,
and the tool→`TransactionService` bridges are adapters. The API key is read from the environment and never
logged; tool args are validated/clamped; the loop is capped (6 rounds) with timeouts; and every cited
transaction id is verified to exist before it reaches the UI.

**Enable it** — set your OpenRouter key and (optionally) a model, then run normally:
```sh
export OPENROUTER_API_KEY=sk-or-...        # required; without it the analyst returns a clear "not configured" state
export PRISM_AI_MODEL=anthropic/claude-sonnet-4.5   # optional; use a cheaper slug (e.g. openai/gpt-4o-mini) for dev/test
```
Config lives under `prism.ai.*` in `backend/src/main/resources/application.yaml` (model, base-url,
max-rounds, max-tokens, timeouts). The stream endpoint is `GET /api/analyst/stream?q=<optional question>`.
The rest of Prism runs fine without a key — only the analyst panel is gated.

## Prerequisites

- **Java 21** (`java -version`)
- **Node 20+** and npm

## Run it locally

Two processes. From the repo root:

**1) Backend** (port 8080):
```sh
cd backend
./gradlew bootRun
```

**2) Frontend** (port 5173, proxies `/api` → 8080):
```sh
cd frontend
npm install
npm run dev
```

Then open **http://localhost:5173**.

Or use the helper script from the repo root: `./run-dev.sh` (macOS/Linux) or `./run-dev.ps1` (Windows).

## Demo walkthrough (what to show)

1. The dashboard loads with seeded history — **FX leakage** and **stablecoin savings** tiles, and
   hidden-cost-by-corridor chart.
2. Click **"Simulate a purchase."** A row appears in the live feed as **Pending — est. $X**.
3. Within a few seconds it **flips to Settled** — revealing the true cost, the hidden FX, and the
   markup in bps. The leakage tiles tick up.
4. Click any row in the table to open the **decomposition drawer**, including the **stablecoin
   corridor alternative**.

## Tests

```sh
# backend
cd backend && ./gradlew test

# frontend
cd frontend && npm test
```

## Project layout

```
backend/   Spring Boot service (FX model, API, SSE, simulator, seed)
frontend/  React dashboard (KPIs, chart, table, drawer, live feed)
docs/      DESIGN.md (approved design) and the implementation plan
```
