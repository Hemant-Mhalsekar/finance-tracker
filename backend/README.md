# Finance Tracker Backend

Spring Boot app (Maven, Java).

## Requirements

- **Java 25** (JDK 25 or newer). Check with `java -version`.
- No Maven install needed; use the bundled `./mvnw`.

## Environment variables

| Name | Required | Where to set it | Notes |
|------|----------|-----------------|-------|
| `DB_URL` | Required | `backend/.env` locally, Render dashboard in production | JDBC URL of the Supabase session pooler (ends with `?sslmode=require`). |
| `DB_USER` | Required | `backend/.env` locally, Render dashboard in production | The pooler username. |
| `DB_PASSWORD` | Required | `backend/.env` locally, Render dashboard in production | Database password. |
| `JWT_SECRET` | Required | `backend/.env` locally, Render dashboard in production | Random string of at least 32 bytes, different in each environment. |
| `COOKIE_SECURE` | Optional | `backend/.env` locally, Render dashboard in production | Defaults to `true`. Set to `false` only for local development over http. |
| `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` | Optional | `backend/.env` locally, Render dashboard in production | Overrides the default pool size. |

Set these in `backend/.env` locally (never commit it) and in the Render dashboard in production. Never put real values in the repository.

## Export variables in a macOS terminal (zsh)

Replace the values with your own, in the same terminal window you run the app from:

```sh
export DB_URL='jdbc:postgresql://your-db-host:5432/your-db-name'
export DB_USER='your-db-user'
export DB_PASSWORD='your-db-password'
```

They only last for that terminal session. Use single quotes so special characters in the password are not interpreted.

## Run

```sh
cd backend
./mvnw spring-boot:run
```

## Endpoints

- `GET /api/ping` returns `ok` (no database access).
- `GET /api/health` returns JSON with `status`, `db` and `time`. Returns HTTP 503 with `"db": "down"` if the database check fails.
