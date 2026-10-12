# Feature Specification: Module 2 — Authentication

**Feature Branch**: `002-authentication`

**Created**: 2026-10-12

**Status**: Draft

**Input**: User description: "module 2 refer sources/application-modules.md"

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 - User Self-Registration with Approval Hold (Priority: P1)

As a new platform visitor, I want to register an account with my personal information so that I can submit an onboarding request to access FinFlow financial services once reviewed by an administrator.

**Why this priority**: Self-registration is the entry portal for all new individual users on the platform. Without account registration, no user identities can enter the system.

**Independent Test**: Can be fully tested by submitting valid registration details and verifying that a user account record is created in `PENDING_APPROVAL` status, without issuing any active authentication tokens or access rights.

**Acceptance Scenarios**:

1. **Given** a new visitor with valid registration details (name, email, phone, password), **When** they submit the registration form, **Then** their account is created in `PENDING_APPROVAL` status, all sensitive data is securely stored, an audit record is created, and the user receives a confirmation notice stating their account is awaiting administrator review.
2. **Given** a newly registered user whose account status is `PENDING_APPROVAL`, **When** they attempt to log in with their credentials, **Then** the login is rejected with a clear message indicating the account is awaiting approval, and zero session tokens are issued.
3. **Given** an existing registered email address, **When** a visitor attempts to register again with that same email, **Then** the registration is rejected with an informative error, and duplicate accounts are prevented.

---

### User Story 2 - User Login & ID-Obfuscated Session Issuance (Priority: P1)

As an approved platform user, I want to log in using my email and password so that I can securely access the platform while ensuring my internal database identifiers are never exposed over the network.

**Why this priority**: Secure authentication is the prerequisite for all authenticated financial operations, guaranteeing identity verification and compliance with data privacy mandates.

**Independent Test**: Can be fully tested with an `ACTIVE` user account by submitting correct credentials, receiving valid short-lived access credentials and a refresh token, and verifying that all identity references in the payload are opaque, obfuscated tokens rather than raw database primary keys.

**Acceptance Scenarios**:

1. **Given** an approved user with status `ACTIVE` and no two-factor authentication enabled, **When** they provide valid email and password credentials, **Then** the system establishes a secure session, generates an ephemeral per-session ID obfuscation key, issues short-lived access credentials, and returns user profile details where all internal identifiers are obfuscated.
2. **Given** an account in `REJECTED` or `SUSPENDED` status, **When** the user attempts to log in with valid credentials, **Then** the system refuses authentication and returns an explicit access denial response without issuing session tokens.
3. **Given** an invalid password attempt, **When** the user submits the login form, **Then** the system denies authentication with a generic credential error and logs the failed attempt for security analysis.

---

### User Story 3 - Multi-Factor Authentication via RFC 6238 TOTP (Priority: P2)

As a security-conscious user or administrator, I want to protect my account with a Time-based One-Time Password (TOTP) authenticator app so that my financial account cannot be breached even if my primary password is leaked.

**Why this priority**: Multi-factor authentication is critical for financial platforms to protect against credential stuffing, phishing, and password reuse attacks.

**Independent Test**: Can be independently tested by enabling TOTP via a standard authenticator QR code/secret, logging in with primary credentials to trigger an MFA challenge, and exchanging a valid 6-digit TOTP code for full active session tokens.

**Acceptance Scenarios**:

1. **Given** an active authenticated user navigating to security settings, **When** they initiate two-factor setup, **Then** the system generates a secure TOTP secret, displays a standard authenticator setup code (and QR pairing URI), and confirms activation only after the user verifies a valid 6-digit code.
2. **Given** a user with two-factor authentication enabled, **When** they supply valid primary credentials during login, **Then** the system issues a temporary second-factor challenge token rather than full session access, requiring a valid TOTP code to complete login.
3. **Given** a pending two-factor challenge, **When** the user supplies a valid current 6-digit code from their authenticator app, **Then** the system completes authentication, binds an obfuscated session, and issues active access tokens.
4. **Given** a pending two-factor challenge, **When** the user supplies an expired, invalid, or reused TOTP code, **Then** authentication is denied and the user is prompted to re-enter a valid code.

