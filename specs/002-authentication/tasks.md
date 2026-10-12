# Tasks: Module 2 — Authentication

**Branch**: `002-authentication`  
**Input Documents**: `plan.md`, `spec.md`, `data-model.md`, `research.md`, `quickstart.md`, `contracts/`  
**Governance**: `.specify/memory/constitution.md` (Principles I–V, Tech Standards)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Module structure initialization, package skeletons, and API contract registration.

- [ ] T001 Verify and add JWT dependencies (`io.jsonwebtoken:jjwt-api:0.12.5`, `jjwt-impl`, `jjwt-jackson`) to Maven build in `backend/pom.xml`
- [ ] T002 [P] Create modular package directory skeleton (`com.finflow.modules.auth.controller`, `com.finflow.modules.auth.dto`, `com.finflow.modules.auth.entity`, `com.finflow.modules.auth.repository`, `com.finflow.modules.auth.service`, `com.finflow.core.security`) in `backend/src/main/java/`
- [ ] T003 [P] Copy OpenAPI 3.1 specification to backend contracts directory in `backend/api/openapi/finflow-auth-api.yaml`
- [ ] T004 [P] Configure JWT application properties mapping (`finflow.security.jwt.secret`, `expiration-ms=900000`, `refresh-expiration-ms=604800000`, `issuer=https://api.finflow.com`) in `backend/src/main/resources/application.yml` and create `backend/src/main/java/com/finflow/config/JwtProperties.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core security, database schema changesets, Redis session registry, and ID obfuscation infrastructure that MUST be completed before ANY user story can be implemented.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T005 Create Liquibase changeset `004-create-auth-module-tables.xml` adding `two_factor_enabled BOOLEAN NOT NULL DEFAULT FALSE`, `failed_login_attempts INT NOT NULL DEFAULT 0`, `last_login_at TIMESTAMPTZ` to `users` table; creating `user_totp_credentials` table with constraints `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `secret VARCHAR(500) NOT NULL`, `confirmed_at TIMESTAMPTZ`, `created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()`, `CONSTRAINT uk_user_totp_credentials_user UNIQUE (user_id)`; creating `federated_identities` table with constraints `id UUID PRIMARY KEY DEFAULT gen_random_uuid()`, `user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE`, `provider VARCHAR(50) NOT NULL`, `provider_subject_id VARCHAR(255) NOT NULL`, `email VARCHAR(500)`, `CONSTRAINT uk_federated_provider_sub UNIQUE (provider, provider_subject_id)`; creating shadow audit tables `user_totp_credentials_aud` and `federated_identities_aud` with columns `audit_id BIGSERIAL PRIMARY KEY`, `id UUID NOT NULL`, `rev_type VARCHAR(10) NOT NULL`, `diff_data JSONB`, `modified_by VARCHAR(100) NOT NULL`, `ip_address VARCHAR(45)`, `rev_timestamp TIMESTAMPTZ NOT NULL DEFAULT NOW()`; and providing complete `<rollback>` tags in `backend/src/main/resources/db/changelog/v1.0.0/004-create-auth-module-tables.xml`
- [ ] T006 Register changeset `004-create-auth-module-tables.xml` in version changelog `backend/src/main/resources/db/changelog/v1.0.0/db.changelog-v1.0.0.xml`
- [ ] T007 [P] Implement domain exceptions `IdDecryptionException`, `AccountNotActiveException`, `InvalidOtpException`, `MfaRequiredException`, and register them with RFC 7807 problem details in `backend/src/main/java/com/finflow/core/exception/GlobalExceptionHandler.java` (mapping `IdDecryptionException` to HTTP 449)
- [ ] T008 Implement `JwtTokenProvider` to generate and validate 15-minute access JWTs signed with HMAC-SHA256, embedding claims `sub` (obfuscated ID), `sid` (session ID), `roles`, `privileges`, `iss`, `iat`, `exp` matching `specs/002-authentication/contracts/session-token.json` in `backend/src/main/java/com/finflow/core/security/JwtTokenProvider.java`
- [ ] T009 Implement `SessionService` managing Redis key hierarchies `auth:session:{sessionId}` (storing `sessionId`, `userId`, `sessionEncryptionKey`, `decryptionFailures`, `userStatus`, `roles`, TTL 7 days), `auth:refresh:{tokenHash}`, and `auth:refresh:grace:{oldTokenHash}` (15-second grace window) in `backend/src/main/java/com/finflow/modules/auth/service/SessionService.java`
- [ ] T010 Implement `IdObfuscationService` generating 256-bit ephemeral session keys, encrypting internal database UUIDs into Base64URL-encoded AES-256-GCM tokens prefixed with `id_`, decrypting incoming `id_` tokens via session key, incrementing `decryptionFailures` in Redis on corruption (terminating session and throwing 401 on 3 failures; throwing `IdDecryptionException` / HTTP 449 on < 3 failures) in `backend/src/main/java/com/finflow/core/crypto/IdObfuscationService.java`
- [ ] T011 [P] Implement unit tests for `IdObfuscationService` verifying round-trip encryption, tampered ciphertext triggering HTTP 449, and 3 consecutive failures triggering session revocation in `backend/src/test/java/com/finflow/core/crypto/IdObfuscationServiceTest.java`
- [ ] T012 Implement `JwtAuthenticationFilter` (`OncePerRequestFilter`) extracting Bearer token from `Authorization` header, resolving session from Redis, verifying account status is `ACTIVE`, and populating `SecurityContextHolder` with `UserPrincipal` in `backend/src/main/java/com/finflow/core/security/JwtAuthenticationFilter.java`
- [ ] T013 Update `SecurityConfig` to register `JwtAuthenticationFilter` before `UsernamePasswordAuthenticationFilter`, permit public access to `/api/v1/auth/**`, and enforce method security for all protected routes in `backend/src/main/java/com/finflow/config/SecurityConfig.java`

