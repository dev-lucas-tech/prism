# Prism — Write-up

**Prism** makes the hidden FX cost of corporate-card spend visible, and then makes it **actionable**
with an AI analyst. Built for the Jeeves Stage-2 challenge. (Synthetic data; not affiliated with any provider.)

## The problem
The most repeated complaint about multi-currency corporate cards is that **you can't see the FX you
actually paid**. Two structural reasons: (1) **timing** — the real cost in your funding currency only
locks at settlement (T+1–T+3), not at swipe; and (2) **no decomposition** — the settled amount bundles
the mid-market rate and the markup into one number, so nothing itemizes `merchant × mid-market` vs.
`what you were charged` = the **hidden FX cost**. I picked this because it's a real, recurring pain in
Jeeves's own domain, and it's scopeable: a focused feature, not a platform.

## The approach
A small Spring Boot (Java 21) + React app. The engineering core is a pure, unit-tested `FxCalculator`
that decomposes each transaction (mid-market, hidden cost, effective markup in bps) and compares the card
path to a **stablecoin corridor**. A settlement simulator streams `authorized → settling → settled` over
SSE so the "timing" cause is visible live. On top of that sits the standout feature: an **agentic FX
analyst** — a real multi-step tool-calling loop where a model investigates recent spend over read-only
tools (`get_spend_summary` → `list_recent_transactions` → `get_transaction_breakdown`) and ends by calling
a terminal `submit_findings` tool whose schema **is** the report. Its tool-trace streams live to the UI.

## Key decisions & trade-offs
- **The AI never computes a number.** Every figure comes from the deterministic `FxCalculator` via tools;
  the model only orchestrates, ranks and narrates, and each cited transaction id is verified to exist.
  This keeps the AI from ever contradicting Prism's own math — the whole point is transparency.
- **A real tool-calling loop, not a single-shot prompt or a chatbot.** The loop is the stronger
  engineering and product story; the risk (flaky tool-calling on free models) is removed by using a
  capable paid model. Trade-off: a live demo depends on an external API, mitigated by graceful,
  typed error states and a bounded (6-round) loop.
- **Hexagonal architecture + security by design.** The domain core depends only on ports (`LlmClient`,
  `AgentTool`, `AgentTraceSink`, `FindingsSubmission`); OpenRouter/SSE/DB are adapters, so the core is
  tested against a fake LLM. Key from env only (never logged), read-only allowlisted tools (no SQL/write
  path), clamped args, single egress URL, schema-validated output, timeouts and retry/backoff.
- **Terminal tool over `response_format`.** Mixing `tools` with `json_schema` output conflicts on several
  providers, so termination is an explicit `submit_findings` call — portable and demo-legible.
- **Deterministic demo.** In-memory H2 + a seeded dataset and bundled rate snapshot: runs with zero
  external setup and can't drift on camera.

## How I used AI tools
Two layers. **As a build assistant:** I drove the work with Claude Code through an explicit
spec → design → plan → test-driven implementation → review loop — each backend/frontend unit was written
test-first (RED→GREEN) with the design decisions recorded as I went. **As a product capability:** the
analyst itself is the AI feature, reusing an OpenRouter integration pattern I'd built before, ported to
Java with structured I/O and resilience.

## What I'd do next
A config/health probe already pre-disables the UI without a key; next I'd add per-run cost/token metrics,
cache the summary tool, let findings deep-link into the transaction drawer, and add a thin "apply this
saving" action that routes a corridor through the stablecoin path.
