# Research & Technical Decisions: Module 1 — Application Foundation

**Feature ID:** MOD-001  
**Module:** Application Foundation  
**Branch:** `001-app-foundation`  
**Date:** 2026-10-12  

---

## 1. Shared Audit Interceptor Architecture

### Decision
Implement a hybrid audit tracking mechanism consisting of:
1. An `@Auditable` entity annotation and base audit model interface (`AuditableEntity`).
2. A Spring Data JPA / Hibernate Post-Insert, Post-Update, Post-Delete event listener combined with an `AuditInterceptor` / `EntityAuditService` executing within the **same ACID transaction**.
3. A dedicated generic schema repository/executor writing directly to `<entity_table>_aud` tables capturing:
   - `audit_id`: BIGSERIAL PRIMARY KEY
   - `id`: UUID (foreign entity ID)
   - `rev_type`: VARCHAR(10) (`INSERT`, `UPDATE`, `DELETE`)
   - `diff_data`: JSONB (field-level delta between old and new state, with PII fields masked)
   - `modified_by`: VARCHAR(100) (obfuscated actor identity from `SecurityContext` / request attribute, e.g. tokenized user UUID or `SYSTEM`)
   - `ip_address`: VARCHAR(45) (client IP extracted from `HttpServletRequest` with `X-Forwarded-For` proxy resolution)
   - `rev_timestamp`: TIMESTAMP WITH TIME ZONE (UTC timestamp)

### Rationale
- **Constitution Principle III Requirement**: Every mutating operation must write to `*_aud` shadow tables within the same ACID transaction. If the transaction rolls back, the audit record must also roll back.
- Hibernate event listeners (`PostInsertEventListener`, `PostUpdateEventListener`, `PostDeleteEventListener`) or JPA entity lifecycle callbacks coupled with `TransactionSynchronizationManager` guarantee that audit records participate in the active transaction boundary.
- Diffing old vs. new state before writing into `diff_data` JSONB provides point-in-time state reconstruction while scrubbing any plain-text PII attributes using the crypto/masking layer.

### Alternatives Considered
- **Envers (Hibernate Envers)**: Envers creates `_AUD` tables automatically, but its default table structure uses numerical revision entities (`REVINFO`) and does not natively produce clean single-table JSONB diffs (`diff_data`), IP address capture, or per-table custom schema conforming to FinFlow's strict audit format.
- **PostgreSQL Database Triggers**: Triggers operate inside PostgreSQL natively, but they cannot directly access HTTP request metadata (client IP address, obfuscated user session token, correlation ID) without passing session parameters in every JDBC connection, adding complexity and coupling.
- **Asynchronous Kafka/RabbitMQ Audit Events**: Rejected because Constitution Principle III explicitly forbids eventual consistency for audit records ("within the same ACID transaction as the primary mutation").

---

## 2. Liquibase Migration & Rollback Strategy

### Decision
- **Tooling**: Liquibase 4.x via `liquibase-core` dependency in Maven and Spring Boot's built-in `LiquibaseAutoConfiguration`.
- **Master Changelog**: Located at `src/main/resources/db/changelog/db.changelog-master.xml`.
- **Directory & Version Structure**:
  ```text
  db/changelog/
  ├── db.changelog-master.xml
  └── v1.0.0/
      ├── db.changelog-v1.0.0.xml
      ├── 001-create-databasechangelog-constraints.xml
      ├── 002-create-app-foundation-tables.xml
      └── 003-seed-super-admin.xml (optional / handled via Liquibase or ApplicationRunner)
  ```
- **File Naming Standard**: `{version}-{sequence}-{description}.xml` (e.g., `v1.0.0-001-create-app-foundation-tables.xml`).
- **Mandatory Rollback Enforcement**:
  - Every `<changeSet>` MUST contain an explicit `<rollback>` block.
  - Changesets containing table creation must have `<rollback><dropTable .../></rollback>`.
  - Schema modifications and data seeds must be isolated in separate changesets.
  - CI verification runs `mvn liquibase:update-testing-rollback`.

### Rationale
- Complies with Constitution Principle V.
- Guarantees zero-downtime or safe emergency rollback during deployment failures.
- XML changesets are chosen for standardized, declarative Liquibase syntax with strict schema validation against Liquibase XSDs.

### Alternatives Considered
- **Flyway**: Flyway requires Flyway Teams (paid) for undo/rollback migrations, whereas Liquibase provides native, first-class rollback scripting in open-source versions.
- **Raw SQL Changesets without Rollback**: Rejected because automated disaster recovery and rollback verification in CI would be impossible.

---

## 3. PII Encryption at Rest & Log Masking

