# Implementation Plan: Module 2 — Authentication

**Branch**: `002-authentication` | **Date**: 2026-10-12 | **Spec**: [specs/002-authentication/spec.md](file:///home/user/Documents/java-projects/finflow/specs/002-authentication/spec.md)

**Input**: Feature specification from `/specs/002-authentication/spec.md`

## Summary

Module 2 delivers the complete identity lifecycle, multi-factor authentication, and secure session management foundation for FinFlow. Key capabilities include:
- **User Self-Registration with Approval Hold**: New registrations capture encrypted PII and BCrypt-hashed credentials, entering `PENDING_APPROVAL` status by default.
- **Session Issuance & Ephemeral ID Obfuscation**: Authenticated users receive a 15-minute JWT access token and 7-day refresh token in Redis. All database UUIDs are wrapped in session-scoped AES-GCM encrypted tokens before serialization. Corrupted ID decryption attempts trigger HTTP 449; three consecutive failures terminate the session.
- **RFC 6238 TOTP Two-Factor Authentication**: Generates 160-bit Base32 secrets (encrypted at rest), provides standard QR pairing URIs, and enforces a 5-minute login MFA challenge exchange with replay prevention.
- **Google OAuth2 Federated Authentication**: Authorization code flow with email verification check, automated account linking, or pending user onboarding.
- **Token Refresh & Revocation**: Refresh token rotation with a 15-second grace window to absorb concurrent browser calls; instant session revocation on explicit logout.
- **Self-Service Password Recovery**: 15-minute 6-digit numeric OTP in Redis with attempt limits (max 5) and immediate cross-session invalidation upon reset.
- **ACID Shadow Audit**: All identity mutations recorded in `users_aud`, `user_totp_credentials_aud`, and `federated_identities_aud` in the same transaction.

---

## Technical Context

**Language/Version**: Java 17 LTS / Spring Boot 3.3.x  
**Primary Dependencies**:
- `org.springframework.boot:spring-boot-starter-web`
- `org.springframework.boot:spring-boot-starter-data-jpa`
- `org.springframework.boot:spring-boot-starter-security`
- `org.springframework.boot:spring-boot-starter-data-redis`
- `org.springframework.boot:spring-boot-starter-validation`
- `io.jsonwebtoken:jjwt-api:0.12.5` (`jjwt-impl`, `jjwt-jackson`)
- `org.liquibase:liquibase-core`
- `org.postgresql:postgresql`
- `org.apache.commons:commons-pool2`
- `net.logstash.logback:logstash-logback-encoder`
- `org.springdoc:springdoc-openapi-starter-webmvc-ui`

**Storage**:
- PostgreSQL 15+ (relational user records, TOTP credentials, federated links, and shadow audit tables)
- Redis 7+ (active session registry, refresh token index, 15-second grace window, password reset OTPs, MFA challenge tokens, and rate limits)

**Testing**: JUnit 5, AssertJ, Mockito, Spring Security Test, Testcontainers (PostgreSQL, Redis), `mvn liquibase:update-testing-rollback`  
**Target Platform**: Linux server / Container runtime (Docker multi-stage JDK build, Docker Compose)  
**Project Type**: Web service (modular monolith backend)  
**Performance Goals**: User login and token refresh P95 response time ≤ 200ms; ID obfuscation/decryption overhead ≤ 2ms  
**Constraints**:
- All PII fields (email, names, phone) encrypted at rest using AES-256-GCM (`AesGcmCryptoConverter`).
- Zero database UUIDs exposed over the wire; wrapped in session-scoped AES-GCM tokens.
- Spring Security method-level `@PreAuthorize` with explicit privilege codes (`AUTH:LOGIN`, `AUTH:UPDATE_PROFILE`, etc.).
- Authentication tokens issued only to `ACTIVE` accounts (`PENDING_APPROVAL`, `REJECTED`, `SUSPENDED` denied).
- Every mutating operation writes to corresponding `*_aud` shadow table in the same transaction.
- Every Liquibase changeset must provide an explicit `<rollback>` block.
- Error responses conform to RFC 7807 Problem Details.
- Suppress and mask all passwords, OTPs, TOTP secrets, and JWT tokens from logs.

**Scale/Scope**: Core authentication module serving all 8 monolith modules and frontend clients.

---

## Constitution Check

*GATE: Evaluated before Phase 0 and re-verified post Phase 1 design.*

| Principle / Standard | Status | Validation / Enforcement Mechanism |
|:---|:---|:---|
| **I. Security & Trust: AES-256-GCM PII Encryption** | PASS | `AesGcmCryptoConverter` applied to `email`, `first_name`, `last_name`, `phone`, and `secret`. |
| **I. Security & Trust: PK Obfuscation Foundation** | PASS | Session-bound AES-GCM ID obfuscation filter/converters; wire payload uses `id_...`. |
| **I. Security & Trust: Method-level Security** | PASS | `@PreAuthorize("hasAuthority('MODULE:OPERATION')")` on all authenticated endpoints. |
| **I. Security & Trust: ACTIVE Status Requirement** | PASS | Security filter & auth service verify account status before issuing any session tokens. |
| **I. Security & Trust: 3 Decryption Failure Rule** | PASS | Redis tracks consecutive decryption failures; 3rd failure terminates session and returns HTTP 401. |
| **II. Contract-First API Design** | PASS | Defined in `specs/002-authentication/contracts/auth-api.yaml` (OpenAPI 3.1) and `session-token.json`. |
| **III. Audit Everything: Same ACID Transaction** | PASS | `AuditInterceptor` writes to `users_aud`, `user_totp_credentials_aud`, `federated_identities_aud`. |
| **III. Audit Everything: No PII in Audit Logs** | PASS | Diff generator masks sensitive credentials/PII prior to persisting `diff_data` JSONB. |
| **IV. RBAC Enforced at Every Layer** | PASS | Fine-grained privileges encoded into JWT claims and validated at Spring Security method level. |
| **V. Versioned, Rollback-Ready Migrations** | PASS | Liquibase changeset `004-create-auth-module-tables.xml` with complete `<rollback>` blocks. |
| **Tech Standards: Java 17+ & Spring Boot 3.x** | PASS | Java 17, Spring Boot 3.3.4 parent POM. |
| **Tech Standards: Redis for Sessions** | PASS | Redis stores session state, refresh tokens, and OTPs; zero in-memory maps. |
| **Tech Standards: RFC 7807 Error Responses** | PASS | `GlobalExceptionHandler` handles `ProblemDetail` with correlation ID and custom error codes. |

---

## Project Structure

### Documentation (this feature)

```text
specs/002-authentication/
├── spec.md              # Feature specification
├── plan.md              # This implementation plan
├── research.md          # Phase 0 architectural decisions & rationale
├── data-model.md        # Phase 1 database schema, Redis keys, and entity design
├── quickstart.md        # Phase 1 end-to-end validation scenarios & runbook
└── contracts/           # Phase 1 interface specifications
    ├── auth-api.yaml    # OpenAPI 3.1 contracts
    └── session-token.json # JWT claims JSON schema
```

### Source Code Layout (repository root)

```text
backend/
├── pom.xml
├── api/
│   └── openapi/
│       ├── finflow-foundation-api.yaml
│       └── finflow-auth-api.yaml
├── src/
│   ├── main/
│   │   ├── java/com/finflow/
│   │   │   ├── config/
│   │   │   │   ├── SecurityConfig.java            # Updated with JWT Filter and public auth paths
│   │   │   │   └── JwtProperties.java             # JWT secret, expiration, and issuer settings
│   │   │   ├── core/
│   │   │   │   ├── crypto/
│   │   │   │   │   ├── IdObfuscationService.java  # Session-bound AES ID encrypt/decrypt
│   │   │   │   │   └── TotpUtils.java             # RFC 6238 HMAC-SHA1 TOTP implementation
│   │   │   │   ├── exception/
│   │   │   │   │   ├── IdDecryptionException.java # Mapped to HTTP 449 in GlobalExceptionHandler
│   │   │   │   │   ├── AccountNotActiveException.java
│   │   │   │   │   └── InvalidOtpException.java
│   │   │   │   └── security/
│   │   │   │       ├── JwtTokenProvider.java      # Access token generation & claim parsing
│   │   │   │       ├── JwtAuthenticationFilter.java # OncePerRequestFilter extracting Bearer JWT
│   │   │   │       └── UserPrincipal.java         # Spring Security UserDetails representation
│   │   │   └── modules/
│   │   │       ├── foundation/
│   │   │       │   └── ...
│   │   │       └── auth/
│   │   │           ├── controller/
│   │   │           │   ├── AuthController.java    # /api/v1/auth/* endpoints
│   │   │           │   ├── MfaController.java     # /api/v1/auth/mfa/* endpoints
│   │   │           │   └── OAuth2Controller.java  # /api/v1/auth/oauth2/* endpoints
│   │   │           ├── dto/
│   │   │           │   ├── request/               # RegistrationRequest, LoginRequest, etc.
│   │   │           │   └── response/              # AuthSuccessResponse, MfaChallengeResponse, etc.
│   │   │           ├── entity/
│   │   │           │   ├── UserTotpCredential.java
│   │   │           │   └── FederatedIdentity.java
│   │   │           ├── repository/
│   │   │           │   ├── UserTotpCredentialRepository.java
│   │   │           │   └── FederatedIdentityRepository.java
│   │   │           └── service/
│   │   │               ├── AuthService.java       # Registration, login, status validation
│   │   │               ├── SessionService.java    # Redis session registry, refresh, rotation
│   │   │               ├── TotpService.java       # Secret generation, pairing, verification
│   │   │               ├── GoogleOAuth2Service.java # Code exchange, linking, user provisioning
│   │   │               └── PasswordResetService.java # OTP generation, verification, session purge
│   │   └── resources/
│   │       └── db/changelog/
│   │           ├── db.changelog-v1.0.0.xml
│   │           └── v1.0.0/
│   │               └── 004-create-auth-module-tables.xml
│   └── test/
│       └── java/com/finflow/modules/auth/
│           ├── controller/AuthControllerTest.java
│           ├── service/AuthServiceTest.java
│           ├── service/TotpServiceTest.java
│           ├── service/SessionServiceTest.java
│           └── crypto/IdObfuscationServiceTest.java
```

**Structure Decision**: Modular monolith layout adhering to `com.finflow.modules.auth`. Generic ID obfuscation and TOTP utilities reside in `com.finflow.core` to enable transparent usage across all downstream modules.

---

## Complexity Tracking

*No violations identified. All designs strictly comply with Constitution Principles I–V and Technology Standards.*
