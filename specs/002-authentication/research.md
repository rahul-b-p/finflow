# Research & Technical Decisions: Module 2 — Authentication

**Feature ID:** MOD-002  
**Module:** Authentication  
**Branch:** `002-authentication`  
**Date:** 2026-10-12  

---

## 1. Session Management, JWT Token Architecture & Redis Store

### Decision
Implement a hybrid authentication architecture combining stateless signed JWT access tokens with a stateful Redis session registry:
1. **Short-Lived Access Token (JWT)**:
   - **Lifetime**: 15 minutes.
   - **Algorithm**: HMAC-SHA256 (HS256) or RSA/ECDSA using `io.jsonwebtoken:jjwt-api:0.12.5` (`jjwt-impl`, `jjwt-jackson`).
   - **Claims**:
     - `sub`: Obfuscated session user token (NOT raw database UUID).
     - `sid`: Unique Session ID (`UUIDv4`).
     - `roles`: Assigned role names (e.g., `["END_USER"]`).
     - `privileges`: Assigned fine-grained privilege strings (e.g., `["AUTH:LOGIN", "AUTH:UPDATE_PROFILE"]`).
     - `iat`: Issued-at timestamp (UTC).
     - `exp`: Expiration timestamp (15 minutes from issue).
2. **Long-Lived Refresh Token**:
   - **Lifetime**: 7 days.
   - **Format**: High-entropy cryptographically secure random string (64 bytes Base64URL-encoded).
   - **Storage**: Tracked exclusively in Redis under `auth:refresh:{refreshTokenHash}` storing:
     - `sessionId`: Associated session UUID.
     - `userId`: Internal user UUID.
     - `createdAt`: UTC instant.
     - `expiresAt`: UTC instant (7 days TTL).
3. **Active Session Registry in Redis**:
   - Stored under key: `auth:session:{sessionId}` with a 7-day TTL matching refresh token expiration.
   - Session metadata schema:
     - `sessionId`: UUID.
     - `userId`: Raw internal UUID.
     - `sessionEncryptionKey`: 256-bit ephemeral AES key generated per session for ID obfuscation.
     - `decryptionFailures`: Atomic counter tracking invalid ID decryption attempts (threshold: 3).
     - `userStatus`: Cached account status (`ACTIVE`).
     - `lastActivityAt`: UTC instant.
4. **Token Refresh Rotation & Grace Window**:
   - Every refresh request invalidates the presenting refresh token and generates a new refresh token and access token.
   - To prevent race conditions from concurrent frontend requests (e.g. multiple parallel HTTP calls on page reload), the old refresh token is placed in a 15-second grace window key in Redis (`auth:refresh:grace:{oldTokenHash}` -> `newRefreshToken`). Subsequent calls within 15 seconds receive the newly issued credentials instead of failing.
5. **Explicit Logout & Revocation**:
   - Explicit logout immediately deletes `auth:session:{sessionId}`, deletes the refresh token, and blacklists the current JWT `jti`/`sid` in Redis with remaining JWT lifetime TTL (`auth:jwt:blacklist:{sid}`).

### Rationale
- Complies with Constitution Principle I (Security & Trust) and Principle IV (RBAC Enforced at Every Layer).
- Meets FR-013, FR-014, FR-015, FR-020, and FR-021.
- Provides high throughput with short-lived JWT access tokens while preserving instant revocation, status validation, and logout capabilities via the Redis session registry.

### Alternatives Considered
- **Pure Stateless JWTs without Redis**: Stateless JWTs cannot be revoked upon logout, account suspension, or password change before their expiration, violating financial security requirements.
- **Pure Server-Side Stateful Sessions (Cookie Session)**: Cookies without JWT claims require a Redis lookup on every micro-call and complicate mobile or cross-origin client integration.
- **Database-Backed Sessions**: Storing transient sessions and refresh tokens in PostgreSQL causes high write volume and transaction contention on auth tables; Redis provides sub-millisecond memory-speed operations with automatic TTL expiration.

---

## 2. Session-Scoped Database ID Obfuscation

### Decision
All internal database primary keys (`UUID`s) must be transformed into ephemeral, encrypted tokens before transmission over the network, and reversed upon arrival:
1. **Ephemeral Key Generation**:
   - Upon successful login / session establishment, a cryptographically secure 256-bit AES key is generated (`SecureRandom.nextBytes(32)`).
   - The key is bound to the session in Redis: `auth:session:{sessionId}.sessionEncryptionKey`.