**Checkpoint**: Core foundation and security filters ready — user story implementation can now begin.

---

## Phase 3: User Story 1 - Self-Registration with Approval Hold (Priority: P1)

**Goal**: Enable prospective users to register with email, password, and personal details; persist encrypted PII and BCrypt password hash; place account in `PENDING_APPROVAL` status by default with zero session tokens issued.

**Independent Test**: Send `POST /api/v1/auth/register` with valid registration payload. Verify HTTP 201 response with `status: "PENDING_APPROVAL"`. Verify database row in `users` has `status = 'PENDING_APPROVAL'` and encrypted PII. Verify `users_aud` has `INSERT` record with masked diff. Attempt `POST /api/v1/auth/login` and verify HTTP 403 Forbidden rejection.

### Tests for User Story 1
- [ ] T014 [P] [US1] Unit test for registration logic in `AuthServiceTest` verifying duplicate email check, password hashing, and `PENDING_APPROVAL` default status in `backend/src/test/java/com/finflow/modules/auth/service/AuthServiceTest.java`
- [ ] T015 [P] [US1] Integration test verifying registration API endpoint returns HTTP 201 and prevents immediate login with HTTP 403 in `backend/src/test/java/com/finflow/modules/auth/controller/AuthControllerRegistrationTest.java`

### Implementation for User Story 1
- [ ] T016 [P] [US1] Update `User` entity to map new columns `twoFactorEnabled`, `failedLoginAttempts`, `lastLoginAt` with default values and JPA annotations in `backend/src/main/java/com/finflow/modules/foundation/entity/User.java`
- [ ] T017 [P] [US1] Implement DTOs `RegistrationRequest` (validating email format, password min length 8, first name, last name, optional phone) and `RegistrationResponse` conforming to `specs/002-authentication/contracts/auth-api.yaml` in `backend/src/main/java/com/finflow/modules/auth/dto/request/RegistrationRequest.java` and `backend/src/main/java/com/finflow/modules/auth/dto/response/RegistrationResponse.java`
- [ ] T018 [US1] Implement `registerUser` method in `AuthService` validating unique email, encrypting PII via `AesGcmCryptoConverter`, hashing password with BCrypt (cost 12), setting status to `PENDING_APPROVAL`, and assigning default `END_USER` role in `backend/src/main/java/com/finflow/modules/auth/service/AuthService.java`
- [ ] T019 [US1] Implement `POST /api/v1/auth/register` endpoint in `AuthController` returning HTTP 201 with `RegistrationResponse` in `backend/src/main/java/com/finflow/modules/auth/controller/AuthController.java`

