# Modular Architecture Overview

This document provides a high-level introduction to our modular architecture approach and serves as a navigation hub to detailed guidelines.

## Quick Reference (For LLMs)

**When to use this doc**: Navigation hub - read first to understand architecture and decide which detailed docs to load

**Key rules**:

- ✅ DO: Use this doc to navigate to specific guidelines based on task type
- ✅ DO: Load specific docs only when needed (progressive loading)
- ✅ DO: Start here when understanding overall architecture
- ❌ DON'T: Load all docs at once (saves ~51k tokens)
- ❌ DON'T: Skip this when starting new work

**Detection**: See [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands) for all verification commands

**See also**:

- [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) - Principles 1-7
- [STATE-ISOLATION.md](./STATE-ISOLATION.md) - Principle 8 (CRITICAL)
- [CODING-PATTERNS.md](./CODING-PATTERNS.md) - Implementation patterns
- [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) - Verification steps

## Core Philosophy

**Modular Architecture** is a pragmatic approach that avoids premature microservices complexity while maintaining clear boundaries between business domains. It provides the benefits of both monoliths (development simplicity) and distributed systems (independence and scalability).

### Key Concepts

- **Services are Bootstraps**: Application services (`*-service/`) only orchestrate domain modules, containing minimal logic
- **Domain Modules are Logic**: All business logic lives in domain modules (`domain-modules/`), enabling maximum reusability
- **Hexagonal Architecture**: Each module follows ports & adapters — domain logic is isolated from infrastructure
- **Domain-Based Organization**: Organize by business capabilities, not technical features
- **Evolutionary Design**: Start modular, extract to microservices only when proven necessary

### Project Structure

```
project-root/
├── bom/                              # Centralized Maven BOM
├── shared-domain-module/             # Shared domain interfaces and value objects
├── commerce-service/                 # Application service (bootstrap)
│   ├── domain-modules/               # Domain modules (business logic)
│   │   ├── ordering-domain-module/
│   │   │   ├── domain-api/           # Public ports/interfaces
│   │   │   └── domain-internal/      # Private domain implementation
│   │   └── inventory-domain-module/
│   ├── data-shared-modules/          # Infrastructure adapters
│   │   ├── ordering-infrastructure-module/
│   │   └── inventory-infrastructure-module/
│   └── infrastructure-module/        # Wiring: CDI producers, REST resources
└── invoicing-service/                # Another application service
```

## The 10 Principles of Modular Architecture

Our architecture is built on 10 foundational principles that ensure maintainability, scalability, and resilience:

| #   | Principle                   | Description                                  | Details                                                                                |
| --- | --------------------------- | -------------------------------------------- | -------------------------------------------------------------------------------------- |
| 1   | **Well-Defined Boundaries** | Clear responsibilities, no internal exposure | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#1-well-defined-boundaries)             |
| 2   | **Composability**           | Building blocks that combine flexibly        | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#2-composability)                       |
| 3   | **Independence**            | Autonomous operation without tight coupling  | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#3-independence)                        |
| 4   | **Individual Scale**        | Module-specific resource optimization        | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#4-individual-scale)                    |
| 5   | **Explicit Communication**  | Well-defined contracts for all interactions  | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#5-explicit-communication)              |
| 6   | **Replaceability**          | Swappable implementations behind interfaces  | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#6-replaceability)                      |
| 7   | **Deployment Independence** | Deployment-agnostic module design            | [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#7-deployment-independence)             |
| 8   | **State Isolation**         | Own state management per module              | [STATE-ISOLATION.md](./STATE-ISOLATION.md) ⚠️                                          |
| 9   | **Observability**           | Individual visibility and monitoring         | [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#observability--monitoring) |
| 10  | **Fail Independence**       | Failures don't cascade between modules       | [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#fail-independence)         |

⚠️ **State Isolation is the most critical and frequently violated principle** - see dedicated document for details.

## Document Organization

Our architecture guidelines are organized into focused documents by responsibility:

### 📘 [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md)

**When to read**: Understanding module boundaries and interactions

Deep dive into principles 1-7 covering:

- How modules should be structured
- Communication patterns between modules
- Composability and independence guidelines
- Code examples for each principle

### 🔒 [STATE-ISOLATION.md](./STATE-ISOLATION.md)

**When to read**: Working with databases, entities, or data access

Critical guidelines for principle 8:

- Entity naming conventions (`@Table(name = "module_entity")`)
- Database connection patterns (named persistence units)
- **FORBIDDEN**: Duplicate `@Table` names across modules
- Detection commands and verification
- Migration strategies (Flyway)

**Read this before creating any JPA entities!**

### 🛠️ [CODING-PATTERNS.md](./CODING-PATTERNS.md)

**When to read**: Implementing services, JAX-RS resources, or repositories

Technical implementation patterns:

- Repository Pattern & Panache Encapsulation
- JAX-RS Resource Responsibilities & Lean Pattern
- Transaction Management with `@Transactional`
- Anti-patterns to avoid
- Testing strategies

### 🔧 [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md)

**When to read**: Adding monitoring, logging, or error handling

Building resilient systems:

- Observability & Monitoring (Principle 9)
- Fail Independence (Principle 10)
- MicroProfile Fault Tolerance patterns (`@CircuitBreaker`, `@Retry`, `@Timeout`)
- Graceful degradation
- MicroProfile Health checks and Micrometer metrics
- Event system implementations (Quarkus Messaging)

### 🔌 [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md)

**When to read**: Integrating external APIs or third-party services

Integration patterns and best practices:

- Mock, REST Client, and SDK integration patterns
- Client encapsulation and architecture compliance
- Direct injection vs interface patterns
- Resilience patterns (circuit breakers, timeouts, retries)
- Security and observability guidelines
- Migration from mock to production

### ✅ [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md)

**When to read**: Starting new features or refactoring code

Practical checklists:

- Verification steps for new features
- Refactoring guidelines
- Common anti-patterns to avoid
- Detection commands
- Automated verification tools (ArchUnit, grep)
- Pre-commit hooks

## Quick Reference Guide

### Decision Tree: What to Read

**IF** creating/modifying JPA entities or Flyway migrations → **THEN** read [STATE-ISOLATION.md](./STATE-ISOLATION.md) (~8k tokens)  
**IF** creating JAX-RS resources, CDI services, or Panache repositories → **THEN** read [CODING-PATTERNS.md](./CODING-PATTERNS.md) (~12k tokens)  
**IF** creating new domain modules or designing boundaries → **THEN** read [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) (~7k tokens)  
**IF** integrating external APIs or third-party services → **THEN** read [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md) (~8k tokens)  
**IF** adding logging, monitoring, or error handling → **THEN** read [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) (~7k tokens)  
**IF** verifying compliance or running detection commands → **THEN** read [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) (~9k tokens)  
**IF** understanding overall architecture → **THEN** read this overview (~3k tokens) + specific docs as needed

### I want to...

- **Create a new domain module** → Read [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) + [STATE-ISOLATION.md](./STATE-ISOLATION.md)
- **Add a JPA entity** → Read [STATE-ISOLATION.md](./STATE-ISOLATION.md) first! (~8k tokens)
- **Create a JAX-RS resource / CDI service** → Read [CODING-PATTERNS.md](./CODING-PATTERNS.md) (~12k tokens)
- **Add inter-module communication** → Read [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md#5-explicit-communication) (~7k tokens)
- **Integrate external API** → Read [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md) (~8k tokens)
- **Implement error handling** → Read [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#fail-independence) (~7k tokens)
- **Add monitoring/logging** → Read [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md#observability--monitoring) (~7k tokens)
- **Verify architecture compliance** → Read [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) (~9k tokens)

## Document Scope

These documents focus on **inter-module architecture** (how domain modules interact and the principles that govern modular systems).

## Common Pitfalls

🚫 **Most Critical Violations**:

1. **Duplicate Table Names** - Never use same `@Table(name = "x")` across modules → [STATE-ISOLATION.md](./STATE-ISOLATION.md)
2. **Fat JAX-RS Resources** - Business logic belongs in services, not resources → [CODING-PATTERNS.md](./CODING-PATTERNS.md#jax-rs-resource-responsibilities--lean-pattern)
3. **Direct Repository Calls from Resources** - Resources should only call services → [CODING-PATTERNS.md](./CODING-PATTERNS.md#jax-rs-resource-responsibilities--lean-pattern)
4. **Missing @Transactional** - Write operations need `@Transactional` → [CODING-PATTERNS.md](./CODING-PATTERNS.md#transaction-management)
5. **Cross-Module Database Access** - Never access another module's persistence → [STATE-ISOLATION.md](./STATE-ISOLATION.md)

## Getting Started

### For New Team Members

1. Read this overview (you're here!)
2. Skim [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) to understand the 10 principles
3. Read [STATE-ISOLATION.md](./STATE-ISOLATION.md) carefully before touching databases
4. Keep [CODING-PATTERNS.md](./CODING-PATTERNS.md) handy while coding
5. Use [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) for verification

### For AI Agents

1. **Load this overview first** (~3k tokens) for navigation
2. **Load specific documents based on task** (progressive loading):
   - **IF** database work → Load [STATE-ISOLATION.md](./STATE-ISOLATION.md) (~8k tokens)
   - **IF** service/resource work → Load [CODING-PATTERNS.md](./CODING-PATTERNS.md) (~12k tokens)
   - **IF** module communication → Load [MODULAR-PRINCIPLES.md](./MODULAR-PRINCIPLES.md) (~7k tokens)
   - **IF** external API integration → Load [THIRD-PARTY-INTEGRATION.md](./THIRD-PARTY-INTEGRATION.md) (~8k tokens)
   - **IF** logging/monitoring → Load [RESILIENCE-OBSERVABILITY.md](./RESILIENCE-OBSERVABILITY.md) (~7k tokens)
   - **IF** verification needed → Load [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md) (~9k tokens)
3. **Always run verification commands** from [IMPLEMENTATION-CHECKLIST.md](./IMPLEMENTATION-CHECKLIST.md#detection-commands)

**Token Efficiency**: Load only what you need. Typical task requires 1-2 docs (~15-20k tokens) instead of all docs (~54k tokens).

## Architecture Principles Summary

### What Makes Good Modular Architecture?

✅ **Good Modular Architecture**:

- Clear boundaries between modules (hexagonal ports & adapters)
- Explicit communication via Java interfaces (domain ports)
- Each module owns its data (separate `@Table` namespaces)
- Modules can be deployed independently
- Failures isolated to single modules (MicroProfile Fault Tolerance)
- Easy to test in isolation (`@QuarkusTest` per module)

❌ **Bad Modular Architecture**:

- Shared database tables between modules
- Direct class dependencies on internal implementations
- Tight coupling via `@Inject` of internal beans across modules
- Cascading failures
- Cannot test modules independently
- Deployment requires all modules

---

**Last Updated**: February 2026  
**Maintained By**: Architecture Team
