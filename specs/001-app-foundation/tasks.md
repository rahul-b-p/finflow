# Tasks: Module 1 — Application Foundation

**Branch**: `001-app-foundation`  
**Input Documents**: `plan.md`, `spec.md`, `data-model.md`, `research.md`, `quickstart.md`, `contracts/`  
**Governance**: `.specify/memory/constitution.md` (Principles I–V, Tech Standards)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize the Maven Spring Boot 3.x project structure, core dependencies, build plugins, and multi-profile configuration files.

- [X] T001 Initialize Maven build configuration with Java 17 and Spring Boot 3.3.x dependencies (Web, Data JPA, Security, Redis, Actuator, Validation, Liquibase, PostgreSQL, Logstash Logback, Springdoc OpenAPI) in `backend/pom.xml`
- [X] T002 [P] Configure Maven wrapper scripts and properties in `backend/mvnw` and `backend/.mvn/wrapper/maven-wrapper.properties`
- [X] T003 [P] Create modular package directory skeleton (`com.finflow.config`, `com.finflow.core.audit`, `com.finflow.core.crypto`, `com.finflow.core.exception`, `com.finflow.core.filter`, `com.finflow.core.logging`, `com.finflow.core.startup`, `com.finflow.modules.foundation`) in `backend/src/main/java/`
- [X] T004 [P] Create Spring Boot main entrypoint class `FinflowApplication.java` in `backend/src/main/java/com/finflow/FinflowApplication.java`
- [X] T005 [P] Create base application configuration with server port 8080, JPA Hibernate naming, Liquibase enablement, and actuator endpoints in `backend/src/main/resources/application.yml`
- [X] T006 [P] Create local profile configuration with PostgreSQL (`localhost:5432/finflow`), Redis (`localhost:6379`), and console logging in `backend/src/main/resources/application-local.yml`
- [X] T007 [P] Create dev profile configuration with development database and test settings in `backend/src/main/resources/application-dev.yml`
- [X] T008 [P] Create staging and production profile configurations with strict external secrets placeholders, disabled console logs, and SSL JDBC properties in `backend/src/main/resources/application-staging.yml` and `backend/src/main/resources/application-production.yml`
- [X] T009 [P] Configure Logback logging with structured JSON Logstash encoder in `backend/src/main/resources/logback-spring.xml`
- [X] T010 [P] Create test application configuration with in-memory H2 or Testcontainers PostgreSQL properties in `backend/src/test/resources/application-test.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core security, crypto, logging, and startup verification infrastructure that MUST be completed before any user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [X] T011 Implement `CryptoUtils` supporting AES-256-GCM encryption/decryption (`AES/GCM/NoPadding`, 96-bit IV, 128-bit authentication tag) in `backend/src/main/java/com/finflow/core/crypto/CryptoUtils.java`
- [X] T012 Implement JPA `AttributeConverter<String, String>` using `CryptoUtils` for transparent PII persistence in `backend/src/main/java/com/finflow/core/crypto/AesGcmCryptoConverter.java`
- [X] T013 [P] Implement unit tests for `CryptoUtils` and `AesGcmCryptoConverter` verifying encryption, decryption, tampering detection, and null handling in `backend/src/test/java/com/finflow/core/crypto/AesGcmCryptoConverterTest.java`
- [X] T014 [P] Implement `PiiMaskingPatternLayout` / Logback composite converter with regex patterns for email (`[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}`), phone numbers, and auth tokens in `backend/src/main/java/com/finflow/core/logging/PiiMaskingPatternLayout.java`
- [X] T015 Implement `SecretsValidator` (`ApplicationRunner`) to verify presence and strength of `PII_ENCRYPTION_KEY`, `DATABASE_PASSWORD`, and `JWT_SECRET` at startup in `backend/src/main/java/com/finflow/core/startup/SecretsValidator.java`
- [X] T016 [P] Implement `BaseException` and domain exception hierarchy (`ResourceNotFoundException`, `DecryptionFailureException`, `InvalidConfigurationException`) in `backend/src/main/java/com/finflow/core/exception/BaseException.java`
- [X] T017 Implement Spring Security baseline configuration with `@EnableMethodSecurity(prePostEnabled = true)` and stateless session creation in `backend/src/main/java/com/finflow/config/SecurityConfig.java`

**Checkpoint**: Foundation ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Super Admin Idempotent Seeding & User Schema (Priority: P1) 🎯 MVP

**Goal**: Ensure a default Super Admin account is automatically and idempotently provisioned on first startup with BCrypt-hashed credentials and encrypted PII fields when no Super Admin exists.

**Independent Test**: Start the application on an empty database. Verify one `SUPER_ADMIN` record exists in `users` with `status = 'ACTIVE'`, `must_change_password = true`, and encrypted `email`. Restart the application and verify no duplicate accounts are created.

### Tests for User Story 1
- [X] T019 [P] [US1] Integration test with Testcontainers PostgreSQL verifying `User` entity persistence with `AesGcmCryptoConverter` in `backend/src/test/java/com/finflow/modules/foundation/UserRepositoryTest.java`

### Implementation for User Story 1
- [X] T020 [P] [US1] Implement `Role` JPA entity mapping `roles` table with constraints `name VARCHAR(50) PRIMARY KEY` and `description VARCHAR(255) NOT NULL` in `backend/src/main/java/com/finflow/modules/foundation/entity/Role.java`
- [X] T021 [US1] Implement `User` JPA entity mapping `users` table with constraints `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `email VARCHAR(500) NOT NULL UNIQUE` (with `@Convert(converter = AesGcmCryptoConverter.class)`), `password_hash VARCHAR(255) NOT NULL`, `first_name VARCHAR(255) NOT NULL` (with `@Convert`), `last_name VARCHAR(255) NOT NULL` (with `@Convert`), `phone VARCHAR(255)` (with `@Convert`, nullable), `status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE'` (allowed values: `'ACTIVE', 'PENDING_APPROVAL', 'REJECTED', 'SUSPENDED'`), `must_change_password BOOLEAN NOT NULL DEFAULT FALSE`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, and `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()` in `backend/src/main/java/com/finflow/modules/foundation/entity/User.java`
- [X] T022 [P] [US1] Implement `RoleRepository` and `UserRepository` with Spring Data JPA query methods in `backend/src/main/java/com/finflow/modules/foundation/repository/RoleRepository.java` and `backend/src/main/java/com/finflow/modules/foundation/repository/UserRepository.java`
- [X] T023 [US1] Implement `SuperAdminInitializer` (`ApplicationRunner`) reading credentials from `finflow.security.seed.super-admin`, hashing password with BCrypt cost 12, assigning `SUPER_ADMIN` role, and persisting idempotently in `backend/src/main/java/com/finflow/core/startup/SuperAdminInitializer.java`