2. **Encryption Mechanism**:
   - Algorithm: `AES/GCM/NoPadding` with 96-bit (12-byte) random IV and 128-bit authentication tag.
   - Payload: The plaintext UUID string (or 16-byte raw UUID bytes).
   - Wire representation: Base64URL-encoded string: `base64url(iv || ciphertext || tag)`.
   - Prefix: `id_` or raw token string for clear recognition.
3. **Decryption & Failure Protection**:
   - When a request containing an obfuscated ID arrives, the system resolves the session's encryption key from Redis and decrypts the identifier to the underlying database UUID.
   - If decryption fails (corrupted, manipulated, or expired session key):
     - Increment the session's `decryptionFailures` counter in Redis atomically.
     - If `decryptionFailures < 3`: Return **HTTP 449 Retry With New Token** with Problem Detail code `ID_DECRYPTION_FAILED`, signaling the frontend to refresh tokens or retry.
     - If `decryptionFailures >= 3`: Immediately terminate the session in Redis, purge all session tokens, and return **HTTP 401 Unauthorized** with detail "Session terminated due to excessive decryption failures. Re-authentication required."
4. **Integration Layer**:
   - Spring MVC / Jackson integration: Custom Jackson Serializer/Deserializer or DTO conversion helpers to ensure zero internal database UUIDs are serialized to JSON responses.

### Rationale
- Non-negotiable requirement of Constitution Principle I: "Internal database primary keys MUST never be exposed to the frontend. All IDs MUST be wrapped in per-session AES-encrypted tokens before transmission."
- Enforces FR-016, FR-017, FR-018, FR-019, SC-003, and SC-004.
- Prevents database enumeration, IDOR (Insecure Direct Object Reference) attacks, and cross-session replay of object identifiers.

### Alternatives Considered
- **Static Salt Hash / Hashids**: Hashids are obfuscation rather than encryption, are vulnerable to brute-force reverse engineering, and do not provide per-session isolation or replay prevention.
- **Global Application-Wide AES Encryption**: Using a single system-wide key for ID encryption allows IDs to be replayed across different user sessions, failing session-scoped isolation mandates.

---

## 3. RFC 6238 TOTP Multi-Factor Authentication (2FA)

### Decision
Implement multi-factor authentication strictly conforming to RFC 6238 Time-based One-Time Password (TOTP) standards:
1. **Secret Generation**:
   - 160-bit (20-byte) cryptographically secure random bytes generated using `SecureRandom`.
   - Formatted as Base32 string (RFC 4648, 32 characters, uppercase A–Z, 2–7).
2. **Secret Storage**:
   - Persisted in the `user_totp_credentials` table.
   - Encrypted at rest using `AesGcmCryptoConverter` (AES-256-GCM).
   - Paired with an immutable shadow audit table `user_totp_credentials_aud`.
3. **Enrollment & QR Code Pairing**:
   - System generates standard Key URI format:  
     `otpauth://totp/FinFlow:{email}?secret={base32Secret}&issuer=FinFlow&algorithm=SHA1&digits=6&period=30`
   - Returns setup payload containing the URI (for QR rendering) and the Base32 secret string (for manual entry).
   - Account status remains `2fa_enabled = false` until the user submits a successful 6-digit TOTP code verification to confirm activation.
4. **TOTP Calculation & Verification**:
   - Algorithm: HMAC-SHA1 with 30-second time step (`period = 30`), 6 digits.
   - Standard Java Cryptography Extension (`javax.crypto.Mac`) with `HmacSHA1`.
   - Window tolerance: Allowed window of ±1 step (current step, -30s, +30s) to absorb minor clock drift between client and server.
   - Replay protection: Store the last verified timestamp/step in Redis with a 90-second TTL (`auth:totp:verified:{userId}:{step}`) to prevent replay of the same 6-digit code within the validity window.
5. **Login Challenge Flow**:
   - If user credentials match and `two_factor_enabled == true`:
     - System does NOT issue full access tokens.
     - System generates an ephemeral `mfa_token` (signed short-lived JWT, 5-minute TTL, claim `purpose: mfa_challenge`, `userId`).
     - Responds with HTTP 200 containing `status: "MFA_REQUIRED"` and `mfaToken`.
     - Client submits code to `/api/v1/auth/mfa/verify` with `mfaToken` and `code`.
     - Upon valid code, full session credentials and obfuscated IDs are returned.

### Rationale
- Fulfills FR-010, FR-011, and FR-012.
- Compatible with all standard authenticator apps (Google Authenticator, Microsoft Authenticator, 1Password, Authy).
- Zero external third-party dependencies required; standard Java `javax.crypto.Mac` implementation provides full compliance with zero supply chain risk.

