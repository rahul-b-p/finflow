<!--
SYNC IMPACT REPORT (remove before committing)
=============================================
Version change:    [unversioned template] → 1.0.0
Added sections:    All (initial ratification — no prior constitution existed)
Modified:          N/A (first edition)
Removed:           N/A
Deferred TODOs:    None — all placeholders resolved from source documents
-->

# FinFlow Constitution

## Core Principles

### I. Security & Trust — Non-Negotiable (NON-NEGOTIABLE)

Security is the foundation of every design and implementation decision in FinFlow.

- All PII (name, email, phone) MUST be stored encrypted at rest using AES-256-GCM via JPA attribute
  converters. No plaintext PII may ever be persisted or logged.
- Internal database primary keys MUST never be exposed to the frontend. All IDs MUST be wrapped in
  per-session AES-encrypted tokens before transmission.
- Every endpoint MUST be protected by Spring Security method-level `@PreAuthorize` with an explicit
  `MODULE:OPERATION` privilege check. Unannotated endpoints MUST NOT exist in production code.
- Authentication tokens MUST only be issued to accounts in `ACTIVE` status. `PENDING_APPROVAL` and
  `REJECTED` accounts MUST be denied tokens at the Spring Security filter level.
- All gateway webhook payloads MUST be verified via HMAC-SHA256 signature before any state mutation.
- Three consecutive token decryption failures within a session MUST force session termination and
  require re-authentication.

**Rationale:** FinFlow handles personal financial data and payment flows. A breach or data exposure
would directly harm users and violate regulatory obligations (GDPR, PCI-DSS trajectory).

### II. Contract-First API Design (NON-NEGOTIABLE)

All REST endpoints MUST be defined in OpenAPI 3.1 specifications before any implementation begins.

- The OpenAPI contract is the single source of truth; backend controller interfaces are generated
  from it. Hand-coded controllers that diverge from the contract MUST NOT be merged.
- Frontend API clients MUST be generated from the same OpenAPI specification. Manual API calls
  bypassing generated clients are not permitted.
- CI MUST fail if controller implementations drift from the OpenAPI contract or if breaking schema
  changes are introduced without a version bump.

**Rationale:** Contract-First prevents frontend/backend drift, forces API design discipline before
coding, and enables parallel development across teams.

### III. Audit Everything — Complete Chain of Custody

Every mutating database operation MUST write a corresponding record to the entity's shadow audit
table (`*_aud`) within the same ACID transaction as the primary mutation.

- No domain table may be created without a corresponding `*_aud` shadow table.
- Audit records MUST capture: `rev_type` (INSERT/UPDATE/DELETE), `modified_by` (obfuscated actor),
  `ip_address`, `rev_timestamp` (UTC), and a `diff_data` JSONB snapshot of changed fields.
- Audit records MUST NOT contain any raw PII. All sensitive fields MUST be tokenized or masked
  before writing to audit tables.
- Audit logs MUST be immutable once written. No UPDATE or DELETE operation is permitted on `*_aud`
  tables by application code.

**Rationale:** The platform requires full auditability for financial compliance, forensic analysis,
and administrator accountability. A gap in the audit chain makes point-in-time reconstruction
impossible.

### IV. RBAC Enforced at Every Layer

Access control is privilege-based, not role-based at the UI level. Roles define default privilege
sets; individual privileges govern every operation.

- Every module operation MUST map to an atomic privilege named `{MODULE}:{OPERATION}`.
- Privilege evaluation MUST happen at the Spring Security method level — never only in the service
  or controller layer.
- Every new module or feature MUST register its privilege codes in the RBAC privilege catalog before
  implementation begins.
- The default end-user privilege set (granted on account approval) MUST be the minimum necessary for
  core platform use. Additional privileges require explicit admin grant.
- Privilege checks MUST be enforced identically for admin users and end-users; no code path may
  bypass the privilege check by testing for a role name directly.

**Rationale:** Fine-grained per-operation RBAC ensures least-privilege access, makes permission
auditing deterministic, and prevents privilege escalation by role label alone.

### V. Versioned, Rollback-Ready Database Migrations

All schema changes MUST be managed through Liquibase changesets with mandatory rollback plans.

- Every Liquibase changeset MUST define an explicit `<rollback>` block. Changesets without rollback
  definitions MUST NOT be merged.
- Changeset naming MUST follow the convention: `{version}-{sequence}-{description}.xml`
  (e.g., `v1.0.0-001-create-users-table.xml`).