### Decision
1. **PII Encryption at Rest**:
   - Algorithm: `AES/GCM/NoPadding` with 256-bit key, 96-bit (12-byte) initialization vector (IV), and 128-bit authentication tag.
   - Delivery: Spring JPA Attribute Converter (`@Converter` implementing `AttributeConverter<String, String>`).
   - Secure Key Management: 256-bit Base64-encoded key injected via environment variable `PII_ENCRYPTION_KEY` (or AWS Secrets Manager / HashiCorp Vault in staging/production).
   - Storage format: Base64-encoded payload containing `[IV (12 bytes) || Ciphertext || AuthTag (16 bytes)]`.

2. **PII Masking in Logs**:
   - Custom Logback `CompositeConverter` / `PatternLayout` registered in `logback-spring.xml`.
   - Regular expression pattern masking for email addresses (`[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` -> `***@***.com`), phone numbers (`(\+?\d{1,3}[- ]?)?\d{10}` -> `***-***-XXXX`), and credit card / sensitive token strings.
   - Structured JSON logging using Logstash Logback Encoder (`net.logstash.logback:logstash-logback-encoder`) for `dev`, `staging`, and `production` profiles.

3. **Request Tracing & Correlation**:
   - `CorrelationIdFilter` (`OncePerRequestFilter`) inspects `X-Correlation-ID` header or generates a UUID if absent.
   - Sets correlation ID in SLF4J MDC (`MDC.put("correlationId", id)`).
   - Echoes `X-Correlation-ID` in the HTTP response headers.

### Rationale
- Complies with Constitution Principle I & Technology Standards.
- Meets FR-005, FR-007, and SC-007 (0 plain-text PII in logs or storage).
- MDC propagation guarantees all logs produced during the HTTP thread execution automatically include the correlation ID.

### Alternatives Considered
- **Hibernate Types / Database Column Encryption (pgcrypto)**: Database-level pgcrypto requires DB-side key management and increases DB CPU load, whereas application-level encryption ensures secrets never enter PostgreSQL in cleartext and supports multi-cloud portability.

---

## 4. Super Admin First-Run Bootstrap & Seeding

### Decision
Implement an idempotent `SuperAdminInitializer` as a Spring Boot `ApplicationRunner` / `SmartInitializingSingleton` with order executed after Liquibase migration.
- **Workflow**:
  1. Executes within a `@Transactional` block upon application ready.
  2. Checks whether any active account with `SUPER_ADMIN` role exists in the database.
  3. If found, logs an informational message and skips creation (idempotent).
  4. If not found, retrieves credentials from environment/profile properties:
     - `finflow.security.seed.super-admin.email` (default: dev/local only; required from vault in prod)
     - `finflow.security.seed.super-admin.password`
     - `finflow.security.seed.super-admin.first-name`
     - `finflow.security.seed.super-admin.last-name`
  5. Hashes the password using BCrypt (cost factor 12).
  6. Encrypts PII fields (email, name) via AES-256-GCM converter.
  7. Persists the Super Admin record with `ACTIVE` status and flags `must_change_password = true`.
  8. Automatically records the creation in `users_aud` via the `AuditInterceptor` with `modified_by = 'SYSTEM_BOOTSTRAP'`.

### Rationale
- Fulfills FR-004.1 through FR-004.4 and SC-003.
- Executing via `ApplicationRunner` ensures the database schema (and initial roles/privileges tables) are already migrated by Liquibase.
- Avoids hardcoded credentials in source control; requires explicit environment configuration in production.

### Alternatives Considered
- **SQL / Liquibase Data Seed Script**: Storing default credentials in XML/SQL changeset would risk hardcoding credentials in version control or running before application encryption beans are initialized.

---

## 5. Global Exception Handling (RFC 7807)

### Decision
Implement `GlobalExceptionHandler` extending `ResponseEntityExceptionHandler` annotated with `@RestControllerAdvice`.
- Uses Spring 6 / Spring Boot 3 `ProblemDetail` class conforming to RFC 7807.
- Fields returned:
  - `type`: URI identifying the error category (e.g. `https://api.finflow.com/errors/resource-not-found`)
  - `title`: Short human-readable summary
  - `status`: HTTP status code integer
  - `detail`: Safe, non-sensitive explanation of the occurrence
  - `instance`: Request URI path
  - `properties`:
    - `correlationId`: The MDC correlation ID for log tracing
    - `timestamp`: UTC instant
    - `errors`: List of field validation errors (for 400 Bad Request)
