# Project context: Personal finance tracker (portfolio project, real users can sign up)

## What it is
- A responsive website (no PWA) for salaried users in India. The user enters a monthly salary and pay day. The app splits each pay cycle into a 50/30/20 plan (needs / wants / savings+investments), the user logs expenses, and reports compare actual spending to the plan.
- Goals: a tool the owner uses daily AND a portfolio piece for SDE / full stack job applications. No deadline. Production-quality engineering matters more than feature count.
- No AI features. AI was deliberately dropped.
- Owner: Hemant, MCA student in Bengaluru. Primary stack is MERN, but this project is Java/Spring Boot + React on purpose (second backend showcase next to the Shortify project).

## How to work with the owner
- Direct, analytical senior-advisor tone. No flattery. Point out weak reasoning, risks and blind spots.
- Simple, clear English. Bullet points. Plain text, no diagrams.
- Do not create documents or files unless asked. For open-ended requests, ask clarifying questions before producing a full plan.
- Work in small steps. Read code before running it. NEVER run git commit or git push unless explicitly told. Never weaken or delete a failing test to make it pass: report the failure and its cause.
- Never read, print, or log backend/.env or any secret. Never put real secrets, tokens, passwords or request bodies in code, logs, commits, or chat.
- Do not add dependencies unless asked, and tell the owner which ones.
- Do not edit a Flyway migration that has been applied (V1 to V3 are applied to the live database). Schema changes go in a new migration.

## Tech and versions
- Backend: Java 25 (Temurin; the shell must use JDK 25, not 27: export JAVA_HOME=$(/usr/libexec/java_home -v 25)), Spring Boot 4.1.2, Maven wrapper (./mvnw), Spring Framework 7, Spring Security 7, Hibernate 7, Jackson 3, Flyway 12, HikariCP, PostgreSQL 17 (Supabase), Tomcat 11. No Lombok.
- Starters used: webmvc, data-jpa, flyway (+ flyway-database-postgresql), validation, security, security-oauth2-resource-server, actuator; postgres driver at runtime.
- Tests: JUnit 5, Mockito, Spring Boot Testcontainers + testcontainers-postgresql (needs Docker Desktop running).
- Frontend: Vite + React + TypeScript, plain CSS, Node 24. No router, no UI library yet.
- Machine: MacBook Air, zsh. Docker Desktop 29.9.2 installed.

## Repo layout
- github.com/Hemant-Mhalsekar/finance-tracker (PUBLIC repo; never commit secrets).
- backend/ (Maven project, base package com.financetracker): packages auth, user, category, cycle, expense, onboarding, common, config, web (and settings if added).
- frontend/ (Vite app): src/App.tsx, src/AuthForm.tsx, src/api.ts, vite.config.ts, vercel.json.
- Root README.md, backend/README.md (environment variable table, how to run tests).

## Hosting (all free tiers)
- Frontend: Vercel (root directory frontend). vercel.json rewrites /api/(.*) to https://finance-tracker-h0zi.onrender.com/api/$1, so the browser sees ONE origin (this makes the httpOnly cookie work; verified).
- Backend: Render free web service, Docker runtime, root directory backend, health check path /api/ping (deliberately not /api/health, which touches the DB). Sleeps after 15 minutes idle. Cold start of the JVM is about 140 seconds on the free instance; the frontend waiting page gives up after 90 seconds (needs raising). A failed deploy leaves the previous version live. Render auto-deploys on every push.
- Database: Supabase free Postgres, Mumbai region. Connect ONLY through the SESSION POOLER (host aws-0-ap-south-1.pooler.supabase.com, port 5432, database postgres, user postgres.<PROJECT_REF>). The direct connection is IPv6-only; transaction mode (6543) is wrong for Flyway. The pooler allows max 15 clients, so the Hikari pool is set to max 3 / min idle 1 in application.yml. Free projects pause after a week of inactivity (a keep-alive idea is pending).
- IMPORTANT: local runs and the live backend share the SAME Supabase database. Be careful with manual tests; delete throwaway users afterwards. Integration tests use their own Docker database.
- Dockerfile (backend/): multi-stage, eclipse-temurin:25-jdk-noble build (-DskipTests) and 25-jre-noble runtime, non-root user, listens on PORT, JVM flags -XX:MaxRAMPercentage=65 -XX:+UseSerialGC for 512 MB. Render never runs tests.