**Checkpoint**: User self-registration with mandatory approval hold is functional and independently verifiable.

---

## Phase 4: User Story 2 - User Login & ID-Obfuscated Session Issuance (Priority: P1) 🎯 MVP

**Goal**: Authenticate active users using email and password; reject non-active accounts; create a Redis session with an ephemeral ID-obfuscation key; issue a 15-minute JWT access token and 7-day refresh token; wrap all exposed database IDs in session-scoped AES-GCM tokens (`id_...`).

**Independent Test**: Register and activate a test user. Submit `POST /api/v1/auth/login`. Verify HTTP 200 response with `status: "AUTHENTICATED"`, `accessToken`, `refreshToken`, and user profile where `id` starts with `id_` and zero raw database UUIDs exist in the payload. Verify Redis key `auth:session:{sessionId}` exists with TTL 7 days.

### Tests for User Story 2
- [ ] T020 [P] [US2] Unit test for login authentication verifying credential verification, `PENDING_APPROVAL` / `SUSPENDED` account blocking, and failed login attempt counter increments in `backend/src/test/java/com/finflow/modules/auth/service/AuthServiceLoginTest.java`
- [ ] T021 [P] [US2] MockMvc test for `POST /api/v1/auth/login` verifying HTTP 200 payload contains obfuscated ID tokens and no cleartext UUIDs in `backend/src/test/java/com/finflow/modules/auth/controller/AuthControllerLoginTest.java`

### Implementation for User Story 2
- [ ] T022 [P] [US2] Implement DTOs `LoginRequest`, `LoginResponse`, `AuthSuccessResponse`, `UserProfileResponse` matching OpenAPI schema in `backend/src/main/java/com/finflow/modules/auth/dto/request/LoginRequest.java` and `backend/src/main/java/com/finflow/modules/auth/dto/response/AuthSuccessResponse.java`
- [ ] T023 [US2] Implement `authenticate` method in `AuthService` verifying BCrypt password hash, incrementing `failedLoginAttempts` on failure (locking on 5 consecutive failures), verifying account status is `ACTIVE` (throwing `AccountNotActiveException` otherwise), resetting failed attempts on success, and updating `lastLoginAt` in `backend/src/main/java/com/finflow/modules/auth/service/AuthService.java`
- [ ] T024 [US2] Implement session creation in `SessionService` generating a 256-bit AES session key, creating Redis session hash/JSON, creating refresh token index `auth:refresh:{tokenHash}`, and registering sessionId in user session set `auth:user_sessions:{userId}` in `backend/src/main/java/com/finflow/modules/auth/service/SessionService.java`
- [ ] T025 [US2] Implement `POST /api/v1/auth/login` endpoint in `AuthController` calling `AuthService` and `IdObfuscationService` to return `AuthSuccessResponse` with session-obfuscated user ID in `backend/src/main/java/com/finflow/modules/auth/controller/AuthController.java`

**Checkpoint**: At this point, User Stories 1 and 2 are fully functional. Core MVP authentication is achieved.

---

## Phase 5: User Story 3 - RFC 6238 TOTP Multi-Factor Authentication (Priority: P2)

**Goal**: Provide RFC 6238 TOTP enrollment (encrypted Base32 secret at rest, standard QR URI), activation confirmation via 6-digit code, and login challenge intercept (issuing temporary 5-min challenge token and requiring TOTP code before session establishment).

