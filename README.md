# FINCORE Nexus --- Secure Digital Banking Platform

A web-based digital banking application for managing customers, bank
accounts, deposits, withdrawals, transfers, transactions, and audit
logs.

The project contains a frontend built with HTML, React, JavaScript, and
Vite, a Java Spring Boot REST API backend, and a PostgreSQL database.

> **Development project:** This application is intended for development
> and demonstration. Do not use real banking credentials or financial
> data.

## 1. Features

### Customer Features

-   Customer portal and authentication interface
-   View customer accounts and balances
-   Create new accounts
-   Deposit and withdraw money
-   View transaction history

### Administrator Features

-   View and manage customers
-   View bank accounts and transactions
-   Freeze and unfreeze accounts
-   View audit logs

### Backend Features

-   REST API using Spring Boot
-   PostgreSQL database integration
-   Spring Data JPA / Hibernate
-   Flyway database migrations
-   JWT-based authentication and role-based authorization
-   API documentation through Swagger UI
-   In-memory caching by default
-   Optional Redis caching and Kafka integration

**Note:** The availability of individual features depends on their
frontend integration and backend implementation. Some features may
require additional development.

## 2. Technology Stack

| Component                   | Technology                     |
| :-------------------------- | :----------------------------- |
| Frontend                    | HTML, React, JavaScript        |
| Frontend Development Server | Vite                           |
| Styling                     | Tailwind CSS                   |
| Backend                     | Java 23, Spring Boot 3.3.5     |
| REST API                    | Spring Web                     |
| Database                    | PostgreSQL                     |
| ORM                         | Spring Data JPA / Hibernate    |
| Database Migrations         | Flyway                         |
| Authentication              | JWT                            |
| API Documentation           | Springdoc OpenAPI / Swagger UI |
| Build Tool                  | Maven                          |
| Optional Caching            | Redis                          |
| Optional Messaging          | Apache Kafka                   |

The frontend loads React, Recharts, Babel, and Tailwind CSS from external CDNs, so an internet connection is required to load the complete interface.


## 3. Project Structure

``` text
Secure-Digital-Banking-Platform-main/
├── backend/
│   ├── pom.xml
│   └── src/
│       └── main/
│           ├── java/com/fincore/nexus/
│           │   ├── config/
│           │   ├── controller/
│           │   ├── dto/
│           │   ├── entity/
│           │   ├── event/
│           │   ├── exception/
│           │   ├── repository/
│           │   ├── security/
│           │   ├── service/
│           │   └── FinCoreNexusApplication.java
│           └── resources/
│               ├── application.yml
│               └── db/migration/
│                   └── V1__init_banking_schema.sql
├── index.html
├── original-prototype.html
├── package.json
├── package-lock.json
├── .gitignore
└── README.md
```

## 4. Prerequisites

Install the following software:

1.  Java Development Kit (JDK) 23
2.  Apache Maven
3.  Node.js and npm
4.  PostgreSQL
5.  Visual Studio Code or another code editor

Verify the installations in a terminal:

``` powershell
java -version
mvn -version
node -v
npm -v
```

## 5. PostgreSQL Database Setup

The backend uses PostgreSQL.

### Step 1: Create the database

Open pgAdmin or the PostgreSQL command-line tool and run:

``` sql
CREATE DATABASE fincore_db;
```

### Step 2: Database connection settings

  Setting    Value
  ---------- --------------------------
  Host       `localhost`
  Port       `5433`
  Database   `fincore_db`
  Username   `postgres`
  Password   Your PostgreSQL password

Make sure PostgreSQL is running on port `5433` and that the database
exists before starting the backend.

The backend uses Flyway migrations to initialize the database schema.
Hibernate is configured to validate the schema rather than create it
automatically.

## 6. Run the Backend

### Step 1: Open the project

Extract the project ZIP and open the main project folder in Visual
Studio Code. Open a terminal using **Terminal → New Terminal**.

### Step 2: Navigate to the backend folder

``` powershell
cd backend
```

### Step 3: Configure environment variables

Replace `YOUR_POSTGRES_PASSWORD` with your actual PostgreSQL password:

``` powershell
$env:DB_URL="jdbc:postgresql://localhost:5433/fincore_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="YOUR_POSTGRES_PASSWORD"
$env:JWT_SECRET="ReplaceWithYourOwnLongRandomDevelopmentSecret"
```

Use a sufficiently long, randomly generated JWT secret. These
environment variables apply to the current PowerShell terminal session.
Do not commit database passwords or JWT secrets to GitHub.

### Step 4: Start Spring Boot

From the `backend` folder, run:

``` powershell
mvn spring-boot:run
```

Wait for the application to start successfully and keep this terminal
open.

### Step 5: Verify the backend

If the health endpoint is enabled, open:

http://localhost:8080/actuator/health

A successful response may look like:

``` json
{
  "status": "UP"
}
```

If startup fails, inspect the terminal for database connection,
migration, configuration, or dependency errors.

## 7. Run the Frontend

Open a **second terminal** in the main project folder---the folder
containing `package.json`. Do not stop the backend terminal.

Install dependencies:

``` powershell
npm install
```

Start Vite:

``` powershell
npm run dev
```

Open the local URL shown by Vite, normally:

http://127.0.0.1:5173/

Keep both the frontend and backend running.

## 8. Frontend and Backend Integration

The frontend is configured to communicate with the backend at:

``` text
http://localhost:8080
```

The frontend settings in `index.html` are:

``` javascript
const USE_BACKEND = true;
const API_URL = 'http://localhost:8080';
```

When backend mode is enabled, the frontend sends API requests to the
Spring Boot application.

### Frontend-only simulation mode

To run only the frontend without the backend, change:

``` javascript
const USE_BACKEND = true;
```

to:

``` javascript
const USE_BACKEND = false;
```

Refresh the frontend after making the change. Simulation mode uses
fictional, in-memory data; changes may be lost when the page is
refreshed. Simulation mode does not test the database or backend API.

## 9. API Documentation

When the backend is running, try these URLs if the corresponding
endpoints are enabled:

-   **Swagger UI:** http://localhost:8080/swagger-ui.html
-   **OpenAPI specification:** http://localhost:8080/v3/api-docs
-   **Health check:** http://localhost:8080/actuator/health

Some endpoints require a valid JWT token and appropriate permissions.

### Development authentication

The backend includes a development token endpoint:

``` text
POST http://localhost:8080/api/auth/token
```

The endpoint accepts username and role parameters for generating a
development JWT. Roles may include `CUSTOMER`, `TELLER`, and `ADMIN`,
depending on the backend configuration.

Use development authentication only in a controlled environment. It is
not a substitute for production-grade authentication.

## 10. Optional Services

### Redis

In-memory caching is used by default. Redis is optional unless Redis
caching is enabled. If required by your configuration, set:

``` powershell
$env:CACHE_TYPE="redis"
$env:REDIS_HOST="localhost"
$env:REDIS_PORT="6379"
```

Redis must be installed and running when Redis caching is enabled.

### Apache Kafka

Kafka is disabled by default and is not required for the default
configuration. To enable it, configure a Kafka broker and set:

``` powershell
$env:KAFKA_ENABLED="true"
$env:KAFKA_BOOTSTRAP_SERVERS="localhost:9092"
```

## 11. Troubleshooting

### `java` is not recognized

Install JDK 23, configure Java and PATH, then reopen the terminal.

### `mvn` is not recognized

Install Apache Maven and add its `bin` directory to the Windows PATH.

### `npm` is not recognized

Install Node.js from https://nodejs.org/, reopen the terminal, and check
`node -v` and `npm -v`.

### PostgreSQL connection failed

Check that PostgreSQL is running, `fincore_db` exists, the server is
listening on port `5433`, and the database username and password are
correct. Confirm that `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` match
your configuration.

### Backend fails during startup

Inspect the backend terminal for database connection errors, migration
failures, missing environment variables, or dependency errors. Make sure
`JWT_SECRET` is configured.

### Frontend cannot reach the backend

Confirm that Spring Boot is running on port `8080`, the frontend API URL
is correct, and the browser console and backend terminal do not show
request or authorization errors.

### Login or data loading fails

Demo credentials from a frontend simulation are not necessarily
registered as real backend users. Check the authentication response,
database contents, backend logs, and API permissions.

## 12. Security Notes

-   This is a development and demonstration project, not a production
    banking service.
-   Never use real banking credentials or customer data.
-   Keep database passwords and JWT signing secrets out of source
    control.
-   Do not expose development authentication endpoints to untrusted
    users.
-   Review authentication, authorization, input validation, transaction
    handling, and deployment security before production use.

## 13. License and Usage

Review the repository's licensing and dependency requirements before
redistributing or deploying this project.

## 14. Quick Start Summary

### Terminal 1 --- Backend

Run these commands from the main project folder:

``` powershell
cd backend
$env:DB_URL="jdbc:postgresql://localhost:5433/fincore_db"
$env:DB_USERNAME="postgres"
$env:DB_PASSWORD="YOUR_POSTGRES_PASSWORD"
$env:JWT_SECRET="ReplaceWithYourOwnLongRandomDevelopmentSecret"
mvn spring-boot:run
```

### Terminal 2 --- Frontend

Run these commands from the main project folder in a second terminal:

``` powershell
npm install
npm run dev
```

### Open the application

-   **Frontend:** http://127.0.0.1:5173/
-   **Backend health check:** http://localhost:8080/actuator/health
-   **Swagger UI:** http://localhost:8080/swagger-ui.html

The frontend, backend, and database must be configured and running for
full-stack functionality.
