# MindHaven — Student Peer Support and Crisis Triage System
**IT342-G02 · Project Development Increment 1: User Accounts & Authentication**  
**Author:** Merven Loui A. Pescante

MindHaven is a privacy-preserving university peer support and crisis triage platform built with **ReactJS (Vite)**, **Spring Boot (Java)**, and **Supabase (PostgreSQL)**.

---

## Implemented Features (Increment 1)
1. **User Account Creation**
   - Student self-registration (`POST /api/v1/auth/register`) with client- and server-side validation, campus domain checks (`@cit.edu`, `*.edu`, `*.edu.ph`), duplicate-account prevention (`409 Conflict`), **BCrypt (strength 12)** password hashing, **HMAC-SHA256** student email hashing (zero plaintext student emails stored), and automatic calming pseudonym + avatar provisioning.
   - Admin provisioning of Counselor and Admin accounts (`POST /api/v1/admin/staff`) with BCrypt hashing and duplicate detection.
   - Initial System Administrator bootstrap via environment variables (`ADMIN_EMAIL`, `ADMIN_PASSWORD`) on first startup — real PostgreSQL record, no hardcoded auth or mock user data.
2. **User Login & Logout**
   - Unified login (`POST /api/v1/auth/login`) for Students, Counselors, and Admins with clear error handling (`401` invalid credentials, `423` lockout after 5 failed attempts, `403` disabled staff account).
   - Short-lived signed **JWT access tokens** (stored in React memory) paired with **HttpOnly refresh cookies** stored SHA-256 hashed in `mindhaven.refresh_token`.
   - True session termination on logout (`POST /api/v1/auth/logout`) that revokes refresh tokens in PostgreSQL and clears the cookie.
3. **Role-Based Dashboards & RBAC (Frontend + Backend)**
   - Automatic role redirect after authentication:
     - `ROLE_STUDENT` → `/student/dashboard` (pseudonymous profile editor, random handle generator, active topic rooms directory, client-side Box Breathing preview)
     - `ROLE_COUNSELOR` → `/counselor/dashboard` (live database counts, persistent on-call toggle & emergency SMS mobile number settings, monitored topic rooms)
     - `ROLE_ADMIN` → `/admin/dashboard` (live platform metrics, Counselor/Admin provisioning & enable/disable controls, Topic Room category management, immutable Security Audit Logs)
   - Enforced in **both** React Router (`ProtectedRoute`, `RoleRoute`) and Spring Security (`SecurityFilterChain` + `@PreAuthorize`), returning `401` for unauthenticated requests and `403` for cross-role access.

> See [docs/SRS_v1.1_Change_Note.md](docs/SRS_v1.1_Change_Note.md) for the SRS v1.1 note documenting email+password registration without OTP verification.

---

## Project Structure
```text
IT342-G02-Pescante-MindHaven/
├── backend/                 # Spring Boot 4.1 (Java 21+) REST API & Spring Security
│   ├── pom.xml
│   └── src/main/
│       ├── java/edu/cit/pescante/mindhaven/
│       │   ├── auth/        # Registration, Login, Refresh, Logout, /me
│       │   ├── common/      # ApiError, GlobalExceptionHandler, HealthController
│       │   ├── config/      # AppProperties, CorsConfig, AdminBootstrapConfig
│       │   ├── dashboard/   # Role-protected Student, Counselor & Admin controllers
│       │   ├── security/    # SecurityConfig, JwtService, JwtAuthenticationFilter, EmailHashService
│       │   └── user/        # JPA Entities, Repositories, PseudonymGenerator, AuditLogService
│       └── resources/
│           ├── application.yml
│           └── db/migration/V1__create_auth_tables.sql
├── frontend/                # React 18 + Vite SPA
│   └── src/
│       ├── api/             # axiosClient, authApi, dashboardApi
│       ├── components/      # AppLayout, FormInput, CalmToolkitPreview
│       ├── context/         # AuthContext (in-memory JWT + cookie refresh)
│       ├── features/auth/   # LoginPage, RegisterPage
│       ├── pages/           # StudentDashboard, CounselorDashboard, AdminDashboard, UnauthorizedPage
│       └── routes/          # AppRouter, ProtectedRoute, RoleRoute
├── docs/
│   └── SRS_v1.1_Change_Note.md
├── .env.example
└── README.md
```

---

## Prerequisites
- **Java:** JDK 21 LTS (or JDK 26)
- **Maven:** Apache Maven 3.8+ (or IntelliJ IDEA bundled Maven)
- **Node.js:** v20 LTS + npm 10+
- **Database:** Supabase PostgreSQL project (Northeast Asia — Tokyo)

---

## Setup & Execution Instructions

### 1. Configure Environment Variables
Copy `.env.example` to `.env` in the repository root and fill in your real Supabase password and generated secrets (never commit `.env`):

```powershell
Copy-Item .env.example .env
```

Generate strong random 256-bit secrets in PowerShell for `JWT_SECRET` and `EMAIL_HASH_PEPPER`:
```powershell
[Convert]::ToBase64String((1..32 | % {Get-Random -Max 256}))
```

> **Supabase Schema:** On first startup, Spring Boot automatically creates the `mindhaven` schema and tables (`student_account`, `student_profile`, `staff_user`, `refresh_token`, `topic_room`, `audit_log`). You may also run `backend/src/main/resources/db/migration/V1__create_auth_tables.sql` manually in the Supabase SQL Editor.  
> **IPv4 Network Note:** If `db.vxinubzpzhjhgiogolbe.supabase.co:5432` times out on an IPv4-only Wi-Fi network, switch `DB_URL` and `DB_USERNAME` in `.env` to Supabase's **Session pooler** string (`aws-0-ap-northeast-1.pooler.supabase.com:5432`, user `postgres.vxinubzpzhjhgiogolbe`).

### 2. Run the Backend (Spring Boot)
Using the included PowerShell launcher (automatically loads `.env` and locates Maven / JDK):

```powershell
.\run-backend.ps1
```

Or manually:

```powershell
Get-Content .env | ForEach-Object {
    if ($_ -match '^\s*([^#][^=]+)=(.*)$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim(), 'Process')
    }
}
cd backend
mvn spring-boot:run
```
Backend API runs at `http://localhost:8080` (Health check: `GET http://localhost:8080/api/v1/health`).

### 3. Run Backend Integration Tests
```powershell
cd backend
mvn test
```

### 4. Run the Frontend (React + Vite)
In a second terminal:
```powershell
cd frontend
npm install
npm run dev
```
Open `http://localhost:5173` in your browser. Vite proxies `/api/*` directly to `http://localhost:8080`.