**Independent Test**: Authenticate as active user. Call `POST /api/v1/auth/mfa/setup` to receive Base32 secret. Call `POST /api/v1/auth/mfa/confirm` with oathtool-generated 6-digit code. Call `POST /api/v1/auth/login` and verify response is `MFA_REQUIRED` with `mfaToken`. Call `POST /api/v1/auth/mfa/verify` with TOTP code and verify full session credentials are returned.

### Tests for User Story 3
- [ ] T026 [P] [US3] Unit test for `TotpUtils` verifying RFC 6238 time-step calculation, HMAC-SHA1 algorithm, ±1 time-step window tolerance, and Base32 encoding in `backend/src/test/java/com/finflow/core/crypto/TotpUtilsTest.java`
- [ ] T027 [P] [US3] Integration test for TOTP enrollment, confirmation, and challenge verification with replay prevention in `backend/src/test/java/com/finflow/modules/auth/service/TotpServiceTest.java`

### Implementation for User Story 3
- [ ] T028 [P] [US3] Implement `TotpUtils` with RFC 6238 TOTP generation and verification (30-second time step, 6-digit length, HMAC-SHA1 using standard JCE `Mac`, ±1 window tolerance) in `backend/src/main/java/com/finflow/core/crypto/TotpUtils.java`
- [ ] T029 [P] [US3] Implement `UserTotpCredential` JPA entity mapping `user_totp_credentials` table with `@Convert(converter = AesGcmCryptoConverter.class)` on `secret` field and audit listener in `backend/src/main/java/com/finflow/modules/auth/entity/UserTotpCredential.java`
- [ ] T030 [P] [US3] Implement `UserTotpCredentialRepository` with Spring Data JPA query methods in `backend/src/main/java/com/finflow/modules/auth/repository/UserTotpCredentialRepository.java`
- [ ] T031 [P] [US3] Implement DTOs `MfaSetupResponse`, `MfaCodeRequest`, `MfaVerificationRequest`, `MfaChallengeResponse`, and `DisableMfaRequest` in `backend/src/main/java/com/finflow/modules/auth/dto/request/MfaCodeRequest.java` and `backend/src/main/java/com/finflow/modules/auth/dto/response/MfaSetupResponse.java`
- [ ] T032 [US3] Implement `TotpService` with methods: `generateSetupSecret(userId)` (160-bit Base32 secret, encrypted in DB, Key URI generated), `confirmSetup(userId, code)` (validates code, sets `confirmed_at`, sets `two_factor_enabled = true` on `User`), `verifyChallenge(mfaToken, code)` (validates temporary challenge token, prevents replay via Redis `auth:totp:verified:{userId}:{timeStep}`, and delegates to `SessionService` to issue tokens), and `disableMfa(userId, password, code)` in `backend/src/main/java/com/finflow/modules/auth/service/TotpService.java`
- [ ] T033 [US3] Update `AuthService.authenticate` to detect if `two_factor_enabled == true`, and if so, issue a signed 5-minute `MfaChallengeResponse` instead of standard session tokens in `backend/src/main/java/com/finflow/modules/auth/service/AuthService.java`
- [ ] T034 [US3] Implement `MfaController` with endpoints `POST /api/v1/auth/mfa/setup`, `POST /api/v1/auth/mfa/confirm`, `POST /api/v1/auth/mfa/verify`, and `POST /api/v1/auth/mfa/disable` in `backend/src/main/java/com/finflow/modules/auth/controller/MfaController.java`

**Checkpoint**: RFC 6238 TOTP two-factor authentication is functional and independently verifiable.

---

## Phase 6: User Story 4 - Google OAuth2 Federated Identity & Auto-Provisioning (Priority: P2)

**Goal**: Support Google OAuth2 Authorization Code Flow; verify `email_verified == true`; link to existing matching user or auto-provision new user in `PENDING_APPROVAL` status; issue session tokens only if account is `ACTIVE`.

**Independent Test**: Mock Google token and userinfo response with verified email. Call `POST /api/v1/auth/oauth2/google/callback`. Verify identity linked in `federated_identities`. If user was newly created, verify response HTTP 201 `PENDING_APPROVAL`. If user was active, verify HTTP 200 with session tokens and obfuscated ID.