---

### User Story 4 - Google OAuth2 Federated Identity & Account Linking (Priority: P2)

As an end-user, I want to sign in using my verified Google account so that I can authenticate quickly without remembering a separate password.

**Why this priority**: Single Sign-On (SSO) drastically reduces onboarding friction and provides enterprise-grade identity verification backed by Google's security infrastructure.

**Independent Test**: Can be tested by initiating the OAuth2 authorization code flow, exchanging the authorization code, and verifying that an existing approved account is authenticated or a new identity is onboarded with `PENDING_APPROVAL` status.

**Acceptance Scenarios**:

1. **Given** an existing `ACTIVE` user whose verified Google email matches their FinFlow account, **When** they authenticate through Google OAuth2, **Then** the system validates the federated identity, links the Google account identifier, establishes an active session, and issues obfuscated session credentials.
2. **Given** a new visitor using Google OAuth2 for the first time, **When** Google verifies their identity and returns verified profile details, **Then** the system provisions a user profile in `PENDING_APPROVAL` status and notifies the user that their account requires administrative approval before access is permitted.
3. **Given** a user whose account is in `PENDING_APPROVAL` or `SUSPENDED` status, **When** they attempt to sign in via Google OAuth2, **Then** the system halts access after Google callback and returns an appropriate status notification without granting active session tokens.

---

### User Story 5 - Password Reset via Time-Limited Email OTP (Priority: P3)

As a user who has forgotten their password, I want to request a secure one-time password sent to my registered email so that I can regain access to my account without administrative intervention.

**Why this priority**: Self-service recovery ensures business continuity for legitimate users while preventing unauthorized account takeover.

**Independent Test**: Can be tested by initiating a reset request for a registered email, verifying receipt of a time-limited OTP, validating the OTP with a new password, and confirming that prior sessions are terminated and the new password is required for subsequent logins.

**Acceptance Scenarios**:

1. **Given** a registered user, **When** they request a password reset for their email address, **Then** the system generates a secure 6-digit one-time password with a 15-minute expiration, sends it via email notification, and records a security audit event.
2. **Given** an unexpired password reset OTP, **When** the user submits the correct OTP and a new password conforming to complexity standards, **Then** the password is updated, all existing sessions for that user are revoked, and the user can immediately log in with their new password.
3. **Given** a password reset request for an email address that does not exist in the system, **When** the request is submitted, **Then** the system responds with a generic success notice to prevent email enumeration, without dispatching an email.

---

### User Story 6 - Session Management, Token Refresh & Revocation (Priority: P3)

As an active user, I want my session to automatically refresh without interrupting my work, and I want to explicitly log out to revoke all active access tokens when I am done.

**Why this priority**: Keeps user workflows seamless while providing strict revocation controls to eliminate stale or hijacked sessions.

**Independent Test**: Can be tested by presenting a valid refresh token to obtain new access credentials, and testing explicit logout to verify that subsequent refresh or access attempts are rejected.

**Acceptance Scenarios**:

1. **Given** an active user whose short-lived access credential is near expiration, **When** the client submits a valid refresh token, **Then** the system issues a new short-lived access token, rotates the refresh token, and maintains the current ID obfuscation session mapping.
2. **Given** an authenticated user who clicks "Log Out", **When** the logout request is processed, **Then** the current session is invalidated in the session store, the refresh token is revoked, and all subsequent requests with those credentials are rejected.
3. **Given** a revoked or manipulated refresh token, **When** presented to the token refresh endpoint, **Then** the request is rejected with HTTP 401 and the suspicious event is logged.

---

### Edge Cases

