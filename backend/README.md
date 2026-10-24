# Finance Tracker Backend

Spring Boot app (Maven, Java).

## Requirements

- **Java 25** (JDK 25 or newer). Check with `java -version`.
- No Maven install needed; use the bundled `./mvnw`.

## Environment variables

| Variable      | Description                                  |
|---------------|----------------------------------------------|
| `DB_URL`      | JDBC URL, e.g. `jdbc:postgresql://host:5432/dbname` |
| `DB_USER`     | Database user                                |
| `DB_PASSWORD` | Database password                            |
| `PORT`        | Optional. HTTP port, defaults to `8080`      |

See `.env.example` for placeholders. Never commit real values.

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