**Checkpoint**: At this point, User Story 1 is functional and verifiable independently.

---

## Phase 4: User Story 2 - Versioned Liquibase Migration Execution & Rollback Safety (Priority: P1)

**Goal**: Execute database migrations automatically at startup using Liquibase with explicit, tested `<rollback>` blocks for every changeset, and prevent concurrent migrations using migration locks.

**Independent Test**: Run `./mvnw liquibase:update-testing-rollback` on a PostgreSQL database and verify all changesets apply, rollback, and re-apply without schema or data corruption.

### Tests for User Story 2
- [X] T024 [P] [US2] Automated rollback test executing Liquibase `update-testing-rollback` against a Testcontainers PostgreSQL instance in `backend/src/test/java/com/finflow/core/migration/LiquibaseRollbackTest.java`

### Implementation for User Story 2
- [X] T025 [US2] Create Liquibase master changelog importing version directories in `backend/src/main/resources/db/changelog/db.changelog-master.xml`
- [X] T026 [US2] Create version changelog file for v1.0.0 in `backend/src/main/resources/db/changelog/v1.0.0/db.changelog-v1.0.0.xml`
- [X] T027 [US2] Author changeset `v1.0.0-001-create-app-foundation-tables.xml` creating `roles`, `users`, and `user_roles` tables with constraints and mandatory `<rollback>` drop table blocks in `backend/src/main/resources/db/changelog/v1.0.0/001-create-app-foundation-tables.xml`
- [X] T028 [US2] Author changeset `v1.0.0-002-seed-initial-roles.xml` inserting default roles (`SUPER_ADMIN`, `ADMIN`, `END_USER`) with mandatory `<rollback><delete tableName="roles"/></rollback>` in `backend/src/main/resources/db/changelog/v1.0.0/002-seed-initial-roles.xml`
- [X] T029 [US2] Author changeset `v1.0.0-003-create-audit-shadow-tables.xml` creating `users_aud` shadow table with columns `audit_id BIGSERIAL PRIMARY KEY`, `id UUID NOT NULL`, `rev_type VARCHAR(10) NOT NULL`, `diff_data JSONB`, `modified_by VARCHAR(100) NOT NULL`, `ip_address VARCHAR(45)`, `rev_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()`, indexes, and mandatory `<rollback>` in `backend/src/main/resources/db/changelog/v1.0.0/003-create-audit-shadow-tables.xml`

