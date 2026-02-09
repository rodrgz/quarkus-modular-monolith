---
name: Domain Module Evaluator
description: Evaluates domain modules for sub-domain boundaries, cohesion, coupling, and provides guidance on whether to split into sub-modules or maintain flat structure following modular monolith architecture principles.
---

# Domain Module Evaluator

Expert in evaluating modular architecture, assessing whether domain modules should be split into sub-modules or maintain a flat structure.

## When to Use

- User asks to evaluate a domain module's structure
- User wants to know if a module should be split into sub-modules
- Module is growing and losing cohesion
- Assessing whether features belong in the same domain
- Planning refactoring of existing modules

## Evaluation Process

### Step 1: Gather Module Information

Collect the module structure:

1. **List all services** in `domain/api/` and `domain/internal/`
2. **List all entities** in `infra/persistence/`
3. **List all resources** in `infra/rest/`
4. **Check existing sub-modules** (subdirectories under `domain-modules/`)
5. **Identify external dependencies** (adapters in `infra/adapter/`)
6. **List domain events** in `events-module/`

### Step 2: Analyze Current Organization

For each service/entity/resource:

- What is its primary responsibility?
- Which other services/entities does it depend on?
- What user persona does it serve?
- What execution model does it use (sync REST / async messaging)?

### Step 3: Identify Potential Sub-Domains

Look for **natural groupings** based on:

#### Signal 1: Different User Personas
- Admin operations vs public/customer operations
- Internal tools vs external APIs

#### Signal 2: Different Execution Models
- Synchronous REST/GraphQL APIs
- Asynchronous message consumers (Kafka, AMQP)
- Scheduled batch jobs (`@Scheduled`)

#### Signal 3: Different Technical Characteristics
- Read-heavy vs write-heavy
- Different scaling needs (CPU-bound vs I/O-bound)
- Different reliability requirements

#### Signal 4: Different Change Velocities
- Frequently changing features vs stable features
- Experimental features vs production-stable features

#### Signal 5: Potential for Independent Deployment
- Could this logically be a separate microservice?
- Could this scale independently?
- Could this fail without affecting other features?

### Step 4: Measure Cohesion and Coupling

#### Cohesion Score (Higher is Better, 1-5)

- **5**: Single, clear responsibility. All components serve same purpose
- **4**: Related responsibilities, changes usually affect same components
- **3**: Some overlap, but components can work independently
- **2**: Loosely related, components serve different purposes
- **1**: Unrelated responsibilities grouped arbitrarily

#### Coupling Score (Lower is Better, 1-5)

- **1**: Groups never interact directly
- **2**: Occasional communication via events/APIs
- **3**: Regular communication through well-defined interfaces
- **4**: Frequent direct calls, shared state
- **5**: Tightly coupled, can't function independently

#### Decision Matrix

```
High Cohesion (4-5) + Low Coupling (1-2) = STRONG CANDIDATE for sub-modules
High Cohesion (4-5) + High Coupling (4-5) = KEEP TOGETHER
Low Cohesion (1-2) + Any Coupling          = REFACTOR SERVICES first, don't split yet
```

### Step 5: Apply the 6-Criteria Test

| # | Criterion | Question | Yes/No |
|---|-----------|----------|--------|
| 1 | **User Persona** | Does this serve fundamentally different users? | |
| 2 | **Access Control** | Does this need different authorization models? | |
| 3 | **Execution Model** | Does this use different patterns (REST vs Queue)? | |
| 4 | **Scaling Needs** | Does this have different scaling characteristics? | |
| 5 | **Deployment** | Could this reasonably be deployed independently? | |
| 6 | **Failure Isolation** | Can this fail without affecting other parts? | |

**Decision Rules**:
- ✅ **4+ criteria met** → STRONG recommendation for sub-modules
- ⚠️ **2-3 criteria met** → CONSIDER sub-modules (evaluate trade-offs)
- ❌ **0-1 criteria met** → KEEP FLAT structure

### Step 6: Validate with Real-World Scenarios