- Exception mapping:
  - `ResourceNotFoundException` -> 404 Not Found
  - `MethodArgumentNotValidException` / `ConstraintViolationException` -> 400 Bad Request
  - `AccessDeniedException` -> 403 Forbidden
  - `AuthenticationException` -> 401 Unauthorized
  - `DecryptionFailureException` -> 449 Retry With New Token (per Constitution / Backend spec)
  - `Exception` (catch-all) -> 500 Internal Server Error (logs full stack trace server-side with correlation ID; returns generic "An unexpected error occurred" to client)

### Rationale
- Enforces Constitution Technology Standard: "All API error responses MUST conform to RFC 7807 Problem Details format using the shared GlobalExceptionHandler."
- Eliminates stack trace leakage in client responses while retaining full forensic logs.

---

## 6. Multi-Environment Profile & Secrets Management

### Decision
- **Spring Profiles**: `local`, `dev`, `staging`, `production`.
- **Configuration Hierarchy**:
  - `application.yml`: Shared defaults, actuator configs, Liquibase master path, Jackson configs.
  - `application-local.yml`: Local PostgreSQL (`localhost:5432`), Redis (`localhost:6379`), human-readable logging, permissive CORS for `localhost:5173`.
  - `application-dev.yml`: Development environment configurations.
  - `application-staging.yml`: Staging configurations, structured JSON logging, strict CORS, vault secrets resolution.
  - `application-production.yml`: Production configurations, strict CORS, enforced TLS/vault secrets, strict validation.
- **Secrets Validation**:
  - A `SecretsValidatorEnvironmentPostProcessor` or `@PostConstruct` bean on `SecurityProperties` verifies mandatory keys (`PII_ENCRYPTION_KEY`, `DATABASE_PASSWORD`, `JWT_SECRET`) at startup.
  - If any mandatory secret is missing or insecure (e.g. placeholder value in production), application startup halts immediately with code 1 and a descriptive log message.

### Rationale
- Fulfills FR-001.1–FR-001.3 and FR-002.1–FR-002.2.
- Prevents accidental boots with misconfigured credentials.

---

## 7. Redis Connection Pool & Resilience

### Decision
- **Client**: Lettuce connection pool (`io.lettuce:lettuce-core`) configured via Spring Data Redis (`spring.data.redis`).
- **Connection Configuration**:
  - Connection pooling with `commons-pool2` (`GenericObjectPoolConfig`).
  - Timeout defaults: Connect timeout 2000ms, command timeout 1000ms.
- **Startup Validation**:
  - An `ApplicationRunner` (`RedisHealthValidator`) executes `redisConnectionFactory.getConnection().ping()`.
  - In `local` and `dev` profiles: logs a `WARN` if Redis is unreachable but allows application startup.
  - In `staging` and `production` profiles: logs an `ERROR` and aborts startup with an `IllegalStateException`.

### Rationale
- Directly addresses FR-008.1 and FR-008.2.
- Provides seamless local development while safeguarding production environments from running without cache/session state storage.

---

## 8. CORS Policy & Security Headers

### Decision
- Implement `CorsConfigurationSource` bean configured via environment properties (`finflow.cors.allowed-origins`, `finflow.cors.allowed-methods`, etc.).
- In `production` and `staging`:
  - Wildcard `*` is explicitly validated and rejected at startup.
  - Origins must be explicit HTTPS URIs (e.g. `https://app.finflow.com`).
  - Allowed methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
  - Allowed headers: `Authorization, Content-Type, X-Correlation-ID, X-Id-Token`.
  - Exposed headers: `X-Correlation-ID`.
  - Max age: 3600 seconds.

### Rationale
- Fulfills FR-006.1–FR-006.3.
- Prevents cross-origin data leakage and unauthorized browser API access.

---

## Summary of Decisions

| Area | Decision | Constitution / Spec Reference |
|---|---|---|
| **Audit Interceptor** | Hibernate/JPA event interceptor with ACID shadow table write & JSONB diff | Principle III, FR-009 |
| **Database Migrations** | Liquibase XML changesets with mandatory `<rollback>` blocks | Principle V, FR-003 |
| **PII Protection** | AES-256-GCM JPA converters; Logback PII masking converter | Principle I, FR-007.3 |
| **ID Protection** | Foundation for AES session ID encryption / token filter | Principle I, Section 21 |
| **Super Admin Bootstrap** | Post-migration `ApplicationRunner`, idempotent, encrypted PII | FR-004 |
| **Error Handling** | Spring `ProblemDetail` (RFC 7807) via `@RestControllerAdvice` | Tech Standards, FR-005 |
| **Environment & Secrets**| Spring profiles + fail-fast startup validator | FR-001, FR-002 |
| **Cache Connectivity** | Lettuce pooled factory + profile-based startup ping check | FR-008 |
| **CORS Policy** | Profile-driven `CorsConfigurationSource` rejecting wildcards in prod | FR-006 |