- The CI pipeline MUST run `mvn liquibase:update-testing-rollback` on every pull request targeting
  `main` or `staging`. A failed rollback test blocks merge.
- Data migrations and schema migrations MUST be separated into distinct changesets.
- No application startup may occur if pending Liquibase migrations exist on `staging` or `production`
  without explicit operator authorization.

**Rationale:** Financial applications cannot afford irreversible schema changes. Mandatory rollback
plans ensure every migration can be safely undone during incident response.

## Technology & Architecture Standards

FinFlow is a **modular monolith** built on Java Spring Boot 3.x (backend) and React Vite (frontend).
The following standards are non-negotiable for all modules:

- **Backend Language:** Java 17+ with Spring Boot 3.x. No mixing of language runtimes in the
  backend.
- **Frontend Language:** TypeScript with React (Vite). No plain JavaScript files permitted in the
  frontend source.
- **Primary Database:** PostgreSQL 15+. All relational data MUST use PostgreSQL. No other OLTP
  database is permitted without constitution amendment.
- **Caching & Sessions:** Redis. All session state, idempotency keys, and rate-limiter state MUST
  be stored in Redis. In-memory maps or application-level caches for session data are forbidden.
- **Module Boundary:** Modules MUST communicate via well-defined service interfaces within the
  monolith. Direct cross-module entity access (e.g., a payment repository referenced from the
  workflow module) MUST NOT occur; use public service APIs.
- **Module Build Order:** Modules MUST be implemented in dependency order:
  Foundation → Auth → RBAC → Administration → Services → Workflows → Payments → Notifications.
  Skipping foundational modules is not permitted.
- **Payments:** Only Razorpay and Cashfree are approved payment gateways. Gateway SDK usage MUST be
  isolated behind a payment-service abstraction to allow future gateway swaps without domain
  changes.
- **Error Responses:** All API error responses MUST conform to RFC 7807 Problem Details format using
  the shared `GlobalExceptionHandler`. Custom error formats per controller are forbidden.

## Development Workflow & Quality Gates

- **Spec before Code:** Every new feature MUST have an approved spec (`spec.md`) before any
  implementation work begins. Speckit commands (`/speckit-specify` → `/speckit-plan` →
  `/speckit-tasks` → `/speckit-implement`) MUST be followed in order.
- **OpenAPI First:** API changes MUST update the OpenAPI contract before any controller or service
  code is written.
- **Liquibase First:** Database schema changes MUST be authored as Liquibase changesets before
  entity model changes are coded.
- **No Direct PII in Logs:** All logging MUST pass through the PII-masking filter. Any log
  statement that could emit user email, name, phone, or payment card data is a blocking defect.
- **OWASP Compliance:** All code MUST be developed against OWASP Top 10 guidelines. Static analysis
  (e.g., SpotBugs, OWASP Dependency Check) MUST run in CI.
- **Performance Baseline:** Non-workflow API endpoints MUST target a P95 response time of ≤200ms
  under normal load. Regressions against this baseline are blocking in staging.
- **Test Coverage:** Every service-layer class MUST have unit tests. Integration tests MUST cover
  all API contract paths, RBAC enforcement, and Liquibase rollback scenarios.
- **Module Audit Registration:** Before merging any new module, its `*_aud` tables MUST be
  registered in the Administration module's audit viewer coverage map.

## Governance

This constitution supersedes all other development practices, team conventions, and prior verbal
agreements. It is the authoritative governance document for FinFlow.

- **Amendment Procedure:** Any principle addition, removal, or material redefinition requires a
  pull request updating this file, with a written rationale, reviewed and approved by at least two
  senior contributors before merge.
- **Versioning Policy:** Constitution versions follow semantic versioning:
  - MAJOR: Removal or backward-incompatible redefinition of a principle.
  - MINOR: New principle or section added, or materially expanded guidance.
  - PATCH: Clarifications, wording improvements, typo fixes.
- **Compliance Review:** Constitution compliance MUST be verified as part of every feature's
  definition-of-done checklist (`/speckit-checklist`). Non-compliant features MUST NOT be
  merged to `main`.
- **Ratification:** This constitution was ratified upon first adoption. All contributors implicitly
  agree to its terms by contributing to the repository.
- **Guidance Reference:** Use `sources/application-modules.md` as the runtime module reference,
  `sources/finflow-brd.md` for business objectives, and `sources/finflow-technical-overview.md`
  for architecture decisions. The constitution governs; the sources inform.

**Version**: 1.0.0 | **Ratified**: 2026-10-12 | **Last Amended**: 2026-10-12