1. **New Feature Test**: "If we add a feature to Group A, would we need to modify Group B?"
2. **Deployment Test**: "Could we deploy Group A without Group B?"
3. **Team Test**: "Could different teams own these groups?"
4. **Database Test**: "Do these groups share entities heavily?"
5. **Change Frequency Test**: "When we change logic, do both groups change?"

## Output Format

```markdown
# Module Evaluation: {module-name}

## Current Structure
- **Pattern**: [Flat / Sub-domain-based]
- **Services**: {count}
- **Entities**: {count}
- **Resources**: {count}

## Identified Groupings

### Group 1: {name}
**Responsibilities**: {brief description}
**Components**: Services, Entities, Resources
**Cohesion Score**: {1-5}
**User Persona**: {who uses this}
**Execution Model**: {sync/async}

### Group 2: {name}
[Same format]

## Coupling Analysis
**Coupling Score**: {1-5}
**Dependencies**: {list}
**Shared Entities**: {list}
**Communication Pattern**: {direct / events / none}

## 6-Criteria Test Results
[Table with results]
**Total**: {X}/6 criteria met

## Recommendation
### ✅ RECOMMENDED: [Split / Keep Flat]
**Rationale**: ...

**Proposed Structure** (if splitting):
```text
{service}/domain-modules/
├── {sub-module-1}-domain-module/
│   └── domain/api/ + domain/internal/
├── {sub-module-2}-domain-module/
│   └── domain/api/ + domain/internal/
└── events-module/ (shared events)
```

**Trade-offs**: Benefits vs Costs
```

## Red Flags: When NOT to Split

❌ **"The module feels big"** — Size alone is not a reason. Refactor services first.

❌ **"To make code easier to find"** — Organization problem, not a domain problem.

❌ **"Features are tightly coupled"** — High coupling = they belong together.

❌ **"To match team structure"** — Don't let org chart drive architecture.

❌ **"Following a pattern without reason"** — Each module's needs differ.

## Green Lights: When TO Split

✅ **"These serve different user types"** — Admin vs customer operations.

✅ **"These have different failure modes"** — Background processing can fail without affecting APIs.

✅ **"These scale differently"** — CPU-intensive processing vs simple CRUD.

✅ **"These could be separate services"** — Minimal shared state, separate databases.

✅ **"These have different change velocities"** — Stable catalog vs frequently changing admin.

## Examples

### Example: Multi-Subdomain Module ✅

```
content/domain-modules/
├── admin-domain-module/       # Content management
├── catalog-domain-module/     # Content discovery  
├── processor-domain-module/   # Video/content processing
└── events-module/             # Shared events
```

Why this works: Different users (creators vs consumers), different execution (REST vs async), different scaling (API vs CPU-heavy), could deploy separately. **4/6 criteria met**.

### Example: Single-Domain Module ✅

```
billing/domain-modules/
└── billing-domain-module/
    └── domain/
        ├── api/
        │   ├── SubscriptionService.java
        │   ├── InvoiceService.java
        │   └── PaymentService.java
        └── internal/
```

Why flat is correct: Same users (billing operations), same execution (REST), tightly coupled (subscriptions → invoices → payments), can't separate. **0/6 criteria met**.

## Important Notes

1. **Sub-modules are strategic, not organizational** — they represent sub-domain boundaries
2. **Flat is often better** — don't prematurely split. Most modules should start flat
3. **Shared events pattern** — if splitting, create `events-module` for shared domain events
4. **Cohesion beats size** — 1000 lines of cohesive code > 300 lines split incorrectly
5. **Future microservices** — good sub-modules → easy microservice extraction

## Quality Checklist

Before recommending a split:

- [ ] Identified 2+ clear sub-domains with distinct responsibilities
- [ ] Each sub-domain has cohesion score 4+
- [ ] Coupling between sub-domains is score 1-2
- [ ] Met 2+ criteria from the 6-criteria test
- [ ] Validated with real-world scenarios
- [ ] Confirmed not splitting for wrong reasons
- [ ] Considered trade-offs and alternatives

---

Remember: The goal is **sustainable architecture**, not premature optimization. When in doubt, stay flat until sub-domains prove themselves through actual usage and pain points.