**Checkpoint**: At this point, User Stories 1 AND 2 are functional and verifiable independently.

---

## Phase 5: User Story 3 - Shared ACID Shadow Audit Interceptor (Priority: P1)

**Goal**: Provide a shared, transactional audit interceptor that captures all entity mutations into `*_aud` shadow tables within the same ACID transaction, recording diff JSONB without raw PII.

**Independent Test**: Mutate a `User` entity within a transaction. Verify a corresponding record is written to `users_aud` with matching UUID, `rev_type = 'UPDATE'`, `modified_by`, `ip_address`, and `diff_data`. Cause transaction rollback and verify no record exists in `users_aud`.

### Tests for User Story 3
- [X] T030 [P] [US3] Transactional integration test verifying `users_aud` record commit upon entity save and rollback upon exception in `backend/src/test/java/com/finflow/core/audit/AuditInterceptorTest.java`

### Implementation for User Story 3
- [X] T031 [P] [US3] Create `AuditableEntity` marker interface and `@Auditable` annotation in `backend/src/main/java/com/finflow/core/audit/AuditableEntity.java` and `backend/src/main/java/com/finflow/core/audit/Auditable.java`
- [X] T032 [P] [US3] Create `AuditRecord` model conforming to `specs/001-app-foundation/contracts/audit-event.json` in `backend/src/main/java/com/finflow/core/audit/AuditRecord.java`
- [X] T033 [US3] Implement `EntityDiffGenerator` to compute field-level deltas between old and new state, automatically masking any sensitive fields (`email`, `phone`, `passwordHash`) in `backend/src/main/java/com/finflow/core/audit/EntityDiffGenerator.java`
- [X] T034 [US3] Implement `EntityAuditService` executing SQL insertion into `<entity>_aud` shadow tables using `JdbcTemplate` within the current active transaction in `backend/src/main/java/com/finflow/core/audit/EntityAuditService.java`
- [X] T035 [US3] Implement `AuditInterceptor` (JPA EntityListener / Hibernate Post-Mutation callback) hooking into `User` and future auditable entities in `backend/src/main/java/com/finflow/core/audit/AuditInterceptor.java`

**Checkpoint**: At this point, User Stories 1, 2, and 3 work together with full transactional audit integrity.

---

## Phase 6: User Story 4 - Request Correlation Tracing & PII-Masked Logging (Priority: P2)

**Goal**: Assign a unique correlation ID to every incoming HTTP request, propagate it through MDC for structured logs, echo it in the response header `X-Correlation-ID`, and ensure no PII reaches logs.

**Independent Test**: Send HTTP request with `X-Correlation-ID: test-id-123`. Verify response header has `X-Correlation-ID: test-id-123` and application log output contains `test-id-123`. Send request with email in body and verify email is masked in logs.

### Tests for User Story 4
- [X] T036 [P] [US4] MockMvc test verifying `CorrelationIdFilter` assigns UUID when absent and preserves existing `X-Correlation-ID` in `backend/src/test/java/com/finflow/core/filter/CorrelationIdFilterTest.java`
- [X] T037 [P] [US4] Unit test verifying `RequestLoggingFilter` outputs duration, method, and URI without logging sensitive headers in `backend/src/test/java/com/finflow/core/filter/RequestLoggingFilterTest.java`

### Implementation for User Story 4
- [X] T038 [US4] Implement `CorrelationIdFilter` (`OncePerRequestFilter`) extracting or generating `X-Correlation-ID`, populating SLF4J MDC, and attaching header to `HttpServletResponse` in `backend/src/main/java/com/finflow/core/filter/CorrelationIdFilter.java`
- [X] T039 [US4] Implement `RequestLoggingFilter` logging HTTP method, URI, status code, and latency in milliseconds with MDC correlation context in `backend/src/main/java/com/finflow/core/filter/RequestLoggingFilter.java`
- [X] T040 [US4] Register `CorrelationIdFilter` and `RequestLoggingFilter` in Spring filter chain with appropriate precedence in `backend/src/main/java/com/finflow/config/WebMvcConfig.java`

