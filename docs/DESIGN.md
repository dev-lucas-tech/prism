# Design Doc — "Prism": FX Transparency for Corporate Card Spend

**Author:** Lucas Alves Teixeira (with Claude Code)
**Date:** 2026-11-17
**Context:** Jeeves Stage 2 — Vibe Coding Challenge (Senior Software Engineer)
**Status:** ⏳ Awaiting approval (design gate)

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

## 7. What's left to the builder
- Exact markup bps per corridor and stablecoin fee (pick realistic, cite reasoning in write-up).
- Settlement simulation timing (seconds-scale for demo).
- The seed dataset (size, merchants, spread of corridors) to make aggregates look real.
- Chart library confirmation (Recharts) and visual polish (secondary — "polish matters less than clarity").
- README + run scripts.

## 8. Deliverable mapping (so nothing is missed)
- **Code + README** → this repo.
- **~5-min video** → beats tracked in `VIDEO-NARRATIVE.md`; demo centerpiece = live settlement reveal.
- **1-page write-up** → derived from `PROGRESS.md` (§2 approach, §4 decisions, §6 references) + AI-usage log.


---

> **Implementation note:** built on Spring Boot 4.1 / Java 21 (Boot 3.3.x from this doc was retired from Spring Initializr in this environment; the web/JPA/validation/SSE APIs used are unchanged). Product name kept as **Prism**.
