# Prism — FX Transparency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A locally-run tool that decomposes corporate-card foreign-currency transactions against mid-market FX, reveals the hidden markup live as each transaction settles, and shows stablecoin-corridor savings.

**Architecture:** Spring Boot 3 backend (Java 21) exposes a REST API over H2-stored transactions plus an SSE stream; a settlement simulator advances transactions `AUTHORIZED → SETTLING → SETTLED`, computing true FX at settlement and broadcasting updates. A React + TypeScript (Vite + Tailwind) dashboard renders per-transaction decomposition, aggregate "FX leakage", a live feed, and a stablecoin-savings comparison.

**Tech Stack:** Java 21, Spring Boot 3.3.x (Web, Data JPA, Validation), H2, Gradle (Kotlin DSL), JUnit 5 + Spring Boot Test + MockMvc; React 18, TypeScript, Vite 5, Tailwind 3, Recharts, Vitest + React Testing Library.

**Spec:** `../../DESIGN.md` (repo copy of the approved design doc) — see also parent `jeeves-challenge/DESIGN.md`.

## Global Constraints
- Java **21**; Spring Boot **3.3.x**; Gradle Kotlin DSL. Backend port **8080**.
- Frontend: Node 20+, Vite dev server **5173**, proxying `/api` → `8080`.
- Runs locally, no external network at runtime; **FX rates are a bundled static snapshot** (deterministic demo). No deployment, no auth, no real Jeeves API.
- Money math uses `BigDecimal` with `RoundingMode.HALF_EVEN`, scale 2 for currency amounts, bps as integers.
- **FX snapshot (mid-market, units of USD per 1 unit of purchase currency):** `MXN=0.054`, `BRL=0.185`, `COP=0.00025`, `EUR=1.08`, `GBP=1.27`. Funding currency is `USD` for all seeded data.
- **Jeeves markup (bps over mid) per corridor:** `MXN=250`, `BRL=300`, `COP=350`, `EUR=120`, `GBP=120`.
- **Stablecoin corridor fee:** `50` bps (flat, all corridors).
- **Settlement drift:** settlement mid-rate = auth mid-rate × (1 + d), where `d` is deterministic per transaction in `[-0.005, +0.005]` (seeded from transaction id hash).
- Naming: product name **Prism**; never present mock data as real Jeeves data; no Jeeves logos/branding.

## FX cost model (authoritative — Task 2 implements, everything else consumes)
For amount `A` (purchase currency `P`), funding currency `F`, `midAuth`/`midSettle` = mid rates P→F:
- `markup = markupBps(P)/10_000`, `stableFee = 50/10_000`
- `estimatedCost   = A × midAuth`                         (naive mid-market expectation at swipe)
- `midMarketCost   = A × midSettle`                       (fair value at settlement)
- `settledCost     = A × midSettle × (1 + markup)`        (what the user is actually charged)
- `hiddenFxCost    = settledCost − midMarketCost`         (= A × midSettle × markup)
- `effectiveMarkupBps = round((settledCost / midMarketCost − 1) × 10_000)`
- `stablecoinCost  = A × midSettle × (1 + stableFee)`
- `stablecoinSavings = settledCost − stablecoinCost`      (= A × midSettle × (markup − stableFee))
- `surpriseVsEstimate = settledCost − estimatedCost`      (the "I expected X, paid Y" gap shown live)

## Review Focus
- **Zero / empty transaction set** → `/api/summary` must return zeroed aggregates (not divide-by-zero on effective markup). *(Task 5 test.)*
- **COP-scale amounts** (rate 0.00025, large integer amounts) → decomposition must not lose precision or round to 0. *(Task 2 test.)*
- **Transaction still AUTHORIZED** (not settled) → summary aggregates exclude it; settled-only fields are null/absent, not 0. *(Task 5 test.)*
- **SSE client disconnect** → emitter is removed, broadcast to remaining clients does not throw. *(Task 6 test.)*
- **Unknown currency / corridor** in a request to `/simulate` → 400 with a clear message, no 500. *(Task 6 test.)*

---

## Task 1: Backend scaffold + build

**Files:**
- Create: `prism/backend/build.gradle.kts`, `settings.gradle.kts`, `gradle` wrapper, `src/main/java/com/prism/PrismApplication.java`, `src/main/resources/application.yaml`
- Test: `src/test/java/com/prism/PrismApplicationTests.java`

**Interfaces:**
- Produces: a bootable Spring Boot app on `8080`; base package `com.prism`.

