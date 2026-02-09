---
name: test-scenario-planner
description: Generates comprehensive test scenario matrices for Quarkus modules, covering happy paths, edge cases, partial failures, and concurrency. Use when analyzing requirements, planning tests, or before writing test code.
---

# Test Scenario Planner

This skill guides the creation of detailed, high-coverage test plans for Quarkus components, ensuring no critical edge case or race condition is overlooked. Test planning is also a **design review** — bugs discovered during planning are as valuable as the scenarios themselves.

## When to Use

- **Before writing code**: To clarify requirements and edge cases (TDD logic).
- **Before writing tests**: To ensure all scenarios are covered before implementation.
- **When analyzing bugs**: To map out failure scenarios and reproduction steps.
- Triggered by: "plan tests", "create test scenarios", "analyze coverage", "test matrix".

## Related Skills

- **`quarkus-test-generator`**: Converts the scenario matrix from this skill into Java test code (Storm, Cliff, Parameterized patterns).
- **`create-e2e-tests`**: For HTTP/REST endpoint contract testing with RestAssured.

## Workflow

1. **Analyze the Code/Requirement**: Read each target class, noting public methods and dependencies.
2. **Decompose by Component**: Create a separate scenario table per class/method. Link to source files.
3. **Identify Dependencies**: Note external calls (Redis, DB, HTTP) that can fail.
4. **Analyze Fail Semantics**: For each dependency, determine if failure is fail-open or fail-closed.
5. **Brainstorm "Cliffs"**: Where does logic change? (e.g., `if (size > 100)`, null checks, TTL expiry).
6. **Brainstorm "Storms"**: What happens if 10 threads do this at once? Is the operation atomic?
7. **Generate Per-Component Tables**: Fill the Markdown tables with prefixed IDs.
8. **Generate Combinatorial Section**: Test feature combinations that individually work but may break together.
9. **Build Coverage Matrix**: Count scenarios per category per component.
10. **Build Risk Register**: Summarize all critical bugs/risks discovered.

## Output Format

The output **MUST** be a Markdown document following this structure:

### 1. Per-Component Scenario Tables

For each class/method under analysis, create a section with file links and a scenario table:

```markdown
## [ComponentName](file:///path/to/Component.java) — Brief Description

| # | Scenario | Input / State | Expected Result | Criticality |
|---|---------|---------------|-----------------|-------------|
| PREFIX-01 | Happy Path | Valid input | Success | High |
| PREFIX-02 | Null input | `null` param | Graceful handling | High |
| PREFIX-03 | Dependency down | Redis throws | Fallback or error | Critical |
```

Use **component-prefixed IDs** for traceability:
- Choose a 1-3 letter prefix per component (e.g., `K-` for KeyGenerator, `G-` for Get, `P-` for Put)
- Cross-component scenarios use `CC-` prefix
- Cross-module scenarios use `X-` prefix

> [!IMPORTANT]
> Scenario IDs are for the **planning document only**. Do NOT embed them in Java test method names. Test methods must be self-descriptive:
> - ✅ `should_call_origin_exactly_once_under_thundering_herd()`
> - ❌ `test_GL04_thunderingHerd_multipleThreads_singleOriginCall()`

### 2. Bug Discovery Callouts

When analysis reveals design bugs or race conditions, flag them inline using GitHub alerts:

```markdown
> [!CAUTION]
> **PREFIX-NN**: The `validateAndSetToken` uses GET + SET (not atomic Lua script),
> creating a race condition window where two threads can both pass validation.

> [!WARNING]
> **PREFIX-NN**: If Pub/Sub publish fails, local invalidation happens but remote
> pods keep stale data. No retry mechanism exists.
```

### 3. Combinatorial Scenarios (Cross-Feature)

Test combinations of features that individually work but may break together:

```markdown
## Combinatorial Scenarios

| # | Combination | Scenario | Expected Result | Criticality |
|---|-------------|---------|-----------------|-------------|
| CC-01 | Feature A + Feature B | Both active | Correct interaction | Critical |
```

### 4. Cross-Module Integration (when applicable)

When the system spans multiple modules with shared infrastructure:

```markdown
## Cross-Module Scenarios

| # | Combination | Scenario | Expected Result | Criticality |
|---|-------------|---------|-----------------|-------------|
| X-01 | Module A + Module B | Shared Redis down | Both degrade gracefully | Critical |
```

### 5. Coverage Matrix

```markdown
| Component | Happy Path | Error/Edge | Concurrency | Combinatorial | Total |
|-----------|-----------|-----------|-------------|---------------|-------|
| ComponentA | 5 | 3 | 2 | 1 | 11 |
| **Total** | **N** | **N** | **N** | **N** | **N** |
```

### 6. Risk Register

```markdown
## Critical Risks Discovered

| # | Risk | Impact | Component |
|---|------|--------|-----------|
| 1 | Non-atomic GET+SET in fencing | Race condition bypasses fencing | FencingTokenProvider |
```

## Analysis Checklist

When generating scenarios, explicitly check for:

- **[ ] Happy Paths**: Basic functionality with valid inputs.
- **[ ] Null/Empty Checks**: `null` inputs, empty collections, zero values.
- **[ ] Boundaries**: Max/Min values, list limits, page sizes.
- **[ ] Serialization**: JSON roundtrips, special characters, large payloads, generic types.
- **[ ] Resilience**: Dependency failures (Redis/DB down), timeouts, retries.
- **[ ] Fail Semantics**: For each external dependency, document:
  - Does it fail-open (proceed anyway) or fail-closed (error)?
  - Is the failure logged?
  - Can partial state be left behind (e.g., SETNX without EXPIRE)?
- **[ ] Concurrency**: Race conditions, `synchronized` blocks, optimistic locking, non-atomic operations (e.g., `check-then-act`, `GET` then `SET`).
- **[ ] Idempotency**: Repeating the same operation multiple times (re-registration, double close).
- **[ ] Lifecycle**: Component startup/shutdown, resource leaks.
- **[ ] Async Messaging** (Kafka, AMQP, etc.):
  - Delivery guarantees: at-least-once → consumer must be idempotent
  - Message ordering: what happens when messages arrive out of order?
  - Poison pills: malformed/undeserializable messages → DLQ or skip?
  - Consumer lag: what if consumer is slow and producer keeps sending?
  - Duplicate delivery: same message processed twice
  - Partition rebalance: consumer restarts mid-batch
  - Producer failure: message sent but ack not received (ghost writes)
  - Transactional outbox: DB commit + message publish atomicity
- **[ ] Feature Combinations**: Two features that individually work but may conflict (e.g., `cacheNulls=true` + `fenced=true`).

## Criticality Definitions

- **Critical**: Data loss, security breach, deadlock, race condition, or service outage.
- **High**: Feature breakage, incorrect business logic.
- **Medium**: Minor annoyance, edge case logic error.
- **Low**: UX glitch, minor logging issue.
