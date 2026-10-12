# Implementation Plan: Module 1 — Application Foundation

**Branch**: `001-app-foundation` | **Date**: 2026-10-12 | **Spec**: [specs/001-app-foundation/spec.md](file:///home/user/Documents/java-projects/finflow/specs/001-app-foundation/spec.md)

**Input**: Feature specification from `specs/001-app-foundation/spec.md`

## Summary

Module 1 establishes the core foundational infrastructure for the FinFlow modular monolith. It provides a hardened, multi-environment runtime backbone (Java 17 / Spring Boot 3.x, PostgreSQL, Redis) with zero business logic. Key deliverables include:
- Environment profile management (`local`, `dev`, `staging`, `production`) with startup fail-fast secret validation.
- Automated, rollback-tested database migrations managed via Liquibase 4.x with strict `<rollback>` blocks.
- First-boot idempotent Super Admin seeding with BCrypt password hashing and AES-256-GCM encrypted PII fields.
- Centralized cross-cutting infrastructure: RFC 7807 Problem Details global exception handler, environment-aware CORS policy, MDC request tracing with `X-Correlation-ID`, Redis Lettuce connection pool validator, and a transactional `AuditInterceptor` writing deltas to `*_aud` shadow tables.

## Technical Context

**Language/Version**: Java 17 LTS / Spring Boot 3.3.x  
**Primary Dependencies**: 
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-security`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.springframework.boot:spring-boot-starter-actuator`
- `org.springframework.boot:spring-boot-starter-validation`
- `org.liquibase:liquibase-core`
- `org.postgresql:postgresql`
- `org.apache.commons:commons-pool2`
- `net.logstash.logback:logstash-logback-encoder`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

**Storage**: PostgreSQL 15+ (relational datastore & shadow audit tables), Redis 7+ (shared connection pool for cache/sessions)  
**Testing**: JUnit 5, AssertJ, Mockito, Testcontainers (PostgreSQL, Redis), `mvn liquibase:update-testing-rollback`  
**Target Platform**: Linux server / Container runtime (Docker multi-stage JDK build, Docker Compose)  
**Project Type**: Web service (modular monolith backend)  
**Performance Goals**: Non-workflow API endpoints P95 response time ≤ 200ms; bootstrap/startup < 15s  
**Constraints**:
- All PII fields encrypted at rest using AES-256-GCM via JPA attribute converters.
- No raw database primary keys exposed to frontend (AES session token obfuscation foundation).
- Every mutating database operation writes to a corresponding `*_aud` shadow table in the same ACID transaction.
- Every Liquibase changeset must provide an explicit `<rollback>` plan.
- All API errors conform to RFC 7807 Problem Details format.
- No plain-text PII in application logs or audit tables.
- Redis connection failure handled by profile: warning in `local`/`dev`, immediate startup abort in `staging`/`prod`.

**Scale/Scope**: Shared infrastructure for 8 application modules across 4 deployment environments.

---

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle / Standard | Status | Validation / Enforcement Mechanism |
|:---|:---|:---|
| **I. Security & Trust: AES-256-GCM PII Encryption** | PASS | Handled via `AesGcmCryptoConverter` JPA attribute converter on `email`, `firstName`, `lastName`, `phone`. |
| **I. Security & Trust: PK Obfuscation Foundation** | PASS | Base ID token encryption interface established; internal UUIDs never directly exposed. |
| **I. Security & Trust: Method-level Security** | PASS | `SecurityConfig` enables `@EnableMethodSecurity(prePostEnabled = true)` for future `@PreAuthorize` module rules. |
| **II. Contract-First API Design** | PASS | Defined in `specs/001-app-foundation/contracts/foundation-api.yaml` (OpenAPI 3.1) and JSON schemas. |
| **III. Audit Everything: Same ACID Transaction** | PASS | `AuditInterceptor` / JPA lifecycle listener persists deltas into `users_aud` in same transaction. |
| **III. Audit Everything: No PII in Audit Logs** | PASS | Diff generator scrubs/masks all sensitive fields before persisting to `diff_data` JSONB. |
| **IV. RBAC Enforced at Every Layer** | PASS | Foundation provisions `SUPER_ADMIN`, `ADMIN`, `END_USER` roles in initial schema. |
| **V. Versioned, Rollback-Ready Migrations** | PASS | Liquibase changelogs defined with mandatory `<rollback>` blocks; verified with `update-testing-rollback`. |
| **Tech Standards: Java 17+ & Spring Boot 3.x** | PASS | Configured for Java 17 and Spring Boot 3.3.x. |
| **Tech Standards: RFC 7807 Error Responses** | PASS | `GlobalExceptionHandler` using Spring `ProblemDetail` with correlation ID. |
| **Tech Standards: PII Log Masking** | PASS | Logback regex pattern converter masks email, phone, and tokens in all log outputs. |

---

## Project Structure

### Documentation (this feature)

```text
specs/001-app-foundation/
├── spec.md              # Feature specification
├── plan.md              # This implementation plan
├── research.md          # Phase 0 architectural decisions & rationale
├── data-model.md        # Phase 1 database schema & entity design
├── quickstart.md        # Phase 1 validation scenarios & runbook
└── contracts/           # Phase 1 interface specifications
    ├── foundation-api.yaml
    └── audit-event.json
```

### Source Code Layout (repository root)

```text
backend/
├── pom.xml
├── api/
│   └── openapi/
│       └── finflow-foundation-api.yaml
├── src/
│   ├── main/
│   │   ├── java/com/finflow/
│   │   │   ├── FinflowApplication.java
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java
│   │   │   │   ├── RedisConfig.java
│   │   │   │   ├── SecurityConfig.java
│   │   │   │   └── WebMvcConfig.java
│   │   │   ├── core/
│   │   │   │   ├── audit/
│   │   │   │   │   ├── AuditableEntity.java
│   │   │   │   │   ├── AuditInterceptor.java
│   │   │   │   │   ├── AuditRecord.java
│   │   │   │   │   └── EntityAuditService.java
│   │   │   │   ├── crypto/
│   │   │   │   │   ├── AesGcmCryptoConverter.java
│   │   │   │   │   └── CryptoUtils.java
│   │   │   │   ├── exception/
│   │   │   │   │   ├── GlobalExceptionHandler.java
│   │   │   │   │   ├── BaseException.java
│   │   │   │   │   └── ResourceNotFoundException.java
│   │   │   │   ├── filter/
│   │   │   │   │   ├── CorrelationIdFilter.java
│   │   │   │   │   └── RequestLoggingFilter.java
│   │   │   │   ├── logging/
│   │   │   │   │   └── PiiMaskingPatternLayout.java
│   │   │   │   └── startup/
│   │   │   │       ├── RedisHealthValidator.java
│   │   │   │       ├── SecretsValidator.java
│   │   │   │       └── SuperAdminInitializer.java
│   │   │   └── modules/
│   │   │       └── foundation/
│   │   │           ├── entity/
│   │   │           │   ├── User.java
│   │   │           │   └── Role.java
│   │   │           └── repository/
│   │   │               ├── UserRepository.java
│   │   │               └── RoleRepository.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-dev.yml
│   │       ├── application-staging.yml
│   │       ├── application-production.yml
│   │       ├── logback-spring.xml
│   │       └── db/changelog/
│   │           ├── db.changelog-master.xml
│   │           └── v1.0.0/
│   │               ├── db.changelog-v1.0.0.xml
│   │               ├── 001-create-app-foundation-tables.xml
│   │               └── 002-create-audit-shadow-tables.xml
│   └── test/
│       ├── java/com/finflow/
│       │   ├── core/
│       │   │   ├── crypto/AesGcmCryptoConverterTest.java
│       │   │   ├── exception/GlobalExceptionHandlerTest.java
│       │   │   └── filter/CorrelationIdFilterTest.java
│       │   └── startup/
│       │       ├── SuperAdminInitializerTest.java
│       │       └── RedisHealthValidatorTest.java
│       └── resources/
│           └── application-test.yml
```

**Structure Decision**: Modular Monolith layout within `backend/`. Foundation services and cross-cutting components live in `com.finflow.core` to provide zero-configuration infrastructure consumed by all future modules (`com.finflow.modules.*`).

---

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

*No violations identified. All designs strictly comply with Constitution Principles I–V and Technology Standards.*
