# 🏗️ Carbochem Helpdesk & SLA Engine

A full-stack **enterprise helpdesk ticketing system** built for Carbochem Construction Operations. Features JWT-secured REST APIs, an intelligent SLA engine with Max-Heap priority scheduling, and a clean multi-role HTML/CSS/JS frontend.

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Backend Setup](#backend-setup)
  - [Frontend Setup](#frontend-setup)
- [API Documentation](#api-documentation)
- [Architecture](#architecture)
- [User Roles](#user-roles)
- [SLA Engine](#sla-engine)
- [Testing](#testing)

---

## Overview

The Carbochem Helpdesk & SLA Engine is a production-ready ticketing platform built to manage internal support workflows for a construction company. The system supports three user roles (Admin, Staff, Consumer), enforces SLA deadlines with automated breach detection, and provides a Swagger-documented REST API.

---

## ✨ Features

- **JWT Authentication** — Secure stateless login with role-based access control
- **Multi-Role Dashboard** — Separate views for Admin, Staff (Agents), and Consumers
- **SLA Engine** — Automated SLA deadline calculation with Priority-weighted Max-Heap scheduling
- **SLA Breach Detection** — Scheduled sweeper detects and escalates breached tickets every 60 seconds
- **Ticket Lifecycle** — Full CRUD: Create → Assign → Comment → Resolve → Close
- **Holiday-Aware SLA** — SLA calculation respects configured company holidays
- **Pagination** — All collection endpoints support page/size query parameters
- **Swagger UI** — Interactive API documentation with Bearer JWT support
- **Search** — Real-time ticket search powered by an in-memory Trie data structure
- **Reporting** — SLA compliance reports and ticket statistics for admins
- **Activity History** — Full audit trail for every ticket action

---

## 🛠️ Tech Stack

### Backend
| Component       | Technology                          |
|----------------|-------------------------------------|
| Language        | Java 17                             |
| Framework       | Spring Boot 3.x                     |
| Security        | Spring Security + JWT (JJWT)        |
| Persistence     | Spring Data JPA + Hibernate         |
| Database        | PostgreSQL                          |
| Build Tool      | Apache Maven                        |
| Documentation   | SpringDoc OpenAPI 3 (Swagger UI)    |
| Scheduling      | Spring `@Scheduled` tasks           |
| Validation      | Jakarta Bean Validation             |

### Frontend
| Component       | Technology                          |
|----------------|-------------------------------------|
| Structure       | HTML5 (Semantic)                    |
| Styling         | Vanilla CSS3 (Custom Properties)    |
| Logic           | Vanilla JavaScript (ES6+)           |
| API Client      | Fetch API (centralized `api.js`)    |

---

## 📁 Project Structure

```
Helpdesk-System-version2/
├── backend/                          # Spring Boot application
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/workflow/engine/
│       │   │   ├── config/           # OpenAPI, App constants
│       │   │   ├── controller/       # REST Controllers
│       │   │   ├── dto/              # Request/Response DTOs
│       │   │   ├── entity/           # JPA Entities
│       │   │   ├── exception/        # Global Exception Handler
│       │   │   ├── repository/       # Spring Data JPA Repositories
│       │   │   ├── security/         # JWT Provider, Filter, Security Config
│       │   │   └── service/          # Business Logic & SLA Engine
│       │   └── resources/
│       │       └── application.properties
│       └── test/                     # Unit and Integration Tests
│           └── java/com/workflow/engine/
│               ├── controller/       # AuthControllerTest
│               └── service/          # TicketServiceTest
├── frontend/                         # Static HTML/CSS/JS application
│   ├── html/                         # HTML pages per role/view
│   ├── css/                          # Stylesheets
│   └── js/                           # JavaScript modules
│       ├── api.js                    # Centralized API client
│       ├── auth.js                   # Auth helpers
│       └── sidebar.js                # Navigation sidebar
├── docs/                             # Architecture & design documents
├── screenshots/                      # UI screenshots
├── .gitignore
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- **Java 17+** — [Download JDK](https://adoptium.net/)
- **Maven 3.8+** — [Download Maven](https://maven.apache.org/download.cgi)
- **PostgreSQL 14+** — [Download PostgreSQL](https://www.postgresql.org/download/)
- **Git**

---

### Backend Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/ilakiya-tech/Helpdesk-System-version2.git
   cd Helpdesk-System-version2
   ```

2. **Set up PostgreSQL database**
   ```sql
   CREATE DATABASE helpdesk_db;
   CREATE USER helpdesk_user WITH ENCRYPTED PASSWORD 'your_password';
   GRANT ALL PRIVILEGES ON DATABASE helpdesk_db TO helpdesk_user;
   ```

3. **Configure application properties**

   Edit `backend/src/main/resources/application.properties`:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/helpdesk_db
   spring.datasource.username=helpdesk_user
   spring.datasource.password=your_password
   
   app.jwt.secret=your_jwt_secret_key_here
   app.organization.secret=your_org_secret_for_admin_registration
   ```

4. **Build and run**
   ```bash
   cd backend
   mvn clean install
   mvn spring-boot:run
   ```

   The backend will start on **http://localhost:8080**

---

### Frontend Setup

The frontend is a static web application — no build step required.

1. Serve the `frontend/` directory using any static file server:

   ```bash
   # Using Python
   cd frontend
   python -m http.server 3000
   ```

   Or open `frontend/html/index.html` directly in a browser.

2. The frontend connects to the backend at `http://localhost:8080` by default.  
   To change the API base URL, edit `frontend/js/api.js` → `BASE_URL` constant.

---

## 📖 API Documentation

Once the backend is running, Swagger UI is available at:

```
http://localhost:8080/swagger-ui/index.html
```

All endpoints are documented with:
- Summary and description
- Request body schemas
- Response codes
- Bearer JWT authentication support

Click **Authorize** in Swagger UI and paste your JWT token to test secured endpoints.

---

## 🏛️ Architecture

```
Browser (Frontend)
      │
      │  HTTP/REST (JSON)
      ▼
Spring Security Filter Chain
      │
      │  JWT Validation
      ▼
REST Controllers (Spring MVC)
      │
      ├── AuthController        /api/auth/**
      ├── TicketController      /api/tickets/**
      ├── UserController        /api/users/**
      ├── HolidayController     /api/holidays/**
      ├── ReportController      /api/reports/**
      └── SearchController      /api/search/**
      │
      ▼
Service Layer
      ├── TicketService         (SLA Engine + Max-Heap Priority Queue)
      ├── UserService
      ├── HolidayService
      ├── ReportService
      └── SearchService         (In-memory Trie)
      │
      ▼
Repository Layer (Spring Data JPA)
      │
      ▼
PostgreSQL Database
```

---

## 👥 User Roles

| Role         | Capabilities |
|-------------|--------------|
| **Admin**   | Full access: manage users, staff, holidays, view reports, assign tickets, manage all tickets |
| **Staff**   | View assigned tickets, update status, add comments |
| **Consumer** | Create tickets, view own tickets, add comments, track SLA status |

### Default Registration
- **Consumer**: Register freely at `/html/register.html`
- **Staff**: Register with a staff key (configured in properties)
- **Admin**: Register with the organization secret key

---

## ⏱️ SLA Engine

The SLA engine is the core innovation of this system:

| Priority   | Response SLA | Resolution SLA |
|-----------|-------------|----------------|
| CRITICAL  | 1 hour      | 4 hours        |
| HIGH      | 4 hours     | 24 hours       |
| MEDIUM    | 8 hours     | 48 hours       |
| LOW       | 24 hours    | 72 hours       |

**Key behaviors:**
- SLA deadlines are calculated at ticket creation, skipping **holidays** and **weekends**
- A **Max-Heap priority queue** ensures highest-urgency tickets surface first
- A **scheduled sweeper** (every 60 seconds) detects breached SLAs and escalates ticket status
- SLA tracking **pauses** when a ticket is assigned, resumes on re-open

---

## 🧪 Testing

Run the full test suite:

```bash
cd backend
mvn clean test
```

Test coverage includes:
- **TicketServiceTest** — Unit tests for SLA deadline calculation, priority weighting, breach detection
- **AuthControllerTest** — Integration tests for registration, login, unauthorized access (HTTP 401)

---

## 📄 License

This project was built as a portfolio/demonstration project. All rights reserved © 2025 Ilakiya.

---

## 🙏 Acknowledgments

Built with Spring Boot, PostgreSQL, and vanilla web technologies as a full-stack portfolio project demonstrating enterprise software engineering practices.