- [ ] **Step 1: Write the failing test** — `PrismApplicationTests.contextLoads()` (empty body; Spring context must start).
- [ ] **Step 2: Run** `./gradlew test` → FAIL (no app/deps yet).
- [ ] **Step 3:** Create Gradle build with deps: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `com.h2database:h2`, `spring-boot-starter-test`. Java toolchain 21. `application.yaml`: H2 in-memory, `jpa.hibernate.ddl-auto=create`, server port 8080. Minimal `PrismApplication` with `@SpringBootApplication`.
- [ ] **Step 4: Run** `./gradlew test` → PASS.
- [ ] **Step 5: Commit** `chore: scaffold Spring Boot 3 / Java 21 backend`.

---

## Task 2: FX cost model (the core — pure, no Spring)

**Files:**
- Create: `src/main/java/com/prism/fx/FxBreakdown.java` (record), `src/main/java/com/prism/fx/FxCalculator.java`
- Test: `src/test/java/com/prism/fx/FxCalculatorTest.java`

**Interfaces:**
- Produces:
  - `record FxBreakdown(BigDecimal estimatedCost, BigDecimal midMarketCost, BigDecimal settledCost, BigDecimal hiddenFxCost, int effectiveMarkupBps, BigDecimal stablecoinCost, BigDecimal stablecoinSavings, BigDecimal surpriseVsEstimate)`
  - `FxCalculator.compute(BigDecimal amount, BigDecimal midAuth, BigDecimal midSettle, int markupBps, int stablecoinFeeBps) -> FxBreakdown` (static; all money scale 2 HALF_EVEN).

- [ ] **Step 1: Write failing tests** in `FxCalculatorTest`:
  - `settledCost_includesMarkupOverSettlementMid`: `compute(1000, 0.054, 0.054, 250, 50)` → `settledCost == 55.35` (1000×0.054×1.025), `midMarketCost == 54.00`, `hiddenFxCost == 1.35`, `effectiveMarkupBps == 250`.
  - `stablecoinSavings_isMarkupMinusFee`: same inputs → `stablecoinCost == 54.27` (×1.005), `stablecoinSavings == 1.08`.
  - `surprise_capturesSettlementDrift`: `compute(1000, 0.054, 0.0543, 250, 50)` → `estimatedCost == 54.00`, `surpriseVsEstimate == settledCost − 54.00` (> hiddenFxCost because rate drifted up).
  - `copScale_doesNotRoundToZero`: `compute(5_000_000, 0.00025, 0.00025, 350, 50)` → `hiddenFxCost == 43.75`, not 0.
  - `zeroMarkup_hasNoHiddenCost`: markupBps 0 → `hiddenFxCost == 0.00`, `effectiveMarkupBps == 0`.
- [ ] **Step 2: Run** `./gradlew test --tests FxCalculatorTest` → FAIL (classes absent).
- [ ] **Step 3:** Implement `FxBreakdown` and `FxCalculator.compute(...)` per the FX cost model above. Pure `BigDecimal` math, scale 2 HALF_EVEN; bps via integer rounding.
- [ ] **Step 4: Run** tests → PASS.
- [ ] **Step 5: Commit** `feat: FX decomposition cost model`.

---

## Task 3: FX rate snapshot + markup policy

**Files:**
- Create: `src/main/java/com/prism/fx/FxRateService.java`, `src/main/java/com/prism/fx/MarkupPolicy.java`, `src/main/java/com/prism/fx/Corridor.java` (enum `MXN,BRL,COP,EUR,GBP` with its USD mid-rate)
- Test: `src/test/java/com/prism/fx/FxRateServiceTest.java`, `MarkupPolicyTest.java`

**Interfaces:**
- Produces:
  - `FxRateService.midRate(Corridor c) -> BigDecimal` (auth snapshot) and `midRateAtSettlement(Corridor c, String txId) -> BigDecimal` (applies deterministic drift from `txId` hash in [-0.005, +0.005]).
  - `MarkupPolicy.markupBps(Corridor c) -> int`, `MarkupPolicy.stablecoinFeeBps() -> int` (== 50).

