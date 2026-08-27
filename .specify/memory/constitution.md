<!--
Sync Impact Report
- Version change: none → 1.0.0 (initial ratification)
- Modified principles: n/a (initial adoption)
- Added sections: Core Principles (I-VII), Technology Stack, Development Workflow, Governance
- Removed sections: none
- Templates requiring updates: none checked beyond this constitution; downstream
  plan/spec/tasks templates already read placeholders at runtime and need no edits.
- Follow-up TODOs: none — all placeholders resolved from user-supplied input.
-->
# Knowledge Graph Interview Prep Constitution
<!-- Portfolio project preparing for a Java knowledge-graph developer interview -->

## Core Principles

### I. Layered Polyglot Architecture, Java as Source of Truth
All graph domain logic, validation, and persistence MUST live in the Java/Spring Boot
core. The MCP server, the Python LangGraph agent, and the React front-end are
consumers/adapters only. They MUST NOT re-implement or duplicate graph business
logic — they may cache, format, or orchestrate calls to the Java core, nothing more.
Rationale: keeps the interview-relevant skill (Java graph engineering) concentrated
in one place and prevents drift between multiple "sources of truth."

### II. Contract-First Integration
Every cross-language boundary MUST be specified before implementation: an OpenAPI
document for the Java REST/GraphQL API, and a JSON schema for each MCP tool. Changing
a contract in a backward-incompatible way REQUIRES a version bump and updating every
affected consumer in the same change. Rationale: the four layers are built in
different languages by nature of this project; undocumented contracts are the most
likely source of integration breakage under time pressure.

### III. Test Discipline Per Stack
Every component MUST ship unit and integration tests before being considered done:
JUnit5 + Mockito for Java, pytest for the Python agent, Vitest/React Testing Library
for the front-end. TDD is encouraged but not mandatory. A feature is NOT "done" if
its layer lacks passing tests. Rationale: standard, testable practice is itself part
of what the interview is evaluating.

### IV. Secure-by-Default Graph Access (NON-NEGOTIABLE)
All user- or agent-supplied input that reaches a graph query MUST be parameterized
or sanitized to prevent Cypher/Gremlin injection (the graph-query equivalent of SQL
injection). The target end-state is OAuth2/JWT authentication on the MCP server and
API. Under time pressure, the minimum acceptable interim bar is API-key
authentication plus input validation — a network-exposed endpoint MUST NEVER ship
with no authentication at all. Rationale: OWASP-aware design must survive schedule
pressure, not be the first thing cut.

### V. Interview-Grade Code Quality & Explainability
Code MUST be idiomatic for its language (standard Java/Spring conventions, PEP
8/ruff for Python, ESLint/Prettier for React). Every non-obvious design decision
MUST be documented clearly enough to explain verbally in an interview setting.
Rationale: this codebase is a talking point, not just a working demo — it must
hold up under technical questioning.

### VI. North-Star Scope with MVP-First Delivery
This constitution describes the full target system: Neo4j-backed graph core,
Spring Boot, CRUD + schema modeling, traversal, graph algorithms, a query DSL,
NLP-based entity extraction, full OAuth2/JWT auth, an agentic LangGraph
orchestrator, and a full-featured React UI (visualization, search, chat, admin
CRUD). Given a tight timeline, every feature spec produced via `/speckit-specify`
MUST explicitly define a minimal demoable slice first and clearly label advanced
capabilities (graph algorithms, NLP extraction, full OAuth2) as stretch goals.
Rationale: preserves the ambitious end-state as a shared reference while keeping
each increment shippable before the interview date.

### VII. Demo-Readiness & Observability
The full stack MUST be runnable locally with a single command (e.g., one
docker-compose invocation covering Neo4j and all services). Structured logging is
REQUIRED in every layer so behavior is traceable during a live walkthrough.
Rationale: an interview demo that fails to start, or that cannot be explained via
logs when something misbehaves, undermines the rest of the preparation.

## Technology Stack

- **Java core**: Java 21+, Spring Boot 3.x, Spring Data Neo4j, Neo4j 5.x.
- **Python agent**: Python 3.12+, dependency management via `uv` and `pyproject.toml`,
  LangGraph + LangChain for agentic orchestration.
- **Front-end**: React 18+, TypeScript, Vite.
- **MCP server**: exposes the Java API as MCP tools over HTTP/SSE transport.

Deviating from a pinned technology requires updating this section in the same
change that introduces the deviation.

## Development Workflow

- Each architectural layer (Java core, MCP server, LangGraph agent, React
  front-end) is developed as its own spec-kit feature slice via `/speckit-specify`.
- Run `/speckit-clarify` before `/speckit-plan` whenever a slice touches a
  cross-language contract (Principle II) or a security boundary (Principle IV).
- Follow the standard spec-kit flow per slice: specify → clarify (when needed) →
  plan → tasks → implement → converge.

## Governance

This is a solo portfolio project; governance is intentionally lightweight.
Amendments are made by directly editing this file and bumping the version
according to semantic versioning (MAJOR: incompatible principle removal/
redefinition; MINOR: new principle or materially expanded guidance; PATCH:
wording/clarification only). No PR-review gate is required, but every feature
plan produced via `/speckit-plan` MUST verify compliance with the Core
Principles before implementation begins, and any deliberate deviation must be
justified in that plan's Complexity Tracking section.

**Version**: 1.0.0 | **Ratified**: 2026-08-26 | **Last Amended**: 2026-08-26
