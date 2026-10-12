# Quickstart & Validation Guide: Module 2 — Authentication

**Feature ID:** MOD-002  
**Module:** Authentication  
**Branch:** `002-authentication`  
**Date:** 2026-10-12  

---

## 1. Prerequisites

Before executing the validation scenarios, ensure the following are available and running:

1. **Docker & Docker Compose** (PostgreSQL 15+ and Redis 7+)
2. **Java Development Kit (JDK 17 or 21)**
3. **Maven 3.8+** (or `./mvnw` inside `backend/`)
4. **Curl** or **HTTPie** and **jq** for parsing JSON responses
5. **oathtool** (optional CLI tool for generating RFC 6238 TOTP codes, e.g. `oathtool --totp -b <SECRET>`)

---

## 2. Infrastructure & Environment Setup

Start local background services (PostgreSQL and Redis):

```bash
docker compose -f deploy/docker-compose.infra.yml up -d
```

Export necessary environment variables for local testing:

```bash
export SPRING_PROFILES_ACTIVE=dev
export PII_ENCRYPTION_KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY="
export JWT_SECRET="dGhpc2lzYXZlcnlzZWNyZXRrZXlmb3Jqd3RzaWduaW5nMTIzNDU="
export DATABASE_URL="jdbc:postgresql://localhost:5432/finflow"
export DATABASE_USERNAME="finflow_user"
export DATABASE_PASSWORD="finflow_secret"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
```

Start the application backend:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Verify backend is healthy:
```bash
curl -s http://localhost:8080/actuator/health | jq .
```

---

## 3. End-to-End Validation Scenarios

### Scenario 1: Automated Liquibase Migration & Rollback Test
Validates that the Module 2 database changesets (`004-create-auth-module-tables.xml`) apply cleanly and can be safely rolled back in CI:

```bash
cd backend
./mvnw liquibase:update-testing-rollback
```

**Expected Outcome:**
- Liquibase applies all changesets, rolls back to test teardown safety, and reapplies.
- `BUILD SUCCESS` with zero errors.

---

### Scenario 2: Self-Registration with Approval Hold
Validates FR-001, FR-002, and FR-003:

1. Submit new registration:
```bash
curl -s -i -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah.connor@example.com",
    "password": "Password123!",
    "firstName": "Sarah",
    "lastName": "Connor",
    "phone": "+15550192834"
  }'
```

**Expected Outcome:**
- HTTP status `201 Created`
- Response body contains `{"status": "PENDING_APPROVAL", "message": "..."}`
- Zero session or access tokens returned in the payload.

2. Attempt immediate login with newly registered credentials:
```bash
curl -s -i -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah.connor@example.com",
    "password": "Password123!"
  }'
```

**Expected Outcome:**
- HTTP status `403 Forbidden`
- Problem detail indicates `ACCOUNT_PENDING_APPROVAL`. Token issuance strictly denied.

3. Verify audit record in PostgreSQL:
```bash
docker exec -i finflow-postgres psql -U finflow_user -d finflow -c \
  "SELECT rev_type, modified_by, rev_timestamp FROM users_aud ORDER BY rev_timestamp DESC LIMIT 1;"
```
- Returns `INSERT` audit row with UTC timestamp. Zero plaintext PII in database.

---

### Scenario 3: Active User Login & Session ID Obfuscation
Validates FR-004, FR-015, FR-016, and SC-003:

1. Approve account directly in PostgreSQL (simulating Module 4 Admin approval):
```bash
docker exec -i finflow-postgres psql -U finflow_user -d finflow -c \
  "UPDATE users SET status = 'ACTIVE' WHERE status = 'PENDING_APPROVAL';"
```

2. Authenticate with valid credentials:
```bash
LOGIN_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah.connor@example.com",
    "password": "Password123!"
  }')

echo $LOGIN_RESP | jq .
```

**Expected Outcome:**
- HTTP status `200 OK`
- `status`: `"AUTHENTICATED"`
- Contains `accessToken` (15-min JWT) and `refreshToken` (7-day token).
- `user.id` starts with `id_` (obfuscated per-session AES ciphertext).
- **Zero raw UUIDs exposed** in any JSON field.

3. Verify Redis session registry:
```bash
docker exec -i finflow-redis redis-cli KEYS "auth:session:*"
```
- Active session key exists in Redis with TTL ~604800s.

---

### Scenario 4: RFC 6238 TOTP Multi-Factor Authentication
Validates FR-010, FR-011, and FR-012:

1. Extract access token from Scenario 3:
```bash
ACCESS_TOKEN=$(echo $LOGIN_RESP | jq -r .accessToken)
```

2. Initiate 2FA enrollment:
```bash
SETUP_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/mfa/setup \
  -H "Authorization: Bearer $ACCESS_TOKEN")

echo $SETUP_RESP | jq .
```
- Returns `secret` (Base32) and `qrCodeUri` (`otpauth://totp/FinFlow:...`).