- [ ] **Step 1: Write failing tests:**
  - `midRate_matchesSnapshot`: `midRate(MXN) == 0.054`, `midRate(EUR) == 1.08`.
  - `settlementDrift_isDeterministic`: `midRateAtSettlement(MXN,"tx-1")` equals itself across calls and lies within ±0.5% of 0.054.
  - `markupBps_perCorridor`: `markupBps(COP) == 350`, `markupBps(GBP) == 120`; `stablecoinFeeBps() == 50`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement enum + services from Global Constraints values. Drift = `midRate × (1 + d)` where `d = ((txId.hashCode() mod 1001) − 500)/100_000.0`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: FX rate snapshot and corridor markup policy`.

---

## Task 4: Transaction entity + repository + seed data

**Files:**
- Create: `src/main/java/com/prism/transaction/Transaction.java` (JPA entity), `TransactionStatus.java` (enum `AUTHORIZED,SETTLING,SETTLED`), `TransactionRepository.java` (Spring Data), `src/main/java/com/prism/seed/SeedDataLoader.java` (`ApplicationRunner`)
- Test: `src/test/java/com/prism/transaction/TransactionRepositoryTest.java` (`@DataJpaTest`)

**Interfaces:**
- Produces:
  - `Transaction` fields: `id (String)`, `employee`, `merchant`, `merchantCountry`, `corridor (Corridor)`, `purchaseAmount (BigDecimal)`, `fundingCurrency (String="USD")`, `status`, `authorizedAt (Instant)`, `settledAt (Instant nullable)`, `midAuthRate`, `midSettleRate (nullable)`. (FX breakdown is computed on read via Task 5, not stored, except `midAuthRate`.)
  - `TransactionRepository extends JpaRepository<Transaction,String>` + `findAllByOrderByAuthorizedAtDesc()`.
  - On startup `SeedDataLoader` inserts ~40 SETTLED transactions spread across all five corridors with realistic merchants/amounts, authorized/ settled timestamps over the last 30 days.

- [ ] **Step 1: Write failing test** `seedData_loadsTransactionsAcrossAllCorridors`: after context start, `repository.count() >= 40` and every `Corridor` value appears at least twice.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement entity/enum/repo and `SeedDataLoader` (uses `FxRateService` for `midAuthRate`/`midSettleRate`; deterministic seed list).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: transaction model, repository, and seed data`.

---

## Task 5: Query API (list, detail, summary)

**Files:**
- Create: `src/main/java/com/prism/transaction/TransactionService.java`, `TransactionDto.java`, `SummaryDto.java`, `CorridorBreakdownDto.java`, `src/main/java/com/prism/api/TransactionController.java`
- Test: `src/test/java/com/prism/api/TransactionControllerTest.java` (`@SpringBootTest` + MockMvc), `TransactionServiceTest.java`

**Interfaces:**
- Consumes: `FxCalculator` (Task 2), `FxRateService`/`MarkupPolicy` (Task 3), `TransactionRepository` (Task 4).
- Produces:
  - `TransactionService.toDto(Transaction) -> TransactionDto` (entity + `FxBreakdown` for SETTLED; for AUTHORIZED only `estimatedCost` populated, settled fields null).
  - `TransactionService.summary() -> SummaryDto` over SETTLED only: `totalSpend`, `totalHiddenFxCost`, `effectiveMarkupBps`, `totalStablecoinSavings`, `List<CorridorBreakdownDto>` (per corridor: count, spend, hiddenFxCost, stablecoinSavings).
  - Endpoints: `GET /api/transactions`, `GET /api/transactions/{id}` (404 if absent), `GET /api/summary`.