- **Account Status Transition**: What happens if an admin suspends or rejects an account while the user has an active session? The Spring Security verification must evaluate the user's account status during token refresh and method authorization, immediately terminating the session and rejecting subsequent requests with HTTP 401/403.
- **Obfuscated ID Decryption Errors**: What happens if an API request includes an altered, corrupted, or stale obfuscated identifier? The system must reject the operation with a retry response (HTTP 449) prompting client token refresh. If a session accumulates three consecutive decryption failures, the system must immediately terminate the session and mandate a fresh login.
- **Brute Force & OTP Expiration**: How does the system handle repeated incorrect attempts for TOTP verification or email reset OTPs? The system must limit verification attempts to a maximum of 5 consecutive failures per OTP challenge, after which the OTP is immediately invalidated and a cooldown period is enforced.
- **Concurrent Session Refresh Race Condition**: What happens if two parallel frontend API calls attempt to refresh the token simultaneously? The token refresh service must support a grace window (e.g. 15 seconds) where the prior refresh token remains valid for in-flight requests during rotation before being completely revoked.
- **Google OAuth2 Profile Conflicts**: What happens if a user signs in with a Google account whose email matches an existing account that was originally registered with a username/password? The system must require that the Google email is verified (`email_verified=true`) before automatically linking the federated identity, logging an audit record for the link.

---

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST support self-service user registration capturing first name, last name, email, phone number, and password.
- **FR-002**: System MUST place all self-registered user accounts in `PENDING_APPROVAL` status by default.
- **FR-003**: System MUST reject authentication attempts and deny token issuance to accounts in `PENDING_APPROVAL`, `REJECTED`, or `SUSPENDED` status.
- **FR-004**: System MUST issue authentication tokens only to accounts in `ACTIVE` status.
- **FR-005**: System MUST encrypt all Personally Identifiable Information (email, first name, last name, phone number) at rest using AES-256-GCM.
- **FR-006**: System MUST hash all user passwords using BCrypt with a minimum cost factor of 12.
- **FR-007**: System MUST support user authentication via Google OAuth2 authorization code flow.
- **FR-008**: System MUST place new user accounts created via Google OAuth2 into `PENDING_APPROVAL` status until reviewed by an administrator.
- **FR-009**: System MUST link Google OAuth2 identity to an existing account only when the Google profile indicates the email is verified.
- **FR-010**: System MUST support two-factor authentication (2FA) conforming to RFC 6238 Time-based One-Time Password (TOTP) standards.
- **FR-011**: System MUST securely store TOTP shared secrets encrypted at rest and expose standard QR code URI strings (`otpauth://totp/...`) for authenticator app enrollment.
- **FR-012**: System MUST require a valid 6-digit TOTP code verification to complete login whenever 2FA is activated for an account.
- **FR-013**: System MUST issue short-lived access credentials (15-minute standard lifetime) and long-lived refresh credentials (7-day standard lifetime).
- **FR-014**: System MUST store and track active user sessions and refresh tokens in a high-performance Redis session store.
- **FR-015**: System MUST bind an ephemeral AES encryption token to each active session in Redis upon login.
- **FR-016**: System MUST obfuscate all internal database primary keys into session-scoped encrypted tokens before transmitting them to clients.
- **FR-017**: System MUST automatically decrypt incoming session-obfuscated identifiers in requests before domain logic processing.
- **FR-018**: System MUST return an HTTP 449 Retry With New Token response if an incoming ID token fails decryption.
- **FR-019**: System MUST terminate the user's active session and mandate re-authentication if three consecutive ID token decryption failures occur within that session.
- **FR-020**: System MUST rotate the refresh token upon each refresh request and invalidate the prior refresh token.
- **FR-021**: System MUST invalidate active Redis session records and revoke associated refresh tokens upon explicit user logout.
- **FR-022**: System MUST support self-service password reset initiated by submitting a registered email address.
- **FR-023**: System MUST generate a cryptographically random 6-digit OTP with a strict 15-minute expiration for password reset requests.
- **FR-024**: System MUST invalidate all active sessions for a user upon successful password reset.
- **FR-025**: System MUST record every state mutation (registration, status change, 2FA activation, password reset, account linking) into the shadow audit table (`users_aud`) within the same ACID database transaction, ensuring no plaintext PII appears in the audit log.
- **FR-026**: System MUST mask or suppress all sensitive authentication credentials (passwords, OTPs, TOTP secrets, tokens) from structured application logs.