**Checkpoint**: Request tracing and correlation active across the foundation.

---

## Phase 7: User Story 5 - Centralized RFC 7807 Problem Details Error Handling (Priority: P2)

**Goal**: Centralize exception handling to return consistent RFC 7807 Problem Details JSON responses (`type`, `title`, `status`, `detail`, `instance`, `correlationId`) and prevent internal stack traces from leaking to clients.

**Independent Test**: Trigger a 400 Bad Request and a 404 Not Found. Verify `Content-Type: application/problem+json`, all RFC 7807 fields are present, correlation ID matches MDC, and 0 stack traces appear in the response payload.

### Tests for User Story 5
- [X] T041 [P] [US5] WebMvcTest verifying `GlobalExceptionHandler` renders RFC 7807 Problem Details for `ResourceNotFoundException`, validation failures, and generic 500 in `backend/src/test/java/com/finflow/core/exception/GlobalExceptionHandlerTest.java`

### Implementation for User Story 5
- [X] T042 [P] [US5] Implement RFC 7807 DTO models (`ProblemDetailDto`, `FieldErrorDto`) conforming to `specs/001-app-foundation/contracts/foundation-api.yaml` in `backend/src/main/java/com/finflow/core/exception/ProblemDetailDto.java`
- [X] T043 [US5] Implement `GlobalExceptionHandler` (`@RestControllerAdvice` extending `ResponseEntityExceptionHandler`) handling `MethodArgumentNotValidException`, `AccessDeniedException`, `ResourceNotFoundException`, and uncaught `Exception` in `backend/src/main/java/com/finflow/core/exception/GlobalExceptionHandler.java`

**Checkpoint**: Centralized error handling active and conforming to API contracts.

---

## Phase 8: User Story 6 - Shared Redis Connection Pool & Startup Health Resilience (Priority: P2)

**Goal**: Establish a shared Redis Lettuce connection pool with environment-sensitive validation (warn on local/dev if unreachable, fail-fast on staging/prod).

**Independent Test**: Start application in `dev` without Redis running — verify warning log and startup success. Start in `prod` without Redis running — verify immediate startup halt with clear error message.

### Tests for User Story 6
- [X] T044 [P] [US6] Unit test for `RedisHealthValidator` verifying behavior across `dev` vs. `prod` profiles in `backend/src/test/java/com/finflow/core/startup/RedisHealthValidatorTest.java`

### Implementation for User Story 6
- [X] T045 [US6] Configure `RedisConnectionFactory` with Lettuce client and `GenericObjectPoolConfig` connection pooling in `backend/src/main/java/com/finflow/config/RedisConfig.java`
- [X] T046 [US6] Implement `RedisHealthValidator` (`ApplicationRunner`) executing Redis ping at startup with profile-dependent tolerance (warn on `local`/`dev`, fail-fast on `staging`/`prod`) in `backend/src/main/java/com/finflow/core/startup/RedisHealthValidatorTest.java` (in `backend/src/main/java/com/finflow/core/startup/RedisHealthValidator.java`)

**Checkpoint**: Redis connection pool configured and guarded by startup health check.

---

## Phase 9: User Story 7 - Environment-Specific CORS Policy Enforcement (Priority: P3)

**Goal**: Enforce strict, profile-driven CORS origins, headers, and methods; handle preflight `OPTIONS` requests; reject wildcard `*` in staging/production.

**Independent Test**: Send `OPTIONS` request with `Origin: http://localhost:5173` in `dev` — verify 200 OK with `Access-Control-Allow-Origin`. Test with unauthorized origin — verify CORS rejection.

### Tests for User Story 7
- [X] T047 [P] [US7] MockMvc integration test verifying CORS preflight handling for allowed and rejected origins in `backend/src/test/java/com/finflow/config/CorsConfigTest.java`

### Implementation for User Story 7
- [X] T048 [P] [US7] Implement `CorsProperties` (`@ConfigurationProperties(prefix = "finflow.cors")`) with validation rejecting wildcard `*` when active profile is `staging` or `production` in `backend/src/main/java/com/finflow/config/CorsProperties.java`
- [X] T049 [US7] Implement `CorsConfigurationSource` bean configuring allowed origins, methods (`GET, POST, PUT, PATCH, DELETE, OPTIONS`), allowed headers, and exposed headers (`X-Correlation-ID`) in `backend/src/main/java/com/finflow/config/CorsConfig.java`