### Alternatives Considered
- **SMS-based 2FA**: SMS is vulnerable to SIM swapping, SS7 interception, and network latency; RFC 6238 TOTP is far more secure for financial applications.
- **Third-Party TOTP Libraries (e.g. WarrentStrange/GoogleAuth)**: Standard JCE `Mac` implementation in ~50 lines of clean Java avoids extra external dependencies and allows custom replay protection and audit logging.

---

## 4. Google OAuth2 Federated Identity & Account Linking

### Decision
Implement Google OAuth2 authentication via the Authorization Code Flow (`response_type=code`):
1. **Flow Specification**:
   - Backend exposes initiation / redirection endpoint `/api/v1/auth/oauth2/google` generating a secure PKCE / state token stored in Redis with 5-minute TTL.
   - Callback endpoint `/api/v1/auth/oauth2/google/callback` receives `code` and `state`.
   - Backend exchanges `code` with Google Token Endpoint (`https://oauth2.googleapis.com/token`) for ID token and access token.
   - Decodes and validates Google ID token (signature, audience matching `GOOGLE_CLIENT_ID`, issuer `https://accounts.google.com`).
2. **Account Linking & Status Evaluation**:
   - Validate Google profile claim `email_verified == true`. If false, reject authentication with HTTP 400 (`UNVERIFIED_FEDERATED_EMAIL`).
   - Query `federated_identities` table for `provider = 'GOOGLE'` and `provider_subject_id`.
   - **Case A (Linked Account Exists)**:
     - Check linked user account status:
       - If `ACTIVE`: Establish session, issue obfuscated session credentials.
       - If `PENDING_APPROVAL`: Return HTTP 403 Forbidden with problem detail `ACCOUNT_PENDING_APPROVAL`.
       - If `SUSPENDED` or `REJECTED`: Return HTTP 403 with `ACCOUNT_SUSPENDED` / `ACCOUNT_REJECTED`.
   - **Case B (No Link, but Local User with Matching Email Exists)**:
     - If local user status is `ACTIVE`: Link the Google federated identity into `federated_identities` (with audit log in `federated_identities_aud`), establish active session.
     - If local user status is not `ACTIVE`: Return appropriate error response without linking.
   - **Case C (Brand New Identity)**:
     - Create new user record in `users` with `status = 'PENDING_APPROVAL'`.
     - Encrypt PII (email, name) with AES-256-GCM.
     - Create linked entry in `federated_identities`.
     - Write audit records to `users_aud` and `federated_identities_aud`.
     - Return HTTP 201 Created with status `PENDING_APPROVAL` and notice that administrative review is required. Zero session tokens issued.

### Rationale
- Fulfills FR-007, FR-008, FR-009, and User Story 4.
- Strictly adheres to Constitution Principle I: "Authentication tokens MUST only be issued to accounts in `ACTIVE` status. `PENDING_APPROVAL` and `REJECTED` accounts MUST be denied tokens."

### Alternatives Considered
- **Direct Client-Side Token Exchange (Implicit Flow)**: Deprecated by OAuth2 / OIDC security best practices; Authorization Code Flow with backend token exchange guarantees client secret protection and tamper-proof user verification.

---

## 5. Password Reset via Time-Limited Email OTP

### Decision
1. **Reset Request (`POST /api/v1/auth/password-reset/request`)**:
   - Accepts `email`.
   - Queries user by encrypted email lookup (or deterministic lookup index).
   - If user exists and is not `REJECTED`:
     - Generates cryptographically secure 6-digit numeric OTP using `SecureRandom`.
     - Hashes the OTP using SHA-256 before persisting in Redis:
       `auth:otp:password_reset:{userId}` containing `{hashedOtp, attempts: 0, expiresAt}` with strict 15-minute TTL.
     - Dispatches email notification with the plaintext OTP via email/notification service.
     - Writes security audit event to `users_aud`.
   - If user does NOT exist: Returns the same HTTP 200 success response ("If your email is registered, a password reset code has been sent.") to eliminate email enumeration vulnerabilities.
