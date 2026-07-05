# Carbochem Helpdesk System - Developer Handover Document

This document contains a complete technical breakdown of the Helpdesk System (Version 2). It is designed to allow any new developer to immediately understand the architecture, run the project locally, and deploy it to production without any prior context.

## 1. Project Overview
The Helpdesk System is a full-stack application designed to manage support tickets, user roles, SLA (Service Level Agreement) deadlines, and holidays.
- **Backend:** Java 17 / Spring Boot 3 / Maven
- **Frontend:** Vanilla HTML, CSS, JavaScript (No framework)
- **Database:** PostgreSQL (Hosted on Neon)

---

## 2. Repository Structure
The repository uses a monorepo structure:
```text
/
├── backend/          # Spring Boot application containing pom.xml and Java source code
├── frontend/         # HTML/CSS/JS frontend
├── docs/             # Documentation files
├── README.md         # General repository readme
└── .gitignore        # Git ignore rules
```

---

## 3. Backend Architecture & Configuration
The backend is a RESTful API built with Spring Boot. It uses Spring Security for authentication and JWTs for session management.

### Key Technologies
- **Spring Boot 3.3.0** & **Java 17**
- **Spring Data JPA** (Hibernate) for database interactions
- **JWT (io.jsonwebtoken)** for stateless authentication
- **PostgreSQL JDBC Driver**

### Environment Variables (Secrets & Database)
The backend does **NOT** contain hardcoded secrets in the source code. It relies entirely on the following environment variables. **You must configure these locally on your machine and on the production server (Render).**

| Variable Name | Value / Description |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://ep-proud-tooth-aoyvotlt.c-2.ap-southeast-1.aws.neon.tech:5432/neondb?sslmode=require` |
| `SPRING_DATASOURCE_USERNAME` | `neondb_owner` |
| `SPRING_DATASOURCE_PASSWORD` | `npg_y6mvZpk2joJU` |
| `APP_JWT_SECRET` | `CarbochemHelpdeskJWTSecret2026@EnterpriseSLA#SecureKey987654321` (Used to sign JWTs) |
| `APP_ORG_SECRET` | `Carbochem@Enterprise#2026AdminSecret` (Required to register an Admin account) |

### Database Configuration Notes
- `spring.jpa.hibernate.ddl-auto=validate` is used in production. This ensures Hibernate does not accidentally drop or alter tables. When running tests or doing local heavy schema changes, this can be temporarily changed to `update`.
- The PostgreSQL dialect is automatically detected by Hibernate 6. There is no `hibernate.dialect` property explicitly set in `application.properties`.

### How to Run Locally
1. Navigate to the `backend` directory.
2. Ensure Java 17 and Maven are installed.
3. Export the 5 environment variables listed above.
4. Run the application: `mvn spring-boot:run`
5. The API will be available at `http://localhost:8080`

---

## 4. Frontend Architecture & Configuration
The frontend is a lightweight Single Page Application (SPA) built without modern frameworks like React or Angular to keep dependencies minimal.

### Key Files
- `frontend/html/index.html`: The main login and registration page.
- `frontend/js/api.js`: The central API client that handles all `fetch` requests to the backend.

### API Connection
The frontend communicates with the backend via a `baseURL` defined in `frontend/js/api.js`.
- **Production URL:** `https://helpdesk-system-version2-1.onrender.com/api`
- *To run locally against a local backend, you must change this line in `api.js` back to `http://localhost:8080/api`.*

### Vercel Routing Configuration
Because the main entry point is located at `frontend/html/index.html` (instead of the root), the frontend utilizes a `vercel.json` configuration file:
```json
{
  "redirects": [
    {
      "source": "/",
      "destination": "/html/index.html",
      "permanent": false
    }
  ]
}
```
This ensures that users visiting the root URL are safely redirected to the login page without breaking internal relative paths (`../css/`, `../js/`).

### How to Run Locally
1. Navigate to the `frontend` directory.
2. Start a simple HTTP server (do not just open the files in a browser to avoid CORS issues).
   - Python: `python -m http.server 3000`
   - Node: `npx serve .`
3. Visit `http://localhost:3000`

---

## 5. Deployment Guide

### Backend (Render)
The backend is Docker-ready. A multi-stage `Dockerfile` is located in the `backend/` directory.
1. Create a **Web Service** on Render.
2. Connect the GitHub repository.
3. **Environment:** Docker
4. **Root Directory:** `backend`
5. Under **Environment Variables**, add the 5 variables listed in section 3.
6. Deploy. Render will automatically build the Maven project and run the output JAR.

### Frontend (Vercel)
The frontend is deployed as a static site.
1. Import the repository in Vercel.
2. **Framework Preset:** Other (Static HTML)
3. **Root Directory:** `frontend`
4. Deploy. Vercel will read the `vercel.json` file and handle the routing automatically.

---

## 6. Known Quirk / Troubleshooting
- **Network Error Message:** If the frontend encounters a CORS error or if the Render backend goes to sleep (which happens on free tiers), it will display `"Network error - unable to connect to the backend server."` Ensure the backend is fully awake by hitting the backend URL directly if this happens.
- **Test Failures:** If running `mvn test`, ensure you supply the environment variables to the test runner, otherwise the Spring Context will fail to load due to missing database credentials and secrets.