---

### Key Entities *(include if feature involves data)*

- **User Account (`users`)**: Represents the identity profile of an individual or administrator. Key attributes include obfuscated ID, encrypted email, hashed password, encrypted name, encrypted phone, account status (`PENDING_APPROVAL`, `ACTIVE`, `REJECTED`, `SUSPENDED`), two-factor enabled flag, and timestamp metadata.
- **User Shadow Audit (`users_aud`)**: Immutable revision record capturing all mutations to user profiles. Captures revision type (`INSERT`, `UPDATE`, `DELETE`), modified field diff (PII masked), actor identifier, client IP address, and UTC timestamp.
- **TOTP Credential**: Represents the two-factor authentication configuration for a user. Key attributes include encrypted base32 secret, activation status, confirmed timestamp, and recovery metadata.
- **Federated Identity Link**: Association between a local user account and an external OAuth2 identity provider. Key attributes include provider name (`GOOGLE`), provider subject identifier, linked timestamp, and last authenticated timestamp.
- **Authentication Session (Redis)**: Ephemeral session record stored in Redis. Key attributes include session identifier, user identifier, ephemeral AES encryption key for ID obfuscation, decryption failure counter, issued timestamp, and TTL.
- **One-Time Password Token (Redis)**: Short-lived verification token generated for password reset or email verification. Key attributes include hashed token/OTP, target user ID, expiration timestamp (15 minutes), and verification attempt count.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of self-registered user accounts enter `PENDING_APPROVAL` status immediately and are prevented from receiving authentication tokens until approved by an administrator.
- **SC-002**: Zero unauthorized logins or token issuances occur for accounts in non-`ACTIVE` statuses (`PENDING_APPROVAL`, `REJECTED`, `SUSPENDED`).
- **SC-003**: 100% of database primary keys sent in API payloads are obfuscated via session-scoped encryption; zero raw database UUIDs/primary keys exposed over the wire.
- **SC-004**: 100% of sessions with 3 consecutive token decryption failures are automatically terminated within 1 second, forcing re-authentication.
- **SC-005**: User login requests (credential verification to token issuance) achieve a P95 response time under 300ms under normal load.
- **SC-006**: Password reset OTPs expire within 15 minutes, and 100% of existing active sessions are revoked immediately upon password change.
- **SC-007**: 100% of user PII attributes (email, name, phone) are stored encrypted at rest, and zero plaintext PII entries appear in database tables, audit shadow tables, or structured logs.
- **SC-008**: 100% of mutating identity actions produce corresponding immutable entries in `users_aud` within the same database transaction.

---

## Assumptions

- **A-001**: The foundation layer (Module 1) is deployed and accessible, providing the database schema, AES-256-GCM encryption converters, Redis connection pool, shared `GlobalExceptionHandler`, and `AuditInterceptor`.
- **A-002**: Email notification delivery (for sending password reset OTPs and registration confirmations) is integrated via the notification service or a compliant mock/SES client pending Module 8 deployment.
- **A-003**: Google OAuth2 client credentials (`clientId`, `clientSecret`, redirect URIs) are securely provisioned via environment configuration/vault in compliance with Module 1 standards.
- **A-004**: Frontend clients store the short-lived access token in memory or secure storage and handle HTTP 449 status codes by requesting token refresh automatically.
- **A-005**: Administrator approval of pending accounts is handled via Module 4 (Administration) or via direct administrative service APIs.
- **A-006**: Default privilege assignment upon account approval is governed by Module 3 (RBAC) and triggered upon state transition to `ACTIVE`.