- [ ] **Step 1: Write failing tests:**
  - `getTransactions_returnsDecomposedList`: GET `/api/transactions` → 200, JSON array non-empty, a SETTLED item has `hiddenFxCost` and `effectiveMarkupBps` fields.
  - `summary_aggregatesHiddenCostAndSavings`: GET `/api/summary` → `totalHiddenFxCost > 0`, `totalStablecoinSavings > 0`, `corridorBreakdown` length 5.
  - `summary_emptyRepo_returnsZeroedAggregates` (service test with empty repo): `totalSpend == 0`, `effectiveMarkupBps == 0`, no exception.
  - `summary_excludesAuthorizedTransactions` (service test): an AUTHORIZED tx does not contribute to totals.
  - `getTransaction_unknownId_returns404`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement DTOs, service (guard divide-by-zero for effective markup), controller.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: transaction query API with FX decomposition and summary`.

---

## Task 6: Live settlement — SSE stream + simulator + /simulate

**Files:**
- Create: `src/main/java/com/prism/live/SseService.java`, `SettlementSimulator.java`, `src/main/java/com/prism/api/SimulationController.java`, `SimulateRequest.java`
- Modify: `TransactionController` or new `StreamController` for `GET /api/stream`
- Test: `src/test/java/com/prism/live/SseServiceTest.java`, `SettlementSimulatorTest.java`, `src/test/java/com/prism/api/SimulationControllerTest.java`

**Interfaces:**
- Consumes: Tasks 2–5.
- Produces:
  - `SseService.subscribe() -> SseEmitter`; `SseService.broadcast(String event, Object payload)` (removes dead emitters, never throws to caller).
  - `SettlementSimulator.createLiveTransaction(SimulateRequest) -> TransactionDto` (status AUTHORIZED, broadcasts `tx.created`); `advance(String txId)` moves AUTHORIZED→SETTLING→SETTLED, sets `midSettleRate`/`settledAt`, broadcasts `tx.updated` each transition. A `@Scheduled` driver advances in-flight transactions on a short cadence (e.g. 2s/step) for the demo.
  - `SimulateRequest(Corridor corridor, BigDecimal amount, String merchant)` with `@NotNull`/`@Positive` validation.
  - Endpoints: `GET /api/stream` (SSE), `POST /api/simulate` (201 + the created `TransactionDto`; 400 on invalid/unknown corridor).

- [ ] **Step 1: Write failing tests:**
  - `broadcast_afterEmitterCompletes_doesNotThrow` (SseServiceTest): complete one emitter, broadcast → no exception, dead emitter pruned.
  - `advance_movesThroughStatesAndComputesSettledFx` (SettlementSimulatorTest): create → advance twice → status SETTLED, `settledCost > midMarketCost`, `hiddenFxCost > 0`.
  - `simulate_invalidCorridor_returns400` (SimulationControllerTest): POST malformed/unknown corridor → 400, not 500.
  - `simulate_validRequest_returns201Authorized`: POST valid → 201, body status `AUTHORIZED`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement SSE service (thread-safe emitter list), simulator (reuses `FxCalculator`), controllers; register `@EnableScheduling`.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: live settlement simulator with SSE stream and /simulate`.

---

## Task 7: Frontend scaffold

**Files:**
- Create: `prism/frontend/` Vite React-TS project: `package.json`, `vite.config.ts` (proxy `/api`→`http://localhost:8080`), `tailwind.config.js`, `postcss.config.js`, `src/main.tsx`, `src/App.tsx`, `index.html`, `src/index.css` (Tailwind directives)
- Test: `src/App.test.tsx` (Vitest + RTL), `vitest.config.ts`, `src/test/setup.ts`

**Interfaces:**
- Produces: a running Vite app rendering an `<App>` shell with a header "Prism — FX Transparency".

- [ ] **Step 1: Write failing test** `App_rendersHeader`: render `<App/>`, expect text `/Prism/i`.
- [ ] **Step 2: Run** `npm test` → FAIL.
- [ ] **Step 3:** Scaffold Vite React-TS + Tailwind + Vitest/RTL; proxy config; `App` header shell.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `chore: scaffold React + TS + Vite + Tailwind frontend`.

---

## Task 8: Types, API client, and formatting utils

**Files:**
- Create: `src/api/types.ts`, `src/api/client.ts`, `src/lib/format.ts`
- Test: `src/lib/format.test.ts`, `src/api/client.test.ts`

**Interfaces:**
- Produces:
  - `types.ts`: `Transaction`, `Summary`, `CorridorBreakdown`, `TransactionStatus` mirroring backend DTOs.
  - `client.ts`: `getTransactions()`, `getSummary()`, `simulate(req)`; `openStream(onEvent)` wrapping `EventSource('/api/stream')`.
  - `format.ts`: `money(n, ccy='USD')`, `bps(n)` → e.g. `"250 bps"`, `pct(n)`.

- [ ] **Step 1: Write failing tests:** `money_formatsUsd`: `money(55.35)` → `"$55.35"`; `bps_formats`: `bps(250)` → `"250 bps"`; `getSummary_parsesJson` (mock fetch) returns typed summary.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement utils + client (typed `fetch`, `EventSource` wrapper).
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: frontend types, API client, formatting utils`.

---

## Task 9: Dashboard — KPI tiles, summary, corridor chart

**Files:**
- Create: `src/components/KpiTiles.tsx`, `src/components/CorridorChart.tsx`, `src/pages/Dashboard.tsx`
- Test: `src/components/KpiTiles.test.tsx`

**Interfaces:**
- Consumes: `getSummary` (Task 8), Recharts.
- Produces: `KpiTiles({summary})` showing Total Spend, **FX Leakage** (hidden cost), Effective Markup (bps), **Stablecoin Savings**; `CorridorChart({breakdown})` bar chart of hidden cost by corridor; `Dashboard` fetches summary on mount.

- [ ] **Step 1: Write failing test** `KpiTiles_showsLeakageAndSavings`: given a summary fixture, renders the hidden-cost and stablecoin-savings values via `money`.
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement tiles (Tailwind cards), Recharts bar chart, Dashboard data fetch.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: dashboard KPI tiles and corridor chart`.