3. Generate current 6-digit TOTP code and confirm setup:
```bash
SECRET=$(echo $SETUP_RESP | jq -r .secret)
TOTP_CODE=$(oathtool --totp -b "$SECRET")

curl -s -i -X POST http://localhost:8080/api/v1/auth/mfa/confirm \
  -H "Authorization: Bearer $ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"code\": \"$TOTP_CODE\"}"
```
- Returns `200 OK` with confirmation notice. `two_factor_enabled` is now active.

4. Authenticate again with email and password:
```bash
CHALLENGE_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah.connor@example.com",
    "password": "Password123!"
  }')

echo $CHALLENGE_RESP | jq .
```

**Expected Outcome:**
- Returns `{"status": "MFA_REQUIRED", "mfaToken": "...", "expiresIn": 300}`.
- Zero session credentials issued yet.

5. Complete challenge with current TOTP code:
```bash
MFA_TOKEN=$(echo $CHALLENGE_RESP | jq -r .mfaToken)
CURRENT_CODE=$(oathtool --totp -b "$SECRET")

curl -s -X POST http://localhost:8080/api/v1/auth/mfa/verify \
  -H "Content-Type: application/json" \
  -d "{\"mfaToken\": \"$MFA_TOKEN\", \"code\": \"$CURRENT_CODE\"}" | jq .
```
- Returns `200 OK` with full `accessToken`, `refreshToken`, and obfuscated `user` profile.

---

### Scenario 5: Token Refresh, Rotation & Revocation
Validates FR-013, FR-020, and FR-021:

1. Refresh access token using refresh token:
```bash
REFRESH_TOKEN=$(echo $LOGIN_RESP | jq -r .refreshToken)

REFRESH_RESP=$(curl -s -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$REFRESH_TOKEN\"}")

echo $REFRESH_RESP | jq .
```
- Returns new `accessToken` and new rotated `refreshToken`.

2. Explicit logout:
```bash
NEW_ACCESS_TOKEN=$(echo $REFRESH_RESP | jq -r .accessToken)
NEW_REFRESH_TOKEN=$(echo $REFRESH_RESP | jq -r .refreshToken)

curl -s -i -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $NEW_ACCESS_TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$NEW_REFRESH_TOKEN\"}"
```
- Returns `200 OK` ("Logged out successfully").

3. Verify revoked token rejected:
```bash
curl -s -i -X POST http://localhost:8080/api/v1/auth/refresh \
  -H "Content-Type: application/json" \
  -d "{\"refreshToken\": \"$NEW_REFRESH_TOKEN\"}"
```
- Returns `401 Unauthorized`. Session and refresh token no longer exist.

---

### Scenario 6: ID Obfuscation Decryption Failure Protection
Validates FR-018, FR-019, and SC-004:

1. Obtain a fresh session via login.
2. Submit a request using an invalid or corrupted obfuscated ID token (e.g. `id_invalid_corrupted_payload`):
```bash
curl -s -i -H "Authorization: Bearer $ACCESS_TOKEN" \
  http://localhost:8080/api/v1/users/id_invalid_corrupted_payload
```
- Returns **HTTP 449 Retry With New Token** with Problem Detail `ID_DECRYPTION_FAILED`.
- Client is informed to refresh token.

3. Submit 3 consecutive corrupted ID tokens:
- On the 3rd consecutive failure, Redis session is terminated immediately.
- Returns **HTTP 401 Unauthorized** ("Session terminated due to excessive decryption failures").
- Next request with previous JWT is rejected with HTTP 401.

---

### Scenario 7: Password Reset via Time-Limited OTP
Validates FR-022, FR-023, and FR-024:

1. Request password reset OTP:
```bash
curl -s -i -X POST http://localhost:8080/api/v1/auth/password-reset/request \
  -H "Content-Type: application/json" \
  -d '{"email": "sarah.connor@example.com"}'
```
- Returns `200 OK` ("If your email is registered, a password reset code has been sent.").

2. Retrieve OTP from Redis (dev simulation):
```bash
docker exec -i finflow-redis redis-cli KEYS "auth:otp:password_reset:*"
```

3. Confirm password reset with OTP and new password:
```bash
curl -s -i -X POST http://localhost:8080/api/v1/auth/password-reset/confirm \
  -H "Content-Type: application/json" \
  -d '{
    "email": "sarah.connor@example.com",
    "otp": "492019",
    "newPassword": "BrandNewPassword2026!"
  }'
```
- Returns `200 OK` ("Password reset successful. All active sessions have been terminated.").
- All previous active sessions in Redis are terminated.
- Prior credentials fail; login succeeds only with `BrandNewPassword2026!`.

---

## 4. Teardown

To stop local infrastructure:
```bash
docker compose -f deploy/docker-compose.infra.yml down
```