### Tests for User Story 4
- [ ] T035 [P] [US4] Unit test for Google OAuth2 token exchange and account linking in `GoogleOAuth2ServiceTest` verifying handling of existing active users, existing pending users, and brand new users in `backend/src/test/java/com/finflow/modules/auth/service/GoogleOAuth2ServiceTest.java`

### Implementation for User Story 4
- [ ] T036 [P] [US4] Implement `FederatedIdentity` JPA entity mapping `federated_identities` table with constraints `provider VARCHAR(50)`, `provider_subject_id VARCHAR(255)`, encrypted `email`, and audit listener in `backend/src/main/java/com/finflow/modules/auth/entity/FederatedIdentity.java`
- [ ] T037 [P] [US4] Implement `FederatedIdentityRepository` with `findByProviderAndProviderSubjectId` in `backend/src/main/java/com/finflow/modules/auth/repository/FederatedIdentityRepository.java`
- [ ] T038 [P] [US4] Implement DTO `OAuth2CallbackRequest` conforming to `specs/002-authentication/contracts/auth-api.yaml` in `backend/src/main/java/com/finflow/modules/auth/dto/request/OAuth2CallbackRequest.java`
- [ ] T039 [US4] Implement `GoogleOAuth2Service` exchanging authorization code with Google token endpoint, verifying email is verified, finding or creating `FederatedIdentity`, auto-provisioning `User` in `PENDING_APPROVAL` if new, or delegating to `SessionService` to issue tokens if active in `backend/src/main/java/com/finflow/modules/auth/service/GoogleOAuth2Service.java`
- [ ] T040 [US4] Implement `OAuth2Controller` with endpoint `POST /api/v1/auth/oauth2/google/callback` in `backend/src/main/java/com/finflow/modules/auth/controller/OAuth2Controller.java`

**Checkpoint**: Google OAuth2 federated authentication and account linking are functional.

---

## Phase 7: User Story 5 - Session Token Refresh & Revocation (Priority: P2)

**Goal**: Support 15-minute access token refresh using 7-day refresh tokens; rotate refresh tokens on each refresh with a 15-second grace window to handle concurrent browser requests; provide explicit logout invalidating Redis session immediately.

**Independent Test**: Authenticate as active user. Call `POST /api/v1/auth/refresh` with refresh token. Verify new `accessToken` and rotated `refreshToken` are returned. Submit the old refresh token within 15 seconds and verify cached response is returned. Submit old token after 15 seconds and verify HTTP 401. Call `POST /api/v1/auth/logout` and verify subsequent requests with that session are rejected with HTTP 401.

### Tests for User Story 5
- [ ] T041 [P] [US5] Unit test for `SessionService` verifying refresh token rotation, grace window reuse within 15 seconds, rejection after TTL, and logout session deletion in `backend/src/test/java/com/finflow/modules/auth/service/SessionServiceTest.java`
- [ ] T042 [P] [US5] MockMvc test for `/api/v1/auth/refresh` and `/api/v1/auth/logout` endpoints in `backend/src/test/java/com/finflow/modules/auth/controller/AuthControllerSessionTest.java`

### Implementation for User Story 5
- [ ] T043 [P] [US5] Implement DTOs `RefreshTokenRequest`, `TokenResponse`, `LogoutRequest`, and `MessageResponse` in `backend/src/main/java/com/finflow/modules/auth/dto/request/RefreshTokenRequest.java` and `backend/src/main/java/com/finflow/modules/auth/dto/response/TokenResponse.java`
- [ ] T044 [US5] Implement `rotateRefreshToken` and `logout` in `SessionService` validating refresh token hash in Redis, checking grace cache `auth:refresh:grace:{oldTokenHash}`, issuing new token pair, caching new tokens for 15s in grace key, deleting old refresh key, and deleting session hash on logout in `backend/src/main/java/com/finflow/modules/auth/service/SessionService.java`
- [ ] T045 [US5] Implement `POST /api/v1/auth/refresh` and `POST /api/v1/auth/logout` endpoints in `AuthController` in `backend/src/main/java/com/finflow/modules/auth/controller/AuthController.java`