---

## Task 10: Transaction table + detail drawer

**Files:**
- Create: `src/components/TransactionTable.tsx`, `src/components/TransactionDrawer.tsx`
- Modify: `src/pages/Dashboard.tsx` (embed table; open drawer on row click)
- Test: `src/components/TransactionTable.test.tsx`, `TransactionDrawer.test.tsx`

**Interfaces:**
- Consumes: `getTransactions` (Task 8), `types`.
- Produces: `TransactionTable({transactions,onSelect})` with columns merchant, corridor, purchase amount, settled cost, **markup bps**, **hidden FX**; `TransactionDrawer({tx})` full §(FX model) breakdown + authorized→settled timeline + stablecoin alternative.

- [ ] **Step 1: Write failing tests:** `table_rendersHiddenFxColumn` (a settled row shows its hidden-cost value); `drawer_showsStablecoinAlternative` (drawer renders stablecoin cost + savings).
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement table + drawer; wire selection in Dashboard.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: transaction table and decomposition drawer`.

---

## Task 11: Live feed (SSE) + simulate button + pending→settled flip

**Files:**
- Create: `src/hooks/useLiveTransactions.ts`, `src/components/LiveFeed.tsx`, `src/components/SimulateButton.tsx`
- Modify: `src/pages/Dashboard.tsx`
- Test: `src/hooks/useLiveTransactions.test.ts`, `src/components/LiveFeed.test.tsx`

**Interfaces:**
- Consumes: `openStream`, `simulate` (Task 8).
- Produces: `useLiveTransactions()` → `{live: Transaction[]}` that applies `tx.created` (prepend as AUTHORIZED) and `tx.updated` (merge by id, update status/FX); `LiveFeed({live})` renders each item as *"Pending — est. {money}"* while AUTHORIZED and *"Settled — {settledCost} · hidden {hiddenFxCost} · {bps}"* once SETTLED (visual flip/highlight on transition); `SimulateButton` calls `simulate` with a random corridor/amount.

- [ ] **Step 1: Write failing tests:** `hook_mergesUpdateById` (feed a `tx.created` then `tx.updated` for same id → one item, status SETTLED, FX populated); `liveFeed_showsPendingThenSettledLabels` (render AUTHORIZED item shows "Pending"; SETTLED shows "hidden").
- [ ] **Step 2: Run** → FAIL.
- [ ] **Step 3:** Implement hook (mock `EventSource` in test), `LiveFeed`, `SimulateButton`; embed in Dashboard.
- [ ] **Step 4: Run** → PASS.
- [ ] **Step 5: Commit** `feat: live settlement feed with pending→settled reveal`.

---

## Task 12: README, run scripts, and final wiring

**Files:**
- Create: `prism/README.md`, `prism/run-dev.sh` / `run-dev.ps1`, copy `DESIGN.md` → `prism/docs/DESIGN.md`
- Modify: root `.gitignore`

**Interfaces:**
- Produces: one-command-per-process local run instructions; README covering problem, the FX root cause, how to run backend + frontend, and the demo script (simulate a purchase → watch it settle → open drawer → read leakage + stablecoin savings).

- [ ] **Step 1:** Write `README.md` (problem → root cause → architecture → prerequisites → run backend (`./gradlew bootRun`) → run frontend (`npm install && npm run dev`) → demo steps → test commands).
- [ ] **Step 2:** Add run scripts (both sh and ps1) and `.gitignore` (gradle `build/`, `node_modules/`, H2 files).
- [ ] **Step 3:** Manual smoke: start both, load dashboard, click Simulate, confirm live settle + aggregates update.
- [ ] **Step 4:** Verify full suites: `./gradlew test` and `npm test` both green.
- [ ] **Step 5: Commit** `docs: README, run scripts, and demo guide`.

---

## Self-review notes
- **Spec coverage:** decomposition (T2/T5), aggregates/leakage (T5), live settlement reveal (T6/T11), stablecoin savings (T2/T5/T10), local run (T1/T7/T12), seed corridors MXN/BRL/COP/EUR/GBP (Global Constraints + T4). ✔
- **Review Focus** inputs each pinned to a task (empty repo→T5, COP scale→T2, authorized-excluded→T5, SSE disconnect→T6, unknown corridor→T6). ✔
- **Type consistency:** `FxBreakdown` fields (T2) are the DTO/UI fields reused verbatim in T5/T8/T10/T11; `Corridor` enum shared T3/T4/T6. ✔
- **Proportion:** interfaces + test assertions, bodies left to implementer. ✔
