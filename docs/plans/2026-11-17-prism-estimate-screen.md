# Prism — "Estimate at Swipe" Receipt Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an honest pre-purchase **estimate screen** and restructure the transaction detail into a two-section **receipt** — "what you see in Jeeves today" + "what Prism adds" — that decomposes the single blended rate into mid-market + hidden FX.

**Architecture:** New read-only `GET /api/estimate` reuses the existing `FxCalculator`/`MarkupPolicy`/`FxRateService` (one FX engine, no duplicated math). The frontend's "Simulate a purchase" button opens a `NewPurchaseModal` that live-queries the estimate and, on Authorize, fires the existing `POST /api/simulate`. The settled `TransactionDrawer` is restructured into the same Section-A / Section-B receipt layout.

**Tech Stack:** Java 21 + Spring Boot 4 (H2, JPA, validation), React 19 + TS + Vite + Tailwind v4 + Vitest + Testing Library.

**Spec:** `jeeves-challenge/DESIGN.md` §9 (Increment — "Estimate at swipe" screen), incl. §9.3 field definitions and §9.4 receipt A/B layout.

## Global Constraints
- Money math in `BigDecimal`, scale 2, `RoundingMode.HALF_EVEN` — reuse `FxCalculator`; never re-implement FX in TypeScript.
- No code comments (project preference) — keep log/exception/test strings.
- Estimate endpoint is **read-only** (no persistence, no new entity).
- Honest copy: the estimate "locks at settlement"; it is **not** a lockable quote.
- Conventional commits; one commit per task.
- Worked example (BRL, mid `0.185`, markup `300 bps`, stablecoin `50 bps`, amount `1800`): midMarketCost `333.00`, estimatedCost (amount charged) `342.99`, hiddenFxCost `9.99`, effectiveMarkupBps `300`, stablecoinCost `334.66`, stablecoinSavings `8.33`.

## Review Focus
- **amount ≤ 0 or missing** on `/api/estimate` → `400`, not a `500` or a bogus zero estimate (Task 2).
- **Unknown corridor** on `/api/estimate` → `400` (Task 2).
- **COP tiny-rate precision** (mid `0.00025`) — estimate must not round the hidden cost to `0.00` on a realistic amount (Task 1).
- **Empty / non-numeric amount typed in the modal** — no fetch fired, no crash, no `$NaN` (Task 4).
- **Drawer opened on a not-yet-settled tx** (null settled fields) — Section B shows dashes, never `$NaN`/throws (Task 5).

---

### Task 1: `EstimateDto` + `EstimateService` (backend estimate math)

**Files:**
- Create: `backend/src/main/java/com/prism/fx/EstimateDto.java`
- Create: `backend/src/main/java/com/prism/fx/EstimateService.java`
- Test: `backend/src/test/java/com/prism/fx/EstimateServiceTest.java`

**Interfaces:**
- Consumes: `FxRateService.midRate(Corridor)`, `MarkupPolicy.markupBps(Corridor)`, `MarkupPolicy.stablecoinFeeBps()`, `FxCalculator.compute(BigDecimal amount, BigDecimal midAuth, BigDecimal midSettle, int markupBps, int stablecoinFeeBps) -> FxBreakdown`, `Corridor.name()/country()`.
- Produces: `EstimateDto` record and `EstimateService.estimate(Corridor corridor, BigDecimal amount) -> EstimateDto`.
- **Field mapping (critical):** call `compute(amount, mid, mid, markupBps, stableBps)`. With `midAuth == midSettle`, read **`settledCost`** as the amount charged (it carries the markup); `midMarketCost`, `hiddenFxCost`, `effectiveMarkupBps`, `stablecoinCost`, `stablecoinSavings` map directly. Do **not** use `FxBreakdown.estimatedCost` (it is `amount × mid`, i.e. no markup — equals mid-market here).

`EstimateDto` fields (all `BigDecimal` unless noted):
`String corridor, String country, BigDecimal merchantAmount, BigDecimal midRate, BigDecimal midMarketCost, BigDecimal estimatedCost, BigDecimal estimatedHiddenFxCost, int effectiveMarkupBps, int stablecoinFeeBps, BigDecimal stablecoinCost, BigDecimal stablecoinSavings`.

- [ ] **Step 1: Write the failing test**