**Checkpoint**: Refresh token rotation with 15-second grace window and explicit logout are functional.

---

## Phase 8: User Story 6 - Self-Service Password Recovery with Session Purge (Priority: P3)

**Goal**: Allow users to request a password reset via email; store a 6-digit numeric OTP in Redis with a 15-minute TTL and max 5 attempts; verify OTP and update password; immediately terminate all active sessions for that user across all devices.

**Independent Test**: Submit `POST /api/v1/auth/password-reset/request` with registered email. Retrieve 6-digit OTP from Redis. Submit `POST /api/v1/auth/password-reset/confirm` with OTP and new password. Verify HTTP 200 response. Verify all Redis session keys in `auth:user_sessions:{userId}` are deleted. Attempt login with old password (fails) and with new password (succeeds).

### Tests for User Story 6
- [ ] T046 [P] [US6] Unit test for `PasswordResetService` verifying OTP generation, rate-limited attempt counter (locking on 5th attempt), expiry validation, and password hash update with session termination in `backend/src/test/java/com/finflow/modules/auth/service/PasswordResetServiceTest.java`
- [ ] T047 [P] [US6] MockMvc test for password reset request and confirmation endpoints in `backend/src/test/java/com/finflow/modules/auth/controller/AuthControllerPasswordResetTest.java`

### Implementation for User Story 6
- [ ] T048 [P] [US6] Implement DTOs `PasswordResetRequest` and `PasswordResetConfirmRequest` in `backend/src/main/java/com/finflow/modules/auth/dto/request/PasswordResetRequest.java` and `backend/src/main/java/com/finflow/modules/auth/dto/request/PasswordResetConfirmRequest.java`
- [ ] T049 [US6] Implement `PasswordResetService` generating cryptographically random 6-digit OTP, saving SHA-256 hashed OTP in Redis `auth:otp:password_reset:{userId}` with 15-min TTL, validating OTP with attempt tracking (max 5), updating `password_hash` in `users` table via `UserRepository`, and purging all user sessions from Redis via `SessionService.revokeAllUserSessions(userId)` in `backend/src/main/java/com/finflow/modules/auth/service/PasswordResetService.java`
- [ ] T050 [US6] Implement `POST /api/v1/auth/password-reset/request` and `POST /api/v1/auth/password-reset/confirm` endpoints in `AuthController` in `backend/src/main/java/com/finflow/modules/auth/controller/AuthController.java`

**Checkpoint**: Self-service password recovery with complete cross-device session termination is functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Actuator metrics, audit verification, OpenAPI documentation update, and automated quickstart validation scenarios.

- [ ] T051 [P] Update `OpenApiConfig` to include Authentication Module OpenAPI specification and JWT Bearer security scheme in `backend/src/main/java/com/finflow/config/OpenApiConfig.java`
- [ ] T052 [P] Verify that sensitive fields (`password`, `otp`, `secret`, `refreshToken`) are masked by `PiiMaskingPatternLayout` in application logs in `backend/src/main/resources/logback-spring.xml`
- [ ] T053 Verify Liquibase changeset rollback safety by running `./mvnw liquibase:update-testing-rollback` on PostgreSQL in `backend/`
- [ ] T054 Execute all 7 validation scenarios documented in `specs/002-authentication/quickstart.md` (registration hold, login obfuscation, TOTP 2FA, token refresh/logout, ID decryption tamper circuit breaker, and password reset)

---

## Dependencies & Execution Order

### Phase Dependencies
- **Phase 1 (Setup)**: Can start immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 — **BLOCKS all user stories**.
  - Creates Liquibase tables (`004-create-auth-module-tables.xml`).
  - Sets up `JwtTokenProvider`, `SessionService`, `IdObfuscationService`, and `JwtAuthenticationFilter`.
