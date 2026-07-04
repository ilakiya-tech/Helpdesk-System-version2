# Architecture Overview — Carbochem Helpdesk & SLA Engine

## System Architecture

The Carbochem Helpdesk is structured as a **monolithic full-stack application** following a classic three-tier architecture:

```
Presentation Tier  → Static HTML/CSS/JS frontend
Application Tier   → Spring Boot REST API
Data Tier          → PostgreSQL relational database
```

---

## Backend Architecture

### Package Structure

```
com.workflow.engine
├── config/
│   ├── AppConstants.java         — App-wide constants (SLA durations, weights)
│   └── OpenApiConfig.java        — Swagger/OpenAPI 3 configuration with JWT scheme
├── controller/
│   ├── AuthController.java       — /api/auth/register, /api/auth/login
│   ├── TicketController.java     — /api/tickets/**
│   ├── UserController.java       — /api/users/**
│   ├── HolidayController.java    — /api/holidays/**
│   ├── ReportController.java     — /api/reports/**
│   └── SearchController.java     — /api/search/**
├── dto/
│   ├── LoginRequest.java
│   ├── RegisterRequest.java
│   ├── AssignRequest.java
│   ├── CommentRequest.java
│   └── StatusUpdateRequest.java
├── entity/
│   ├── User.java                 — User accounts (ADMIN/STAFF/CONSUMER roles)
│   ├── Ticket.java               — Core ticket entity with SLA fields & DB indexes
│   ├── Comment.java              — Ticket comments with DB index on ticketId
│   ├── ActivityHistory.java      — Audit trail with DB index on ticketId
│   └── Holiday.java              — Company holiday definitions
├── exception/
│   ├── ResourceNotFoundException.java
│   └── GlobalExceptionHandler.java  — Centralised @ControllerAdvice error handling
├── repository/
│   ├── UserRepository.java
│   ├── TicketRepository.java
│   ├── CommentRepository.java
│   ├── ActivityHistoryRepository.java
│   └── HolidayRepository.java
├── security/
│   ├── JwtTokenProvider.java         — JWT generation & validation
│   ├── JwtAuthenticationFilter.java  — Per-request JWT extraction & auth
│   ├── JwtAuthenticationEntryPoint.java — Returns 401 for missing/invalid token
│   ├── CustomUserDetailsService.java — Loads UserDetails from DB
│   └── SecurityConfig.java           — HttpSecurity rules, filter chain
└── service/
    ├── TicketService.java        — SLA engine, Max-Heap, scheduled sweeper
    ├── UserService.java
    ├── HolidayService.java
    ├── ReportService.java
    └── SearchService.java        — In-memory Trie autocomplete & search
```

---

## SLA Engine Design

### Data Structures

**Max-Heap Priority Queue** (`PriorityQueue<Ticket>`)
- Comparator based on composite priority score: `weight × time_factor`
- Ensures highest-urgency tickets are always surfaced first
- Updated on ticket creation, assignment, and status changes

**Trie (Prefix Tree)**
- Built in-memory from ticket titles and descriptions
- Enables O(k) prefix-matching search where k = query length
- Rebuilt from DB at application startup

### SLA Calculation

SLA deadlines are computed by:
1. Starting from ticket creation timestamp
2. Skipping weekend days (Saturday, Sunday)
3. Skipping configured company holidays
4. Adding the appropriate SLA duration based on priority level

### Scheduled Sweeper

A `@Scheduled` task runs every 60 seconds:
1. Loads all open, unbreached tickets
2. Compares current time against response/resolution deadlines
3. Marks tickets as `SLA_BREACHED` when deadlines are exceeded
4. Logs breach events for auditing

---

## Security Architecture

```
HTTP Request
    │
    ▼
JwtAuthenticationFilter (OncePerRequestFilter)
    │  Extract Bearer token from Authorization header
    │  Validate token signature and expiry
    │  Load UserDetails → set SecurityContext
    ▼
SecurityConfig FilterChain
    │  Permit: /api/auth/**, /swagger-ui/**, /v3/api-docs/**
    │  Authenticate: all other /api/** paths
    ▼
Controller method
    │  @PreAuthorize or role-based method security
    ▼
Service → Repository → Database
```

**JwtAuthenticationEntryPoint** intercepts unauthorized requests and returns HTTP `401 Unauthorized` with a structured JSON error body.

---

## Database Schema

### Key Tables and Indexes

**tickets**
```
id (PK), title, description, status, priority, sla_status,
assigned_to (FK → users), created_by (FK → users),
response_sla_deadline, resolution_sla_deadline, weight, created_at

Indexes: status, sla_status, priority, assigned_to,
         response_sla_deadline, resolution_sla_deadline, created_at
```

**users**
```
id (PK), username, email, password_hash, role, organization_id, created_at
```

**comments**
```
id (PK), ticket_id (FK → tickets, indexed), author_id, content, created_at
```

**activity_histories**
```
id (PK), ticket_id (FK → tickets, indexed), action, performed_by, created_at
```

**holidays**
```
id (PK), name, date, description
```

---

## Frontend Architecture

The frontend is a **server-less static SPA** organized as:

```
frontend/
├── html/         — One HTML file per view (role-scoped)
├── css/          — Global + page-specific stylesheets
└── js/
    ├── api.js    — Centralized Fetch API client with auth headers, pagination normalization
    ├── auth.js   — JWT storage/retrieval helpers
    └── sidebar.js — Dynamic role-aware navigation
```

**api.js** is the critical glue layer:
- Automatically attaches `Authorization: Bearer <token>` headers
- Normalizes Spring Page responses (extracts `.content` arrays)
- Handles token expiry and redirects to login

---

## Engineering Quality

- **Bean Validation** — All DTOs use `@NotBlank`, `@Email`, `@Size` constraints
- **Global Exception Handling** — Consistent JSON error structure for all error types
- **SLF4J Logging** — Structured logging at controller and service layers (no sensitive data)
- **Pagination** — All collection endpoints support `?page=N&size=M`
- **Database Indexes** — Covering indexes on all common query fields
- **Swagger UI** — Full OpenAPI 3 documentation with JWT Bearer auth support
- **Automated Tests** — JUnit 5 unit and MockMvc integration tests
