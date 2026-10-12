# FinFlow Frontend — React.js

[![Framework](https://img.shields.io/badge/Framework-React%2018%2B-61DAFB.svg)](https://react.dev/)
[![Build Tool](https://img.shields.io/badge/Build%20Tool-Vite-646CFF.svg)](https://vitejs.dev/)
[![API Contract](https://img.shields.io/badge/API-OpenAPI%203.0%20Generated-green.svg)](https://swagger.io/specification/)
[![Styling](https://img.shields.io/badge/Styling-Tailwind%20CSS%20%2F%20Modern%20CSS-38B2AC.svg)](https://tailwindcss.com/)
[![State Management](https://img.shields.io/badge/State-Zustand%20%2B%20TanStack%20Query-FF4154.svg)](https://tanstack.com/query)

The FinFlow frontend is a responsive, modern Single Page Application (SPA) built with **React.js** and **Vite**. It delivers intuitive financial service management for end users and powerful workflow and audit tools for administrators. The frontend strictly follows a **Contract-First approach**, consuming backend OpenAPI specifications to generate typed API clients and models.

---

## Table of Contents

- [Overview & Portals](#overview--portals)
- [Contract-First API Integration](#contract-first-api-integration)
- [Project Architecture](#project-architecture)
- [Client Security & Obfuscated Token Handling](#client-security--obfuscated-token-handling)
  - [Encrypted ID Exchange](#encrypted-id-exchange)
  - [Automatic Refresh & Retry Interceptor](#automatic-refresh--retry-interceptor)
- [Portals & User Journeys](#portals--user-journeys)
  - [User Portal](#user-portal)
  - [Admin Portal & Audit Trail Viewer](#admin-portal--audit-trail-viewer)
- [Prerequisites & Getting Started](#prerequisites--getting-started)
- [Available Scripts](#available-scripts)
- [Environment Configuration](#environment-configuration)
- [State Management & Data Fetching](#state-management--data-fetching)

---

## Overview & Portals

The frontend houses two primary interfaces within a unified application:
1. **User Financial Portal**: Dedicated to personal finance, bill payments, dynamic service subscriptions, UPI payment gateway interactions, and privilege requests.
2. **Admin Operations Portal**: Dedicated to dynamic service onboarding, BPMN workflow step configuration, privilege approvals, and system-wide audit trail inspection powered by backend shadow audit tables.

---

## Contract-First API Integration

FinFlow eliminates manual API mapping and frontend-backend drift:
- The backend maintains the source of truth in `backend/api/openapi/finflow-api.yaml`.
- The frontend runs `npm run generate:api` to automatically generate:
  - Strongly typed TypeScript models and request/response DTOs.
  - Type-safe Axios API clients in `src/api/generated/`.
- Frontend UI components and TanStack Query hooks bind directly to these generated contracts.

---

## Project Architecture

```text
frontend/
├── index.html                   # HTML entry point
├── package.json                 # Dependencies and scripts
├── vite.config.ts               # Vite configuration & proxy settings
├── public/                      # Static assets and icons
└── src/
    ├── main.tsx                 # React DOM mount point
    ├── App.tsx                  # Root router and global providers
    ├── api/
    │   ├── generated/           # Auto-generated OpenAPI TypeScript models & client
    │   ├── apiClient.ts         # Axios instance with auth & token-refresh interceptors
    │   ├── authApi.ts           # OAuth2 and 2FA endpoints
    │   ├── servicesApi.ts       # Service catalog endpoints
    │   ├── paymentsApi.ts       # UPI payment integration endpoints
    │   ├── auditApi.ts          # Endpoints fetching per-table shadow audit logs & diffs
    │   └── adminApi.ts          # Admin approvals, workflows, and catalog operations
    ├── assets/                  # Images, SVGs, and brand styling
    ├── components/
    │   ├── common/              # Buttons, modals, form inputs, toast alerts
    │   ├── layout/              # Navbar, Sidebar, AppShell
    │   ├── audit/               # Audit timeline, diff comparison viewers
    │   └── guards/              # ProtectedRoute, RoleBasedRoute, TwoFactorRoute
    ├── context/                 # AuthContext and ThemeContext
    ├── hooks/                   # Custom hooks (useAuth, useServices, useUPIPayment, useAuditLogs)
    ├── pages/
    │   ├── auth/                # Login, OAuth Callback, 2FA Verification
    │   ├── user/                # User Dashboard, Services, Payments, History
    │   └── admin/               # Service Builder, Workflow Designer, Approvals, Audit Logs
    └── styles/                  # Global styles, variables, and typography
```

---

## Client Security & Obfuscated Token Handling

### Encrypted ID Exchange
To prevent ID enumeration and data scraping, the client never receives raw database IDs (e.g., UUIDs or database sequence IDs):
- The backend emits obfuscated tokens bound to the user's active session.
- The React application sends these tokens in path parameters and request bodies as transparent identifiers.

### Automatic Refresh & Retry Interceptor
When an ID token cannot be decrypted (e.g. session refreshed or token expired):
1. The backend returns an HTTP `449 Retry With New Token` response.
2. The Axios response interceptor catches the `449` status:
   - Requests a refreshed session token from `/api/v1/auth/token-refresh`.
   - Automatically replays the original request with the fresh token.
3. **Session Lockout Handling**:
   - If 3 consecutive decryption failures occur within the same session, the backend terminates the session.
   - The interceptor detects session invalidation, clears local state, and redirects the user to `/login` with an informative notification.

```typescript
// Sample Axios Interceptor logic
apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const originalRequest = error.config;
    if (error.response?.status === 449 && !originalRequest._retry) {
      originalRequest._retry = true;
      try {
        await authApi.refreshToken();
        return apiClient(originalRequest);
      } catch (refreshErr) {
        useAuthStore.getState().logout("Session expired due to token decryption errors.");
      }
    }
    return Promise.reject(error);
  }
);
```

---

## Portals & User Journeys

### User Portal
- **Dashboard**: High-level snapshot of upcoming payments, active services, and recent payment history.
- **Service Catalog**: Browse available financial utilities and submit privilege access requests.
- **Payment Gateway Flow**: Trigger seamless UPI intent/QR code payments, track payment states, and upload payment confirmation screenshots.
- **Notification Center**: Real-time in-app alerts and delivery history.

### Admin Portal & Audit Trail Viewer
- **Service Management**: Add, modify, or archive service offerings without redeploying code.
- **Workflow Builder**: Configure visual BPMN stages for each service (due notices, reminder schedules, payment validation steps).
- **Privilege Request Manager**: Review, approve, or reject user access requests with automatic privilege assignment upon approval.
- **Audit Viewer**: Filter, search, and view historical revisions backed by per-table shadow audit tables (`users_aud`, `services_aud`, `payments_aud`), featuring point-in-time diff inspections with tokenized user identifiers.

---

## Prerequisites & Getting Started

### Prerequisites
- **Node.js 18+**
- **npm 9+** (or pnpm / yarn)

### Local Setup

1. **Install Dependencies**:
   ```bash
   cd frontend
   npm install
   ```

2. **Generate API Client from Backend OpenAPI Spec**:
   ```bash
   npm run generate:api
   ```

3. **Configure Environment Variables**:
   ```bash
   cp .env.example .env.local
   ```

4. **Start Development Server**:
   ```bash
   npm run dev
   ```
   *The application will launch at: `http://localhost:5173`*

---

## Available Scripts

| Command | Action |
| :--- | :--- |
| `npm run dev` | Starts the Vite development server with Hot Module Replacement (HMR). |
| `npm run generate:api` | Generates type-safe API clients and TypeScript interfaces from backend OpenAPI spec. |
| `npm run build` | Compiles and bundles production-ready assets into the `dist/` directory. |
| `npm run preview` | Locally serves the production build from `dist/` for verification. |
| `npm run lint` | Runs ESLint to check for code standard conformance. |
| `npm run test` | Runs unit and component tests with Vitest / React Testing Library. |

---

## Environment Configuration

Configuration variables are defined in `.env.local`:

| Variable | Description | Example |
| :--- | :--- | :--- |
| `VITE_API_BASE_URL` | Base URL for the Spring Boot backend API | `http://localhost:8080/api/v1` |
| `VITE_GOOGLE_OAUTH_CLIENT_ID` | Client ID for Google OAuth2 login flow | `[google_client_id].apps.googleusercontent.com` |
| `VITE_APP_NAME` | Display name of the application | `FinFlow` |
| `VITE_ENABLE_ANALYTICS` | Flag to enable or disable client-side telemetry | `false` |

---

## State Management & Data Fetching

- **Server State**: Managed via **TanStack Query (React Query)** for declarative caching, background refetching, and optimistic updates.
- **Client State**: Light-weight stores powered by **Zustand** for active session tokens, user privileges, and theme states.