- **Phase 3+ (User Stories)**: Depend on Phase 2 completion.
  - **User Story 1 (P1 - Self-Registration)**: Can start after Phase 2.
  - **User Story 2 (P1 - Login & Obfuscated Session)**: Can start after Phase 2 and US1 entities. 🎯 MVP
  - **User Story 3 (P2 - TOTP MFA)**: Depends on US2 (requires active session and credentials).
  - **User Story 4 (P2 - Google OAuth2)**: Depends on US2 and US1 (linking / provisioning).
  - **User Story 5 (P2 - Token Refresh & Revocation)**: Depends on US2 (refreshes active sessions).
  - **User Story 6 (P3 - Password Reset)**: Depends on US2 (terminates active sessions).
- **Phase 9 (Polish)**: Depends on all user stories being complete.

### Story Completion Order (Recommended Execution Flow)
```
Phase 1: Setup (Dependencies, OpenAPI, Config)
   ↓
Phase 2: Foundational (Liquibase Changeset 004, JWT Provider, Redis SessionService, IdObfuscationService)
   ↓
Phase 3: US1 (Self-Registration with Approval Hold)
   ↓
Phase 4: US2 (User Login & ID-Obfuscated Session Issuance) 🎯 MVP
   ↓
Phase 5: US3 (TOTP MFA) || Phase 6: US4 (Google OAuth2) || Phase 7: US5 (Token Refresh)
   ↓
Phase 8: US6 (Password Recovery & Session Purge)
   ↓
Phase 9: Polish & Quickstart Runbook Validation
```

---

## Parallel Execution Examples

### Foundational Phase Parallelism
```bash
# Can run concurrently in Phase 2:
T007 [P] Implement domain exceptions in backend/src/main/.../GlobalExceptionHandler.java
T011 [P] Implement unit tests for IdObfuscationService in backend/src/test/.../IdObfuscationServiceTest.java
```

### User Story 1 Parallelism
```bash
# Can run concurrently in Phase 3:
T014 [P] [US1] Unit test for registration logic in backend/src/test/.../AuthServiceTest.java
T015 [P] [US1] Integration test for registration API in backend/src/test/.../AuthControllerRegistrationTest.java
T016 [P] [US1] Update User entity in backend/src/main/.../entity/User.java
T017 [P] [US1] Implement RegistrationRequest and RegistrationResponse DTOs
```

### User Story 3 (MFA) Parallelism
```bash
# Can run concurrently in Phase 5:
T026 [P] [US3] Unit test for TotpUtils in backend/src/test/.../TotpUtilsTest.java
T028 [P] [US3] Implement TotpUtils in backend/src/main/.../crypto/TotpUtils.java
T029 [P] [US3] Implement UserTotpCredential entity in backend/src/main/.../entity/UserTotpCredential.java
T030 [P] [US3] Implement UserTotpCredentialRepository in backend/src/main/.../repository/
T031 [P] [US3] Implement MFA DTOs in backend/src/main/.../dto/
```

---

## Implementation Strategy

### MVP First (User Stories 1 & 2)
1. Complete **Phase 1: Setup** (JWT dependencies, configs, openapi).
2. Complete **Phase 2: Foundational** (Liquibase changeset 004, JWT provider, session service, ID obfuscator, security filter).
3. Complete **Phase 3: User Story 1** (Self-registration with approval hold).
4. Complete **Phase 4: User Story 2** (Credential login with session creation and ID obfuscation).
5. **STOP and VALIDATE**: Verify registration creates pending user, login rejects pending user, approved user logs in and receives `id_...` obfuscated token with zero raw UUIDs exposed.

### Incremental Delivery
1. Foundation + US1 + US2 = Core functional MVP (Registration + Login with ID obfuscation).
2. Add US3 (TOTP MFA) = High-security two-factor authentication for financial transactions.
3. Add US4 (Google OAuth2) = Seamless federated onboarding and social sign-in.
4. Add US5 (Refresh & Revocation) = Secure token rotation and immediate logout.
5. Add US6 (Password Recovery) = Self-service OTP recovery with cross-device session termination.
6. Polish = Complete end-to-end quickstart validation and Liquibase rollback test.