## Environment variables (names only; values are secret)
- DB_URL (JDBC, ends with ?sslmode=require), DB_USER, DB_PASSWORD
- JWT_SECRET (at least 32 bytes; DIFFERENT local vs Render; app refuses to start without it)
- COOKIE_SECURE (default true; set false only for local http)
- Optional: SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE (overrides the yml default)
- Set them in backend/.env locally (gitignored) and in the Render dashboard. Add a variable on Render BEFORE pushing code that needs it (a missing JWT_SECRET once crashed a deploy).
- application.yml also sets: ddl-auto validate, open-in-view false, Flyway on, server.port ${PORT:8080}, spring.jackson.deserialization.accept-float-as-int false.

## Database (Flyway, all applied; Row Level Security is ENABLED with no policies on every table, the backend connects as the owner and is not blocked)
- V1: app_meta(key, value).
- V2: users(id uuid pk default gen_random_uuid(), email text, password_hash, pay_day smallint null 1..31, created_at); unique index on lower(email). refresh_tokens(id, user_id fk cascade, token_hash unique, expires_at, revoked, created_at).
- V3: categories(id, user_id fk cascade, name 1..50, type text null in NEED/WANT/INVESTMENT where NULL means "user picks type per expense", archived, created_at; unique (user_id, lower(name)); unique (id, user_id)).
  cycles(id, user_id fk cascade, period_start, period_end, salary_paise bigint >0, needs_pct/wants_pct/savings_pct smallint default 50/30/20 summing to 100, needs_paise/wants_paise/savings_paise >=0 summing to salary_paise, unique (user_id, period_start)).
  expenses(id, user_id fk cascade, category_id, category_type NOT NULL (snapshot of the type when logged), amount_paise 1..10_000_000_000, expense_date date, description 1..200, created_at; composite FK (category_id, user_id) -> categories(id, user_id) with NO ACTION, so an expense can only use its owner's category; indexes on (user_id, expense_date) and category_id). There is deliberately no FK from expenses to cycles: the cycle is found by date range.

## Domain rules
- Money: BIGINT paise everywhere (1 rupee = 100 paise). The API only accepts and returns whole paise. UI shows rupees with 2 decimals in Indian format; convert text to paise by string handling, never floats. Salary limits: 100 to 1,000,000,000 paise (Rs 1 to Rs 1 crore). Expense limit: up to 10,000,000,000 paise.
- Pay cycle: runs from one pay date to the day before the next. If the pay day does not exist in a month (29-31), use the last day of that month. CycleCalculator: payDateInMonth, containing, startingOn, nextAfter. Pay day change (decided 10 Nov 2026): the current cycle AND the next full cycle keep the old pay day; the new pay day takes effect only after that, so the transition cycle comes one cycle later (needs a pending pay day stored, so a new migration). The transition cycle itself can still be short or long (even 1 day).
- STORED CYCLES BEAT THE FORMULA: always look dates up in the database first (findContaining); the formula is only used to create new cycles after the newest stored one.
- Budget split (BudgetSplit): needs = floor(salary*50/100), wants = floor(salary*30/100), savings = remainder, so the three always equal the salary. Percentages are stored per cycle (fixed 50/30/20 for now).
- Savings is derived: salary - needs spent - wants spent. "Invested" = sum of INVESTMENT expenses, a breakdown INSIDE savings, not subtracted from it. Unspent money counts as savings. Warn if logged spending exceeds salary. Overspend flags on needs and wants even when savings covers them. Negative savings is shown as a deficit, not zero.
- Savings overview (planned): sum of saved over completed cycles that have at least one expense, current cycle shown separately. Weekly view (planned): prorated per day from the cycle's daily rate, weeks start Monday, split across cycles. Yearly (planned): cycles grouped by the calendar year of their start.
- Default categories (13, seeded at onboarding): NEED: Rent, Groceries, Transport, Utilities and Bills, Health, EMI and Loans. WANT: Dining, Shopping, Entertainment, Subscriptions. INVESTMENT: Investments, Courses and Learning. Plus "Other" with NULL type (protected: cannot be renamed, retyped, archived or deleted).
- Expenses: type comes from the category; for Other the request MUST send type; for typed categories sending type is a 400. The expense keeps the type it was logged with even if the category is retyped later (editing with the same category keeps the stored type; changing category re-snapshots). Future dates blocked, dates before 2000-01-01 blocked, date defaults to today in Asia/Kolkata (injected Clock bean, app is India-only). No new expense in an archived category (editing an old one in place is allowed).
- Limits: 50 categories per user, 20,000 expenses per user. Another user's resource always returns 404 (never 403).

## API (JSON; errors are { "error", "message" }; money in paise)
- Public: GET /api/ping ("ok"), GET /api/health ({status, db, time}; 503 if DB down), POST /api/auth/register (201), /login (200), /refresh, /logout (204). Register/login/refresh return { accessToken } and set the refresh cookie.
- Authenticated (Bearer access token): GET /api/me -> {id, email, payDay (null until onboarding)}; POST /api/onboarding {salaryPaise, payDay} -> 201 {payDay, cycle}; GET/POST /api/categories (?includeArchived), PATCH/DELETE /api/categories/{id}; POST /api/expenses, PUT/DELETE /api/expenses/{id}; GET /api/expenses ?from&to (ISO dates) &categoryId &page &size (default 20, max 100) -> {items, page, size, totalItems, totalPages}, sorted by date desc, createdAt desc, id desc.
- CycleView = {periodStart, periodEnd, salaryPaise, needsPaise, wantsPaise, savingsPaise}. ExpenseView = {id, categoryId, type, amountPaise, expenseDate, description}. CategoryView = {id, name, type|null, archived}.
- NOT BUILT YET (confirmed 10 Nov 2026: no CycleService, no settings package): GET /api/cycles/current (rolls forward: creates missing cycles up to today with INSERT ... ON CONFLICT DO NOTHING, copying salary and percentages, using the stored-cycles-first rule), PUT /api/cycles/current/salary (current cycle onward only), PUT /api/settings/pay-day (decided 10 Nov 2026: waits one FULL cycle, see Domain rules).

## Security decisions (keep them)
- Access token: HS256 JWT, 15 minutes, subject = user UUID, signed with JWT_SECRET (startup fails if missing or under 32 bytes). Stateless sessions, CSRF disabled (bearer tokens), BCrypt passwords (min 8 chars, max 72 bytes), email trimmed and lowercased.
- Refresh token: 32 random bytes (SecureRandom), base64url; only its SHA-256 hash is stored; 30 days; cookie refresh_token (HttpOnly, SameSite=Strict, Path=/api/auth, Secure from COOKIE_SECURE). Rotation is ONE atomic conditional UPDATE (revokeIfActive); presenting a revoked token again revokes all of that user's tokens (reuse detection). The frontend must never send two refreshes at once.
- Login returns the same 401 for unknown email and wrong password (dummy hash for timing). Duplicate email returns a generic 409 (known trade-off: it reveals registered emails).
- Repositories extend the bare Spring Data Repository (not JpaRepository) so there is no findById without a user: every query takes userId. Use saveAndFlush/flush THROUGH the repository (not an injected EntityManager) so database errors become DataIntegrityViolationException and map to 409 instead of 500.
- Races are handled with atomic SQL (conditional UPDATE, ON CONFLICT DO NOTHING), not read-then-write in Java.
- Global handler: never echoes rejected values, never logs request bodies, tokens, passwords or SQL; conflict logging is only the exception class and SQLState. Fractional JSON numbers for integer fields are rejected (400 "Malformed request body"). Malformed UUID or parameter gives 400 via MethodArgumentTypeMismatchException. Do not use @Validated constraints on request parameters (they would fall into the catch-all and return 500); validate manually in services.

## Code conventions
- Feature packages; records for DTOs; entities have protected no-arg constructor, getters and minimal mutators, user_id and category_id as plain UUID columns (no relationships), created_at insertable=false/updatable=false, enums as EnumType.STRING.
- Services hold the business rules and use @Transactional on every method that changes data (including derived deletes). Controllers read the user id from the JWT subject (@AuthenticationPrincipal Jwt).
- Short comments that explain WHY, not what.

## Tests (37 reported passing on 30 Oct 2026; the 9 integration tests live on the updates branch, commit 01ec401, not yet merged to main as of 10 Nov 2026)
- Unit tests: CycleCalculatorTest (10, includes a property test over every pay day 1-31 for 4 years), BudgetSplitTest (3), DefaultCategoriesTest (1), JsonDeserializationTest (2, uses the real JSON config), CategoryServiceTest (6) and ExpenseServiceTest (6) with Mockito.
- IntegrationTest (9 methods): real PostgreSQL 17 in a shared Testcontainers container, @SpringBootTest + MockMvc, covers auth, refresh rotation and reuse, onboarding races, cross-user isolation, composite FK, category and expense rules, cascade delete, and RLS-enabled on every table.
- Run: cd backend && ./mvnw test (Docker Desktop must be running). Quick summary: ./mvnw test 2>&1 | grep -E "Tests run:|BUILD".
- Mutation check idea (break the code on purpose, expect a failing test, restore with git checkout): only SecurityConfig permitAll was verified-planned; the type-snapshot and reuse-detection mutations were never run.

## Run commands (from the repo)
- Backend: cd backend && set -a; source .env; set +a; ./mvnw spring-boot:run (always load env in each new terminal; only one copy at a time because of the 15-connection pooler limit)
- Frontend: cd frontend && npm install && npm run dev (Vite proxies /api to localhost:8080)
- zsh tip: quote URLs that contain ? in curl commands.

## Frontend (current state)
- One page. api.ts keeps the access token in a module variable only (never in localStorage/sessionStorage or console logs). On load: waitForServer() polls /api/ping every 3 s up to 90 s (cold start), then refreshSession() exactly once (shared in-flight promise, never auto-retried because a lost response would trigger reuse detection), then getMe(). apiFetch retries once after a 401 via refresh. AuthForm toggles login/register. App shows waking message, error + Retry, form, or "Logged in as ...".
- Not built yet: onboarding screen (salary in rupees, pay day), add-expense (mobile-first, under 10 seconds), dashboard (wide layout for desktop), expense list, categories management, settings, reports, router, demo mode with seeded fake data, polished README with screenshots.

## Roadmap and open items
- Next: confirm/finish cycle roll-forward + salary change + pay-day change (above); then the cycle report (spent vs allocated per type, savings so far, category breakdown, overspend flags, "last expense logged X days ago"); then frontend screens; then weekly, yearly and savings overview reports.
- Deferred: the "did your salary change?" prompt each cycle (needs a stored flag, so a new migration).
- Before sharing the link: rate limiting on register/login (Bucket4j), cleanup job for expired refresh tokens, GitHub Actions CI (runners have Docker), a separate Supabase project for development, decide on cold-start fix (keep-alive ping, paid Render Starter about $7/month, or startup-time tuning), raise the frontend waiting limit above 140 s, Swagger/OpenAPI docs, account deletion, CSV export, README limitations (savings counts only logged expenses, no opening balance, no bonus income, India only, registration reveals emails).

## First thing to do in a new session
- Run git log --oneline and git status, run the tests, and report back before changing anything.
