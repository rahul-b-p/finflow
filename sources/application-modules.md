# FinFlow — Application Module Separation

**Project Name:** FinFlow — Personal Finance & Service Manager  
**Document Type:** Module Architecture Reference  
**Version:** 1.0  
**Date:** October 12, 2026

---

## Overview

FinFlow is structured as a **modular monolith** with **8 modules** in two tiers:

- **Foundational Tier (Modules 1–4):** Infrastructure, identity, access control, and operations. Every feature module depends on all four.
- **Feature Tier (Modules 5–8):** Business capabilities delivered to end-users and administrators.

```
┌──────────────────────────────────────────────────────┐
│  Module 8 — Notifications                            │
├──────────────────────────────────────────────────────┤
│  Module 7 — Payments & UPI Integration               │
├──────────────────────────────────────────────────────┤
│  Module 6 — Workflow Engine                          │
├──────────────────────────────────────────────────────┤
│  Module 5 — Service Management                       │
├──────────────────────────────────────────────────────┤
│  Module 4 — Administration                           │
├──────────────────────────────────────────────────────┤
│  Module 3 — RBAC                                     │
├──────────────────────────────────────────────────────┤
│  Module 2 — Authentication                           │
├──────────────────────────────────────────────────────┤
│  Module 1 — Application Foundation                   │
└──────────────────────────────────────────────────────┘
```

---

## Module Build Order

```
1. Application Foundation         →  Infrastructure & seeding
2. Authentication                 →  Identity & session        (depends on: 1)
3. RBAC                           →  Privilege engine          (depends on: 1, 2)
4. Administration                 →  Operations & audit        (depends on: 1, 2, 3)
5. Service Management             →  Services catalog          (depends on: 1, 2, 3, 4)
6. Workflow Engine                →  BPMN service lifecycles   (depends on: 1, 2, 3, 4, 5)
7. Payments & UPI Integration     →  Financial transactions    (depends on: 1, 2, 3, 4, 5, 6)
8. Notifications                  →  Multi-channel alerts      (depends on: 1, 2, 3, 4, 5, 6, 7)
```

---

## Modules

### Module 1 — Application Foundation

Shared infrastructure layer with no business logic. Bootstraps the Spring Boot application, wires environment profiles (`local`, `dev`, `staging`, `production`), manages secrets via AWS Secrets Manager / Vault, runs Liquibase database migrations on startup, and seeds a default **Super Admin** user if none exists. Provides cross-cutting concerns — global exception handler, CORS, request logging filter, Redis connection pool, and the shared `AuditInterceptor` used by all other modules.

---

### Module 2 — Authentication

Manages the full identity lifecycle: user self-registration (placed in `PENDING_APPROVAL` until an admin approves), Google OAuth2 login, RFC 6238 TOTP two-factor authentication, JWT access/refresh token issuance and revocation, and password reset via email OTP. Sessions are Redis-backed with a per-login AES encryption token used to obfuscate all database IDs. Authentication tokens are only issued to `ACTIVE` accounts.

---

### Module 3 — RBAC (Role-Based Access Control)

The privilege engine of the application. Defines roles (`SUPER_ADMIN`, `ADMIN`, `END_USER`) and a catalog of atomic privileges named `{MODULE}:{OPERATION}` (e.g. `PAYMENT:READ`, `SERVICE:CREATE`). Every approved end-user receives a **default privilege set** automatically. Users can request additional privileges; admins approve or reject those requests. All RBAC decisions are enforced via Spring Security `@PreAuthorize` annotations at the method level.

---

### Module 4 — Administration

The operational control plane. Administrators review and approve or reject pending user registrations (approval triggers automatic default RBAC grant). Provides full user management (search, suspend, reinstate, delete) and role/privilege administration. Exposes a **module-scoped audit viewer** to query audit logs across all modules, filtered by module, entity, actor, operation type, date range, and IP address. Also manages global system configuration (session TTL, 2FA enforcement, etc.).

---

### Module 5 — Service Management

Manages the catalogue of financial services available on the platform. Admins create, activate, and deactivate services dynamically via the dashboard — no code changes required. A predefined set of default services (Bill Payment, Subscription Manager, Insurance Premium Tracker, Loan EMI Manager) is seeded on first startup. End-users browse active services and subscribe; each subscription spawns a Workflow Engine instance (Module 6).

---

### Module 6 — Workflow Engine

Defines and executes the operational lifecycle of each service using BPMN-based workflow templates. Admins design templates with ordered steps (pre-payment reminders, UPI payment collection, payment screenshot upload, post-payment confirmation, due-expiry alerts, rescheduling, custom notifications). A workflow instance is spawned per user subscription and driven by Redis queue workers and Spring `@Async` scheduled tasks. Supports step-level rescheduling and manual payment proof upload/verification.

---

### Module 7 — Payments & UPI Integration

Handles all financial transactions via Razorpay / Cashfree. Supports UPI Collect, UPI QR, UPI Intent deeplink, Netbanking, and Card payments. Payment orders are created with Redis-backed idempotency keys to prevent duplicate processing. Incoming gateway webhooks are validated via HMAC-SHA256 signature verification before any state mutation, and every raw webhook event is persisted for forensic replay. Provides refund management and downloadable payment statements (PDF / CSV).

---

### Module 8 — Notifications

Delivers multi-channel messages — **Email** (AWS SES), **SMS** (Twilio), **WhatsApp** (Twilio / Meta Cloud API), and **In-App** (Server-Sent Events) — in response to system events from other modules. Admins manage notification templates with variable substitution. Failed dispatches are retried up to 3 times with exponential backoff. Users configure per-channel opt-in/opt-out preferences. All user PII is masked before notification content is written to dispatch logs.

---

## Cross-Cutting Standards

| Concern | Standard |
|---|---|
| **API Design** | Contract-First via OpenAPI 3.1; endpoints defined before implementation |
| **Database Migrations** | Liquibase changesets with mandatory `<rollback>` blocks; semantic versioning |
| **Audit** | Every mutating operation writes to a `_aud` shadow table in the same ACID transaction |
| **ID Exposure** | Primary keys never sent to the frontend; AES session-token obfuscation on all IDs |
| **PII Protection** | All PII fields encrypted at rest using AES-256-GCM |
| **Error Handling** | Shared `GlobalExceptionHandler`; RFC 7807 Problem Details format |
| **Logging** | SLF4J + Logback; structured JSON logs; PII auto-masked before writing |
| **Security** | Spring Security `@PreAuthorize("hasPrivilege('MODULE:OPERATION')")` on every endpoint |
