# Quickstart & Validation Guide: Module 1 — Application Foundation

**Feature ID:** MOD-001  
**Module:** Application Foundation  
**Branch:** `001-app-foundation`  
**Date:** 2026-10-12  

---

## 1. Prerequisites

Before running the foundation validation scenarios, ensure the following are present on your development machine or container host:

1. **Docker & Docker Compose** (PostgreSQL 15+ and Redis 7+)
2. **Java Development Kit (JDK 17 or 21)**
3. **Maven 3.8+** (or `./mvnw` inside `backend/`)
4. **Curl** or **HTTPie** for endpoint validation

---

## 2. Infrastructure Setup

Start the background infrastructure containers (PostgreSQL and Redis) from the repository root:

```bash
docker compose -f deploy/docker-compose.infra.yml up -d
```

Verify containers are healthy:
```bash
docker compose -f deploy/docker-compose.infra.yml ps
```

Ensure `finflow_db` is listening on `5432` and `redis` is listening on `6379`.

---

## 3. Environment Configuration

Create a local environment configuration file:

```bash
cp deploy/env/.env.example deploy/env/.env
```

Ensure minimum environment secrets are exported for local development:
```bash
export SPRING_PROFILES_ACTIVE=dev
export PII_ENCRYPTION_KEY="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=" # 32-byte Base64 key
export JWT_SECRET="dGhpc2lzYXZlcnlzZWNyZXRrZXlmb3Jqd3RzaWduaW5nMTIzNDU="
export DATABASE_URL="jdbc:postgresql://localhost:5432/finflow"
export DATABASE_USERNAME="finflow_user"
export DATABASE_PASSWORD="finflow_secret"
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export SUPER_ADMIN_EMAIL="admin@finflow.internal"
export SUPER_ADMIN_PASSWORD="AdminSecurePassword123!"
```

---

## 4. Validation Scenarios

### Scenario 1: Automated Liquibase Migration & Rollback Test
Verifies that all foundation changesets apply cleanly and can be safely rolled back in CI:

```bash
cd backend
./mvnw liquibase:update-testing-rollback
```

**Expected Outcome:**
- Liquibase applies all changesets in `v1.0.0/`.
- Executes rollback testing on each changeset.
- Reapplies the changesets.
- Returns `BUILD SUCCESS` with zero errors.

---

### Scenario 2: Application Startup & Super Admin Bootstrap
Launches the Spring Boot backend in `dev` mode:

```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

**Expected Outcome in Logs:**
1. Liquibase executes pending migrations; logs `Liquibase: Successfully acquired change log lock` and `Update has found - 0 changesets to execute` (or executed count).
2. `RedisHealthValidator` pings Redis; logs `Redis connection verified: PONG`.
3. `SuperAdminInitializer` executes; detects 0 existing Super Admin accounts; logs `Provisioned initial Super Admin account with ID [tokenized] and email [masked]`.
4. Embedded Tomcat starts on port `8080` in < 15 seconds.

---

### Scenario 3: Actuator Health & Probes
In a separate terminal, test the system health endpoint:

```bash
curl -i http://localhost:8080/actuator/health
```

**Expected Outcome:**
- HTTP status: `200 OK`
- JSON payload:
  ```json
  {
    "status": "UP",
    "components": {
      "db": {
        "status": "UP",
        "details": {
          "database": "PostgreSQL"
        }
      },
      "redis": {
        "status": "UP"
      }
    }
  }
  ```

---

### Scenario 4: Request Tracing & Correlation Header
Send a request with a custom correlation ID:

```bash
curl -i -H "X-Correlation-ID: 7a8f1e3c-89b1-4f76-bc21-0a1b2c3d4e5f" http://localhost:8080/actuator/info
```

**Expected Outcome:**
- Response headers include: `X-Correlation-ID: 7a8f1e3c-89b1-4f76-bc21-0a1b2c3d4e5f`.
- Server log lines for this request include `[correlationId=7a8f1e3c-89b1-4f76-bc21-0a1b2c3d4e5f]`.

---

### Scenario 5: Global Exception Handling (RFC 7807)
Trigger a 404 / 400 error:

```bash
curl -i http://localhost:8080/api/non-existent-endpoint
```

**Expected Outcome:**
- HTTP status: `404 Not Found`
- Content-Type: `application/problem+json`
- Response contains `type`, `title`, `status`, `detail`, `instance`, and `correlationId`.
- No raw Java stack traces appear in the response payload.

---

### Scenario 6: Database Verification of Super Admin & Shadow Audit
Inspect PostgreSQL tables using `psql` or a database client:

```bash
docker exec -it finflow-postgres psql -U finflow_user -d finflow -c "SELECT id, status, must_change_password FROM users;"
docker exec -it finflow-postgres psql -U finflow_user -d finflow -c "SELECT audit_id, id, rev_type, modified_by, ip_address, rev_timestamp FROM users_aud;"
```

**Expected Outcome:**
- `users` table contains 1 row with `status = 'ACTIVE'`, `must_change_password = true`, and encrypted `email`.
- `users_aud` table contains 1 row with `rev_type = 'INSERT'`, `modified_by = 'SYSTEM_BOOTSTRAP'`, and JSONB `diff_data`.
- Email and password are not stored in cleartext.

---

## 5. Teardown

To shut down infrastructure:

```bash
docker compose -f deploy/docker-compose.infra.yml down
```
