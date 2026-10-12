# FinFlow Backend — Java Spring Boot

[![Language](https://img.shields.io/badge/Language-Java%2017%2F21-ED8B00.svg)](https://openjdk.org/)
[![Framework](https://img.shields.io/badge/Framework-Spring%20Boot%203.x-6DB33F.svg)](https://spring.io/projects/spring-boot)
[![API Contract](https://img.shields.io/badge/API-OpenAPI%203.0%20(Contract--First)-green.svg)](https://swagger.io/specification/)
[![Security](https://img.shields.io/badge/Security-Spring%20Security%206-green.svg)](https://spring.io/projects/spring-security)
[![Database](https://img.shields.io/badge/Database-PostgreSQL%20%2B%20Liquibase-336791.svg)](https://www.liquibase.org/)
[![Cache](https://img.shields.io/badge/Cache-Redis-DC382D.svg)](https://redis.io/)

The FinFlow backend is an enterprise-grade modular monolith built with **Java 17/21** and **Spring Boot 3.x**. It provides transactional integrity, fine-grained role-based access control, BPMN workflow orchestration, UPI payment integration, field-level encryption for sensitive user data, contract-first API development via OpenAPI, version-controlled Liquibase migrations with automated rollback support, and comprehensive per-table audit shadow tables.

---

## Table of Contents

- [Architectural Overview](#architectural-overview)
- [Contract-First API Development (OpenAPI)](#contract-first-api-development-openapi)
- [Domain Module Structure](#domain-module-structure)
- [Security Architecture](#security-architecture)
  - [Field-Level PII Encryption (AES-256-GCM)](#field-level-pii-encryption-aes-256-gcm)
  - [Session ID Obfuscation & Failure Policy](#session-id-obfuscation--failure-policy)
  - [OAuth2 & TOTP Two-Factor Authentication](#oauth2--totp-two-factor-authentication)
- [BPMN Workflow Engine](#bpmn-workflow-engine)
- [Payments & Webhook Idempotency](#payments--webhook-idempotency)
- [Per-Table Audit Architecture & Log Retrieval](#per-table-audit-architecture--log-retrieval)
- [Database Migrations (Liquibase)](#database-migrations-liquibase)
  - [Versioning Strategy & Master Changelog](#versioning-strategy--master-changelog)
  - [Mandatory Rollback Plans](#mandatory-rollback-plans)
- [Prerequisites & Getting Started](#prerequisites--getting-started)
- [Configuration & Environment Variables](#configuration--environment-variables)
- [Testing Strategy](#testing-strategy)

---

## Architectural Overview

The backend is organized as a **modular monolith**. Each functional domain is encapsulated within its own module package with clearly defined boundaries, domain models, services, repositories, and generated OpenAPI REST controller interfaces.

```text
backend/
├── api/
│   └── openapi/
│       └── finflow-api.yaml     # Single source of truth OpenAPI 3.0 specification
├── src/
│   ├── main/
│   │   ├── java/com/finflow/
│   │   │   ├── FinflowApplication.java
│   │   │   ├── config/              # Security, Redis, Async, OpenAPI configs
│   │   │   ├── core/
│   │   │   │   ├── crypto/          # AES-256-GCM cipher & ID obfuscation utilities
│   │   │   │   ├── security/        # JWT filter, OAuth2 handler, TOTP validator
│   │   │   │   └── exception/       # Global exception handler & error codes
│   │   │   └── modules/
│   │   │       ├── auth/            # Authentication, registration, 2FA
│   │   │       ├── user/            # User profile, RBAC & privilege requests
│   │   │       ├── servicecatalog/  # Dynamic service definitions & catalog
│   │   │       ├── workflow/        # BPMN workflow lifecycle execution
│   │   │       ├── payment/         # UPI gateway client & webhook processor
│   │   │       ├── notification/    # Email, SMS, WhatsApp, In-app push
│   │   │       └── audit/           # Interceptor, shadow table queries & audit logger
│   │   └── resources/
│   │       ├── application.yml      # Base Spring Boot configuration
│   │       ├── application-dev.yml  # Development profile
│   │       ├── application-prod.yml # Production profile
│   │       └── db/changelog/        # Liquibase changelogs & versioned changesets
│   │           ├── db.changelog-master.xml
│   │           └── v1.0.0/
│   │               ├── db.changelog-v1.0.0.xml
│   │               └── 001-initial-schema.xml
│   └── test/                        # Unit and Testcontainers integration tests
├── pom.xml                          # Maven build configuration (OpenAPI & Liquibase plugins)
└── mvnw / mvnw.cmd                  # Maven wrappers
```

---

## Contract-First API Development (OpenAPI)

FinFlow enforces a strict **Contract-First workflow** across backend and frontend engineering:
1. **API Specification**: The canonical contract is defined in `backend/api/openapi/finflow-api.yaml` using OpenAPI 3.0.
2. **Backend Stubs Generation**: During `./mvnw compile`, the `openapi-generator-maven-plugin` automatically generates Spring MVC controller interfaces (`@Controller` delegates) and DTO models.
   - Controllers implement these generated interfaces, guaranteeing strict parameter and response conformity.
3. **Frontend Contract Sharing**: The frontend consumes this identical schema to auto-generate TypeScript interfaces and type-safe Axios client functions (`npm run generate:api`).
4. **Drift Prevention**: Changes to endpoints must start in `finflow-api.yaml`; CI fails if implementation diverges from the contract.

---

## Domain Module Structure

| Module | Responsibilities | Key Entities & Audit Tables |
| :--- | :--- | :--- |
| **`auth`** | OAuth2 Google login, TOTP setup & verification, session generation, JWT issuance. | `UserSession`, `TotpSecret` |
| **`user`** | User management, RBAC enforcement (`Admin`, `User`), privilege request submissions and admin review. | `User`, `Role`, `UserPrivilege`<br>*(Audited: `users_aud`, `user_privileges_aud`)* |
| **`servicecatalog`**| Pre-configured default services ("Bill Payment", "Subscription Manager") and dynamic admin service onboarding. | `ServiceDefinition`, `ServiceCategory`<br>*(Audited: `services_aud`)* |
| **`workflow`** | BPMN workflow engine integration: manages service process state, execution steps, pre/post reminders, and due-expiry alerts. | `WorkflowInstance`, `WorkflowStep`, `WorkflowSchema`<br>*(Audited: `workflows_aud`)* |
| **`payment`** | Razorpay / UPI gateway integration, payment link/intent creation, idempotent webhook handling. | `PaymentTransaction`, `PaymentMethod`<br>*(Audited: `payments_aud`)* |
| **`notification`** | Dispatches messages across Email, SMS, WhatsApp, and in-app alerts based on workflow triggers. | `NotificationLog`, `NotificationTemplate`<br>*(Audited: `notifications_aud`)* |
| **`audit`** | Captures all entity mutations into shadow tables; exposes audit query APIs with zero cleartext PII. | Shadow tables (`*_aud`), `AuditLog` |

---

## Security Architecture

### Field-Level PII Encryption (AES-256-GCM)
In compliance with data protection policies (GDPR/PCI-DSS), all Personally Identifiable Information (name, email, phone number) is encrypted before persisting to PostgreSQL.
- **Algorithm**: `AES/GCM/NoPadding` (256-bit key with 12-byte initialization vector and 128-bit authentication tag).
- **Implementation**: JPA Attribute Converters (`@Convert(converter = AesGcmCryptoConverter.class)`) ensure automatic encryption upon write and decryption upon read.
- **Key Storage**: Master encryption keys are sourced from secure environment variables or AWS KMS / HashiCorp Vault.

### Session ID Obfuscation & Failure Policy
Internal database primary keys (UUIDs / numerical IDs) are never transmitted in cleartext to the client application:
1. When a user authenticates, a unique ephemeral encryption token is bound to their active session in Redis.
2. DTO serialization encrypts all entity IDs into session-bound tokens before sending JSON responses.
3. An incoming request filter (`IdTokenDecryptionFilter`) decrypts tokens back to internal IDs.
4. **Retry & Lockout Rule**:
   - If decryption fails, the server responds with HTTP `449 (Retry With New Token)` so the frontend can refresh tokens.
   - If **3 consecutive decryption failures** occur within a session, the backend immediately terminates the session, logs a security alert, and blocks subsequent requests.

### OAuth2 & TOTP Two-Factor Authentication
- **Primary Auth**: Google OAuth2 federated identity.
- **Secondary Factor**: Time-based One-Time Password (TOTP) using standard RFC 6238 authenticators (Google Authenticator, Authy).

---

## BPMN Workflow Engine

FinFlow utilizes an embedded BPMN engine (such as **Camunda Engine** or **Flowable**) integrated into Spring Boot:
- Admins configure service workflows visually or via JSON/BPMN schemas.
- Supported step triggers:
  - **Pre-Payment Alert**: Automatic reminder prior to due date.
  - **Payment Initiation**: Generates UPI QR / payment intent.
  - **Screenshot Marking**: User upload and verification flow.
  - **Post-Payment Confirmation**: Notification dispatches and receipt generation.
  - **Rescheduling / Due Expiry**: Automated escalation or grace period handling.

---

## Payments & Webhook Idempotency

- Integrates with Indian payment gateways (e.g. Razorpay, Cashfree) supporting native UPI.
- **Webhook Resilience**:
  - Webhooks verify cryptographic HMAC SHA-256 signatures.
  - An idempotency filter checks `idempotency_key` against Redis before processing to eliminate duplicate crediting or state changes.

---

## Per-Table Audit Architecture & Log Retrieval

FinFlow implements comprehensive point-in-time state tracking across all business data:

### Shadow Audit Tables (`*_aud`)
Every domain table is paired with a corresponding shadow audit table (e.g. `users_aud`, `services_aud`, `user_privileges_aud`, `workflows_aud`, `payments_aud`).

```sql
-- Example: Shadow Audit Table Structure for Services
CREATE TABLE services_aud (
    audit_id BIGSERIAL PRIMARY KEY,
    id UUID NOT NULL,
    rev_type VARCHAR(10) NOT NULL, -- 'INSERT', 'UPDATE', 'DELETE'
    name VARCHAR(100),
    description TEXT,
    is_active BOOLEAN,
    diff_data JSONB,
    modified_by VARCHAR(100),       -- Obfuscated/Tokenized User ID
    ip_address VARCHAR(45),
    rev_timestamp TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
```

### Audit Log Retrieval API
The `audit` module queries these shadow tables to power admin audit dashboards, forensics, and user transparency views:
- **`GET /api/v1/audit/entities/{entityType}/{id}`**: Fetches the complete revision timeline and historical state diffs for a given record.
- **`GET /api/v1/audit/logs`**: Aggregated audit log stream supporting multi-criteria filtering by actor, entity type, revision type (`INSERT`, `UPDATE`, `DELETE`), and timestamp intervals.
- **Zero Raw PII Persistence**: Actor identifiers and sensitive fields in audit tables are tokenized/masked before persistence.

---

## Database Migrations (Liquibase)

FinFlow uses **Liquibase** for deterministic, repeatable, and reversible relational database migrations.

### Versioning Strategy & Master Changelog
- **Master Changelog**: Located at `src/main/resources/db/changelog/db.changelog-master.xml`. It imports version-scoped changelog directories.
- **Incremental Versioning**: Changesets follow strict semantic numbering: `v<Major>.<Minor>.<Patch>/<seq>-<description>.xml` (e.g. `v1.0.0/001-create-users-table.xml`).
- Each changeset explicitly specifies `id`, `author`, and database constraints.

### Mandatory Rollback Plans
Every single changeset defined in the codebase **must include an explicit `<rollback>` plan**:
```xml
<changeSet id="v1.0.0-001" author="finflow-team">
    <createTable tableName="services">
        <column name="id" type="UUID">
            <constraints primaryKey="true" nullable="false"/>
        </column>
        <column name="name" type="VARCHAR(100)">
            <constraints nullable="false"/>
        </column>
    </createTable>
    <rollback>
        <dropTable tableName="services"/>
    </rollback>
</changeSet>
```

### Common Liquibase Commands
```bash
# Apply all pending changesets
./mvnw liquibase:update

# Roll back the last migration changeset
./mvnw liquibase:rollback -Dliquibase.rollbackCount=1

# Roll back to a specific version tag
./mvnw liquibase:rollback -Dliquibase.rollbackTag=v1.0.0

# CI Automated Verification: executes update, verifies rollback, and reapplies
./mvnw liquibase:update-testing-rollback
```

---

## Prerequisites & Getting Started

### Prerequisites
- **Java Development Kit (JDK) 17 or 21**
- **Docker & Docker Compose** (for PostgreSQL and Redis)
- **Maven 3.8+** (or use `./mvnw`)

### Local Setup

1. **Start Database and Redis**:
   ```bash
   docker compose -f ../deploy/docker-compose.yml up -d postgres redis
   ```

2. **Configure Environment Variables**:
   Copy the example environment configuration:
   ```bash
   cp src/main/resources/application.example.yml src/main/resources/application-local.yml
   ```

3. **Build the Application & Generate OpenAPI Stubs**:
   ```bash
   ./mvnw clean compile
   ```

4. **Run Liquibase Migrations & Launch Spring Boot**:
   ```bash
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

5. **Access OpenAPI / Swagger Documentation**:
   Navigate to: `http://localhost:8080/swagger-ui/index.html`

---

## Configuration & Environment Variables

| Variable Name | Description | Default (Dev) |
| :--- | :--- | :--- |
| `SPRING_PROFILES_ACTIVE` | Active Spring profile (`dev`, `prod`, `test`) | `dev` |
| `SERVER_PORT` | HTTP server port | `8080` |
| `DATABASE_URL` | PostgreSQL JDBC connection URL | `jdbc:postgresql://localhost:5432/finflow` |
| `DATABASE_USERNAME` | PostgreSQL username | `finflow_user` |
| `DATABASE_PASSWORD` | PostgreSQL password | `finflow_secret` |
| `LIQUIBASE_ENABLED` | Enable automated Liquibase execution on startup | `true` |
| `REDIS_HOST` | Redis host | `localhost` |
| `REDIS_PORT` | Redis port | `6379` |
| `JWT_SECRET` | 256-bit secret key for JWT signing | `[configured in secret manager]` |
| `PII_ENCRYPTION_KEY` | 32-byte Base64 key for AES-256-GCM | `[configured in secret manager]` |
| `RAZORPAY_KEY_ID` | Payment gateway API key | `[test_key_id]` |
| `RAZORPAY_KEY_SECRET` | Payment gateway webhook secret | `[test_key_secret]` |

---

## Testing Strategy

- **Unit Testing**: JUnit 5 and Mockito for isolated domain service and crypto tests.
- **Contract Testing**: Validates generated OpenAPI controllers against API specifications.
- **Migration & Rollback Testing**: Validates Liquibase changesets with `./mvnw liquibase:update-testing-rollback`.
- **Integration Testing**: `@SpringBootTest` with **Testcontainers** (spinning up ephemeral PostgreSQL and Redis Docker containers during test suites).

Run the full test suite:
```bash
./mvnw clean test
```