**Checkpoint**: CORS policy enforced cleanly across all environments.

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Actuator health checks, OpenAPI contract verification, static analysis, and end-to-end quickstart validation.

- [X] T050 [P] Configure Springdoc OpenAPI UI and register foundation API contract in `backend/src/main/java/com/finflow/config/OpenApiConfig.java`
- [X] T051 [P] Configure Spring Boot Actuator health indicators for database and Redis in `backend/src/main/resources/application.yml`
- [X] T052 Execute quickstart validation scenarios (Liquibase rollback, Super Admin seeding, health probe, RFC 7807 error, correlation trace) documented in `specs/001-app-foundation/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 completion — **BLOCKS all user stories**.
- **Phase 3+ (User Stories)**: Depend on Phase 2 completion.
  - User Story 1 (P1 - Super Admin): Depends on Phase 2 and Liquibase tables (Phase 4).
  - User Story 2 (P1 - Liquibase): Depends on Phase 2.
  - User Story 3 (P1 - Audit): Depends on Phase 2 and User Story 1 & 2.
  - User Story 4 (P2 - Tracing & Logging): Depends on Phase 2.
  - User Story 5 (P2 - RFC 7807 Error Handling): Depends on Phase 2.
  - User Story 6 (P2 - Redis Connection Pool): Depends on Phase 2.
  - User Story 7 (P3 - CORS Policy): Depends on Phase 2.
- **Phase 10 (Polish)**: Depends on all user stories being complete.

### Story Completion Order (Recommended Execution Flow)
```
Phase 1: Setup 
   ↓
Phase 2: Foundational (Crypto, Secrets, Base Exceptions)
   ↓
Phase 4: US2 (Liquibase Schema & Tables)
   ↓
Phase 3: US1 (Super Admin Seeding & User Entity) 🎯 MVP
   ↓
Phase 5: US3 (Shared ACID Audit Interceptor)
   ↓
Phase 6: US4 (Correlation & Tracing) || Phase 7: US5 (RFC 7807 Errors) || Phase 8: US6 (Redis)
   ↓
Phase 9: US7 (CORS Policy)
   ↓
Phase 10: Polish & Quickstart Runbook
```

---

## Parallel Execution Examples

### Setup Phase Parallelism
```bash
# Can be authored simultaneously:
T002 [P] Configure Maven wrapper scripts in backend/mvnw
T003 [P] Create modular package directory skeleton in backend/src/main/java/
T006 [P] Create local profile configuration in backend/src/main/resources/application-local.yml
T007 [P] Create dev profile configuration in backend/src/main/resources/application-dev.yml
T009 [P] Configure Logback logging in backend/src/main/resources/logback-spring.xml
```

### User Story 1 Parallelism
```bash
# Can run concurrently:
T018 [P] [US1] Unit test for SuperAdminInitializer in backend/src/test/.../SuperAdminInitializerTest.java
T019 [P] [US1] Integration test for UserRepository in backend/src/test/.../UserRepositoryTest.java
T020 [P] [US1] Implement Role JPA entity in backend/src/main/.../entity/Role.java
T022 [P] [US1] Implement RoleRepository and UserRepository in backend/src/main/.../repository/
```

---

## Implementation Strategy

### MVP First (User Story 1 & Liquibase Foundation)
1. Complete **Phase 1: Setup** (`pom.xml`, configurations, packages).
2. Complete **Phase 2: Foundational** (AES-256 crypto converter, secrets validation).
3. Complete **Phase 4: US2** (Liquibase changesets for `roles`, `users`, `users_aud`).
4. Complete **Phase 3: US1** (Super Admin seeding and `User` entity).
5. **STOP and VALIDATE**: Verify Super Admin creation in database with encrypted PII and BCrypt password.

### Incremental Delivery
1. Foundation + US2 + US1 = Working bootstrapped application with DB schema and admin access (MVP).
2. Add US3 = ACID shadow table audit logging active across all entity mutations.
3. Add US4 + US5 = Complete request correlation tracing and RFC 7807 error responses.
4. Add US6 + US7 = Redis pool resilience and secure CORS enforcement.
5. Polish = Actuator health verification, OpenAPI docs, and full quickstart execution.