2. **Reset Confirmation (`POST /api/v1/auth/password-reset/confirm`)**:
   - Accepts `email`, `otp`, `newPassword`.
   - Retrieves Redis record `auth:otp:password_reset:{userId}`.
   - If expired or not found: Return HTTP 400 (`OTP_EXPIRED_OR_INVALID`).
   - If OTP verification fails:
     - Increment `attempts` counter in Redis.
     - If `attempts >= 5`: Immediately delete OTP record from Redis and enforce cooldown. Return HTTP 400 (`OTP_MAX_ATTEMPTS_EXCEEDED`).
     - Otherwise: Return HTTP 400 (`INVALID_OTP`).
   - If OTP is valid:
     - Validate `newPassword` against complexity rules (min 8 chars, uppercase, lowercase, number, special char).
     - Hash `newPassword` with BCrypt (cost factor 12).
     - Update user record in PostgreSQL within `@Transactional` block.
     - Write mutation to `users_aud` in the same transaction.
     - Delete Redis OTP record.
     - **Revoke all active sessions** for the user: scan and delete all Redis session records and refresh tokens associated with `userId`.
     - Return HTTP 200 (`PASSWORD_RESET_SUCCESS`).

### Rationale
- Fulfills FR-022, FR-023, FR-024, and User Story 5.
- Eliminates account takeover risks through strict rate limiting (max 5 attempts), short expiration (15 minutes), and full session invalidation upon credential modification.

### Alternatives Considered
- **Reset Token Link via URL Parameter**: Magic links can be intercepted via browser history, proxy logs, or email previews; a 6-digit OTP with active rate limiting and 15-minute expiration provides strong financial security and prevents automated link traversal attacks.

---

## 6. Database Schema & Liquibase Migrations for Module 2

### Decision
Define a new Liquibase changeset file `src/main/resources/db/changelog/v1.0.0/004-create-auth-module-tables.xml` referenced in `db.changelog-v1.0.0.xml`.
1. **Schema Enhancements to `users`**:
   - Add column `two_factor_enabled` (BOOLEAN NOT NULL DEFAULT FALSE).
   - Add column `failed_login_attempts` (INT NOT NULL DEFAULT 0).
   - Add column `last_login_at` (TIMESTAMP WITH TIME ZONE NULL).
2. **New Table: `user_totp_credentials`**:
   - `id`: UUID PRIMARY KEY DEFAULT gen_random_uuid()
   - `user_id`: UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
   - `secret`: VARCHAR(500) NOT NULL (AES-256-GCM encrypted Base32 string)
   - `confirmed_at`: TIMESTAMP WITH TIME ZONE NULL
   - `created_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
   - `updated_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
3. **New Table: `federated_identities`**:
   - `id`: UUID PRIMARY KEY DEFAULT gen_random_uuid()
   - `user_id`: UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE
   - `provider`: VARCHAR(50) NOT NULL ('GOOGLE')
   - `provider_subject_id`: VARCHAR(255) NOT NULL
   - `created_at`: TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
   - UNIQUE CONSTRAINT on `(provider, provider_subject_id)`
4. **Shadow Audit Tables (`user_totp_credentials_aud`, `federated_identities_aud`)**:
   - Standard audit contract: `audit_id BIGSERIAL PK`, `id UUID`, `rev_type VARCHAR(10)`, `diff_data JSONB`, `modified_by VARCHAR(100)`, `ip_address VARCHAR(45)`, `rev_timestamp TIMESTAMPTZ`.
5. **Rollback Plan**:
   - Explicit `<rollback>` block dropping `federated_identities_aud`, `federated_identities`, `user_totp_credentials_aud`, `user_totp_credentials`, and dropping added columns on `users`.

### Rationale
- Complies with Constitution Principle III (Audit Everything) and Principle V (Versioned, Rollback-Ready Migrations).
- Keeps user domain model clean and normalized with clear 1:1 TOTP and 1:N federated identity relationships.

---

## Summary of Decisions

| Area | Decision | Spec / Constitution Reference |
|---|---|---|
| **Session & Token Architecture** | 15-min JWT access token + 7-day Redis refresh token + Redis session registry | Principle I, IV; FR-013, FR-014, FR-020 |
| **ID Obfuscation** | Ephemeral per-session AES-GCM encryption for all database UUIDs; HTTP 449 on failure; abort after 3 fails | Principle I; FR-015, FR-016, FR-017, FR-018, FR-019 |
| **2FA / TOTP** | RFC 6238 HMAC-SHA1 Base32 encrypted secret at rest, QR setup URI, 5-min challenge token | FR-010, FR-011, FR-012 |
| **Google OAuth2** | Authorization Code Flow, email verification check, auto-link or pending registration | FR-007, FR-008, FR-009 |
| **Password Reset** | 6-digit numeric OTP in Redis (15-min TTL, 5-attempt limit), full session revocation | FR-022, FR-023, FR-024 |
| **Database Migrations** | Liquibase changeset `004-create-auth-module-tables.xml` with shadow audit tables and rollbacks | Principle III, V; FR-025 |