```java
@Test
void estimatesBrlDecomposition() {
    EstimateService service = new EstimateService(new FxRateService(), new MarkupPolicy());
    EstimateDto dto = service.estimate(Corridor.BRL, new BigDecimal("1800"));
    assertThat(dto.corridor()).isEqualTo("BRL");
    assertThat(dto.country()).isEqualTo("Brazil");
    assertThat(dto.midMarketCost()).isEqualByComparingTo("333.00");
    assertThat(dto.estimatedCost()).isEqualByComparingTo("342.99");
    assertThat(dto.estimatedHiddenFxCost()).isEqualByComparingTo("9.99");
    assertThat(dto.effectiveMarkupBps()).isEqualTo(300);
    assertThat(dto.stablecoinCost()).isEqualByComparingTo("334.66");
    assertThat(dto.stablecoinSavings()).isEqualByComparingTo("8.33");
}

@Test
void copEstimateKeepsHiddenCostNonZero() {
    EstimateService service = new EstimateService(new FxRateService(), new MarkupPolicy());
    EstimateDto dto = service.estimate(Corridor.COP, new BigDecimal("4000000"));
    assertThat(dto.estimatedHiddenFxCost()).isGreaterThan(new BigDecimal("30"));
}
```

- [ ] **Step 2: Run to verify it fails** — Run: `./gradlew test --tests 'com.prism.fx.EstimateServiceTest'` · Expected: FAIL (classes don't exist).
- [ ] **Step 3: Implement** `EstimateDto` (record) and `EstimateService` (`@Service`, constructor-injected `FxRateService` + `MarkupPolicy`) with `estimate(...)` per the field mapping above; set `merchantAmount` to `amount.setScale(2, HALF_EVEN)`.
- [ ] **Step 4: Run to verify it passes** — Run: `./gradlew test --tests 'com.prism.fx.EstimateServiceTest'` · Expected: PASS.
- [ ] **Step 5: Commit** — `feat(backend): add EstimateService FX decomposition for pre-purchase estimates`

---

### Task 2: `EstimateController` — `GET /api/estimate`

**Files:**
- Create: `backend/src/main/java/com/prism/api/EstimateController.java`
- Test: `backend/src/test/java/com/prism/api/EstimateControllerTest.java`

**Interfaces:**
- Consumes: `EstimateService.estimate(Corridor, BigDecimal)` (Task 1).
- Produces: `GET /api/estimate?corridor={C}&amount={A}` → `200` `EstimateDto` JSON; `400` on unknown corridor, and on amount missing / ≤ 0.
- Notes: unknown corridor enum query param → Spring `MethodArgumentTypeMismatchException` → `400` automatically. For amount ≤ 0 throw `ResponseStatusException(HttpStatus.BAD_REQUEST, ...)`. Mirror existing controller tests: `@SpringBootTest @AutoConfigureMockMvc` (Boot 4 package `org.springframework.boot.webmvc.test.autoconfigure`).

- [ ] **Step 1: Write the failing test**

```java
@Test
void returnsEstimateForValidRequest() throws Exception {
    mockMvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "1800"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estimatedCost").value(342.99))
        .andExpect(jsonPath("$.estimatedHiddenFxCost").value(9.99))
        .andExpect(jsonPath("$.effectiveMarkupBps").value(300))
        .andExpect(jsonPath("$.stablecoinCost").value(334.66));
}

@Test
void rejectsUnknownCorridor() throws Exception {
    mockMvc.perform(get("/api/estimate").param("corridor", "ZZZ").param("amount", "100"))
        .andExpect(status().isBadRequest());
}

@Test
void rejectsNonPositiveAmount() throws Exception {
    mockMvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "0"))
        .andExpect(status().isBadRequest());
    mockMvc.perform(get("/api/estimate").param("corridor", "BRL").param("amount", "-5"))
        .andExpect(status().isBadRequest());
}
```

- [ ] **Step 2: Run to verify it fails** — Run: `./gradlew test --tests 'com.prism.api.EstimateControllerTest'` · Expected: FAIL (no mapping).
- [ ] **Step 3: Implement** `EstimateController` (`@RestController @RequestMapping("/api")`), `@GetMapping("/estimate")` with `@RequestParam Corridor corridor, @RequestParam BigDecimal amount`; guard `amount == null || amount.signum() <= 0` → `ResponseStatusException(BAD_REQUEST)`; else return `service.estimate(corridor, amount)`.
- [ ] **Step 4: Run to verify it passes** — Run: `./gradlew test --tests 'com.prism.api.EstimateControllerTest'` · Expected: PASS.
- [ ] **Step 5: Commit** — `feat(backend): expose GET /api/estimate endpoint`

---

### Task 3: Frontend `Estimate` type + `getEstimate` client

**Files:**
- Modify: `frontend/src/api/types.ts`
- Modify: `frontend/src/api/client.ts`
- Test: `frontend/src/api/client.estimate.test.ts`

**Interfaces:**
- Produces: `interface Estimate` (numeric fields mirroring `EstimateDto`; `corridor: Corridor`), and `getEstimate(corridor: Corridor, amount: number) => Promise<Estimate>` hitting `/api/estimate?corridor=&amount=`.

- [ ] **Step 1: Write the failing test** (mock `fetch`, assert parse + URL):

```ts
it('getEstimate requests the endpoint and parses the body', async () => {
  const body = { corridor: 'BRL', country: 'Brazil', merchantAmount: 1800, midRate: 0.185,
    midMarketCost: 333, estimatedCost: 342.99, estimatedHiddenFxCost: 9.99,
    effectiveMarkupBps: 300, stablecoinFeeBps: 50, stablecoinCost: 334.66, stablecoinSavings: 8.33 }
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, json: async () => body })
  vi.stubGlobal('fetch', fetchMock)
  const out = await getEstimate('BRL', 1800)
  expect(fetchMock).toHaveBeenCalledWith('/api/estimate?corridor=BRL&amount=1800')
  expect(out.estimatedHiddenFxCost).toBe(9.99)
})
```

- [ ] **Step 2: Run to verify it fails** — Run: `npm test -- client.estimate` · Expected: FAIL (`getEstimate` undefined).
- [ ] **Step 3: Implement** `Estimate` in `types.ts` and `getEstimate` in `client.ts` (reuse `asJson`).
- [ ] **Step 4: Run to verify it passes** — Run: `npm test -- client.estimate` · Expected: PASS.
- [ ] **Step 5: Commit** — `feat(frontend): add getEstimate API client`

---

### Task 4: `NewPurchaseModal` (estimate screen) + wire the button

**Files:**
- Create: `frontend/src/components/NewPurchaseModal.tsx`
- Modify: `frontend/src/components/SimulateButton.tsx` (open the modal instead of firing a random simulate)
- Test: `frontend/src/components/NewPurchaseModal.test.tsx`

**Interfaces:**
- Consumes: `getEstimate` (Task 3), `simulate` (existing), `money`/`bps`/`rate` from `lib/format`.
- Produces: `NewPurchaseModal({ onClose }: { onClose: () => void })`. Inputs: corridor `<select>` (default `BRL`), merchant-amount number input, merchant text input. On a **valid positive** amount, debounced (~300 ms) `getEstimate` populates two sections:
  - **"What you see in Jeeves today"**: Merchant charge (`merchantAmount` + corridor), Est. amount charged (`estimatedCost`), Conversion rate (`estimatedCost / merchantAmount` via `rate`).
  - **"What Prism adds"** (highlighted): Mid-market reference (`midMarketCost`), **Est. hidden FX cost** (`estimatedHiddenFxCost`, accent), Effective markup (`effectiveMarkupBps` bps), Via stablecoin corridor (`stablecoinCost`), You'd save (`stablecoinSavings`), caveat line "This is an estimate; true FX cost locks at settlement (T+1–T+3)."
  - **Authorize purchase** → `simulate({ corridor, amount, merchant })` then `onClose()`. **Cancel** → `onClose()`. Empty/invalid amount → no fetch, Authorize disabled.
- `SimulateButton`: holds `open` state; button opens the modal; renders `<NewPurchaseModal onClose={...} />` when open.

- [ ] **Step 1: Write the failing test** (mock `../api/client`):

```tsx
vi.mock('../api/client', () => ({
  getEstimate: vi.fn().mockResolvedValue({ corridor: 'BRL', country: 'Brazil', merchantAmount: 1800,
    midRate: 0.185, midMarketCost: 333, estimatedCost: 342.99, estimatedHiddenFxCost: 9.99,
    effectiveMarkupBps: 300, stablecoinFeeBps: 50, stablecoinCost: 334.66, stablecoinSavings: 8.33 }),
  simulate: vi.fn().mockResolvedValue({}),
}))

it('shows both receipt sections and authorizes the purchase', async () => {
  const onClose = vi.fn()
  render(<NewPurchaseModal onClose={onClose} />)
  fireEvent.change(screen.getByLabelText(/amount/i), { target: { value: '1800' } })
  expect(await screen.findByText(/what you see/i)).toBeInTheDocument()
  expect(screen.getByText(/what prism adds/i)).toBeInTheDocument()
  expect(await screen.findByText(/9\.99/)).toBeInTheDocument()
  fireEvent.click(screen.getByRole('button', { name: /authorize/i }))
  await waitFor(() => expect(simulate).toHaveBeenCalledWith(
    expect.objectContaining({ corridor: 'BRL', amount: 1800 })))
  await waitFor(() => expect(onClose).toHaveBeenCalled())
})

it('does not fetch an estimate and disables Authorize when amount is empty', async () => {
  render(<NewPurchaseModal onClose={vi.fn()} />)
  // default amount is empty; advance past the debounce window
  await new Promise((r) => setTimeout(r, 350))
  expect(getEstimate).not.toHaveBeenCalled()
  expect(screen.getByRole('button', { name: /authorize/i })).toBeDisabled()
})
```

- [ ] **Step 2: Run to verify it fails** — Run: `npm test -- NewPurchaseModal` · Expected: FAIL (component missing).
- [ ] **Step 3: Implement** `NewPurchaseModal` (debounce via `setTimeout` in an effect keyed on corridor+amount; guard non-positive/NaN), add `rate(n)` to `lib/format.ts` (`\`$${n.toFixed(4)}\``-style per unit, dash on null), and update `SimulateButton` to open it.
- [ ] **Step 4: Run to verify it passes** — Run: `npm test -- NewPurchaseModal` · Expected: PASS.
- [ ] **Step 5: Commit** — `feat(frontend): add New Purchase estimate modal with receipt A/B sections`

---

### Task 5: Restructure `TransactionDrawer` into the A/B receipt layout

**Files:**
- Modify: `frontend/src/components/TransactionDrawer.tsx`
- Modify: `frontend/src/lib/format.ts` (if `rate` not already added in Task 4)
- Test: `frontend/src/components/TransactionDrawer.test.tsx`

**Interfaces:**
- Consumes: `Transaction` (existing), `money`/`bps`/`rate`.
- Produces: drawer with **Section A "What you see in Jeeves today"** (Merchant header; Date; Status; Merchant charge = `purchaseAmount` + corridor; Amount charged = `settledCost ?? estimatedCost`; Conversion rate = `(settledCost ?? estimatedCost) / purchaseAmount` via `rate`; `Card ••1234 · {employee}`) and **Section B "What Prism adds"** highlighted (Mid-market reference = `midMarketCost`; **Hidden FX cost** = `hiddenFxCost`, accent; Effective markup = `effectiveMarkupBps`; Stablecoin cost + You would save; settlement timeline). All money/rate via helpers so null → dash (no `$NaN`).

- [ ] **Step 1: Write the failing test**

```tsx
const settled: Transaction = { /* minimal SETTLED fixture: id, merchant:'iFood', corridor:'BRL',
  purchaseAmount:1800, status:'SETTLED', settledCost:342.89, estimatedCost:333, midMarketCost:333.10,
  hiddenFxCost:9.79, effectiveMarkupBps:294, stablecoinCost:334.66, stablecoinSavings:8.23,
  ...nullable timestamps/rates... } */ } as Transaction

it('renders the two receipt sections with the hidden FX cost', () => {
  render(<TransactionDrawer tx={settled} onClose={() => {}} />)
  expect(screen.getByText(/what you see/i)).toBeInTheDocument()
  expect(screen.getByText(/what prism adds/i)).toBeInTheDocument()
  expect(screen.getByText(/hidden fx cost/i)).toBeInTheDocument()
  expect(screen.getByText(/9\.79/)).toBeInTheDocument()
})

it('renders dashes, not NaN, for a not-yet-settled transaction', () => {
  const pending = { ...settled, status: 'AUTHORIZED', settledCost: null, midMarketCost: null,
    hiddenFxCost: null, effectiveMarkupBps: null, stablecoinCost: null, stablecoinSavings: null,
    settledAt: null } as Transaction
  render(<TransactionDrawer tx={pending} onClose={() => {}} />)
  expect(screen.queryByText(/NaN/)).not.toBeInTheDocument()
})
```

- [ ] **Step 2: Run to verify it fails** — Run: `npm test -- TransactionDrawer` · Expected: FAIL (headers absent).
- [ ] **Step 3: Implement** the A/B restructure (keep the existing `Row` + stablecoin emerald sub-box inside Section B).
- [ ] **Step 4: Run to verify it passes** — Run: `npm test -- TransactionDrawer` · Expected: PASS.
- [ ] **Step 5: Commit** — `feat(frontend): restructure transaction drawer into Jeeves-receipt + Prism-adds sections`

---

## Final verification (after Task 5)
- `./gradlew test` (backend, all) → BUILD SUCCESSFUL.
- `npm test` (frontend, all) → all pass.
- `npm run build` → clean.
- Manual: restart both servers, open `:5173`, click "Simulate a purchase" → modal shows the estimate with both sections → Authorize → row settles → open its drawer → both receipt sections render with the hidden-FX hero number.
