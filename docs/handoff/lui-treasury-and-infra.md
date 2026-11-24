# Handoff: treasury-service, Docker/Compose and MySQL (Lui)

Branch: `claude/keen-darwin-9gadwh` · Status: **done, tested, ready to merge**

When this branch was cut, every other module (including `orgwise-common`, the frontend shell and
`docs/internal-api-contracts.md`) was still an empty placeholder. So treasury was built to stand
on its own, and this note lists **every assumption it makes about your pieces**. Please check the
section with your name before merging. Each item is either "make yours match" or "tell Lui and
I'll change mine".

## 1. What is in this branch

| Area | Files |
|---|---|
| Treasury service | `treasury-service/**`: entities, repositories, `TransferService`, `DisbursementSaga`, `PendingTransferReconciler`, bootstrap, Feign client + Resilience4J fallback, JWT security, controllers, exception handler, 182 tests (99% line coverage) |
| Treasury config | `config-repo/treasury-service.yml` |
| Docker | `*/Dockerfile` for all 8 backend modules (one shared template), `frontend/Dockerfile`, `frontend/nginx.conf`, `.dockerignore`, `frontend/.dockerignore` |
| Compose / DB | `docker-compose.yml`, `db/init/01-create-schemas.sql`, `db/init/02-create-users.sh`, `.env.example`, `.gitignore` |
| Build | root `pom.xml` (reactor aggregator), `mvnw`, `mvnw.cmd`, `.mvn/wrapper/maven-wrapper.properties` |
| Frontend pages | `frontend/src/api/treasuryApi.ts`, `frontend/src/pages/treasury/{TreasuryPage,TransferHistoryPage,TransferStatusBadge}.tsx`, tests in `frontend/src/__tests__/{pages,api}/` |
| Docs | `docs/rfc/RFC-001-sections-treasury.md` (§3 context #4, §4 saga, §7 trade-offs, saga PlantUML), `docs/test-cases/treasury-service-test-cases.md`, this file |

## 2. Versions (everyone)

Spring Cloud has no release train for Boot 4.1.x (2025.1.x → Boot 4.0.x, 2026.0.x → Boot 4.2.x),
so, as the plan says, **pin Spring Boot 4.0.10 + Spring Cloud 2025.1.5**, Java 17. Boot 4 changes
that bit me and will bite you:

* Starters: `spring-boot-starter-webmvc` (not `-web`). Test slices need `spring-boot-starter-webmvc-test`
  and `spring-boot-starter-data-jpa-test`.
* Packages moved: `org.springframework.boot.webmvc.test.autoconfigure.{WebMvcTest,AutoConfigureMockMvc}`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`. `@MockBean` is gone, so use
  `org.springframework.test.context.bean.override.mockito.MockitoBean`.
* Jackson 3 is the default: `tools.jackson.databind.ObjectMapper`. The annotations are still
  `com.fasterxml.jackson.annotation.*`.
* `@WebMvcTest` only builds `HttpSecurity` if your config class has `@EnableWebSecurity`.
* A `@Component` JWT `OncePerRequestFilter` is **also** auto-registered as a servlet filter, runs
  before Spring Security and gets wiped. Disable that with a `FilterRegistrationBean`
  (`setEnabled(false)`), as in `treasury-service/.../security/SecurityConfig.java`.
* Put `@EnableFeignClients` on a separate `@Configuration` class, not on the application class,
  or every `@WebMvcTest` tries to build your Feign clients.
* Feign + Resilience4J: set `spring.cloud.circuitbreaker.resilience4j.disable-thread-pool: true`,
  otherwise the Feign call runs on another thread and the interceptor cannot see the caller's JWT.

## 3. Docker / Compose rules for every module (everyone)

The Dockerfiles build from the **repo root** with `mvn -pl <module> -am package`, so:

1. **Add your module to `<modules>` in the root `pom.xml`** once its `pom.xml` has content (they
   are commented out now so the reactor builds). `orgwise-common` must be listed first.
2. Include `spring-boot-starter-actuator` and expose `health`. The image healthcheck calls
   `GET /actuator/health` on your port and Compose waits for `"status":"UP"`.
3. Read these environment variables (Compose sets them; local defaults are up to you):

| Variable | Value in compose | Used for |
|---|---|---|
| `SERVER_PORT` | your port (8081 – 8085, 8080, 8761, 8888) | set by the Dockerfile |
| `CONFIG_SERVER_URL` / `SPRING_CONFIG_IMPORT` | `http://config-server:8888` / `optional:configserver:…` | Config client |
| `EUREKA_URL` / `EUREKA_CLIENT_SERVICEURL_DEFAULTZONE` | `http://discovery-server:8761/eureka/` | Eureka client |
| `DB_HOST`, `DB_PORT` | `mysql`, `3306` | JDBC URL |
| `<SVC>_DB_USER`, `<SVC>_DB_PASSWORD` | e.g. `EXPENSE_DB_USER` | your own schema's user |
| `JWT_SECRET` | from `.env` | shared HS256 key |

Schemas: `orgwise_identity`, `orgwise_event`, `orgwise_expense`, `orgwise_treasury`,
`orgwise_reporting`. Each user can only touch its own schema (verified: `treasury_user` gets
`ERROR 1044 … to database 'orgwise_identity'`). `ddl-auto: update` creates the tables.

Run it: `cp .env.example .env && docker compose up --build`. SPA on `:3000`, gateway on `:8080`,
Eureka on `:8761`, MySQL Workbench on `:3307`. Domain services are deliberately **not** published on
host ports, so `/internal/**` really is only reachable inside the Docker network.

## 4. Meg: expense-service internal API that treasury calls

`docs/internal-api-contracts.md` was empty, so treasury is coded against the contract below.
Paths are relative to the service, and responses are **raw DTOs (no `ApiResponseDTO` envelope)**.

| Call | Request | Response / semantics |
|---|---|---|
| `GET /api/v1/internal/expenses/{id}` | | `ExpenseSnapshot`, 404 if unknown |
| `GET /api/v1/internal/expenses?status=APPROVED` | | `ExpenseSnapshot[]` |
| `POST /api/v1/internal/expenses/{id}/disburse` | `DisbursementCommand` | Sets status `DISBURSED` and stores `transferReference`. **Idempotent:** the same reference again returns 200 with no change. Returns 409 if not `APPROVED` or already disbursed by a *different* reference |
| `POST /api/v1/internal/expenses/{id}/revert-disburse` | `DisbursementCommand` | If `DISBURSED` by **this** reference, back to `APPROVED`. Otherwise a **no-op 200** (step 2 never applied). Idempotent |

```jsonc
// ExpenseSnapshot (extra fields are ignored by treasury)
{ "id": 5, "title": "Venue deposit", "amount": 2500.00, "status": "APPROVED",
  "requesterId": 7, "requesterName": "Ana Reyes", "eventId": 3, "transferReference": null }
// DisbursementCommand
{ "transferReference": "uuid", "amount": 2500.00, "disbursedById": 2, "disbursedByUsername": "finance" }
```

* Status strings: `APPROVED`, `DISBURSED`.
* Error bodies should carry a `"message"`, which treasury passes through to the UI.
* The internal endpoints must accept a JWT with role **`SERVICE`** (`sub=treasury-service`, no
  `officerId`). The reconciler uses one because it has no user token to forward. User calls forward
  the finance head's own token.

## 5. Pat: identity, `orgwise-common`, Config Server, parent POM

* **JWT:** HS256, key = `Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwt.secret))`, so `JWT_SECRET`
  is **Base64** (≥ 32 bytes decoded). Claims: `sub` (username), `role` (with or without `ROLE_`;
  treasury normalises it), `officerId` (number). Role names treasury uses: `ADMIN`, `PRESIDENT`,
  `FINANCE_HEAD`, `OPERATIONS_HEAD`, `ASSOCIATE` (and `SERVICE`). If identity's enum differs,
  change `treasury-service/.../security/Roles.java` and the `@PreAuthorize` strings.
* **`orgwise-common`:** when it is published, swap these treasury-local classes for the shared ones
  (same behaviour, so it is a delete-and-re-import):

| treasury-local | orgwise-common |
|---|---|
| `security/JwtTokenService` | `JwtUtil` (keep `generateServiceToken()`, or add it to common) |
| `security/JwtAuthenticationFilter`, `JwtPrincipal` | `JwtFilter`, `JwtPrincipal` |
| `security/RestAuthenticationEntryPoint`, `RestAccessDeniedHandler` | `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler` |
| `client/FeignAuthForwardingInterceptor` | `FeignAuthInterceptor` |
| `dto/ApiResponseDTO`, `exception/ErrorResponseDTO`, `exception/ServiceUnavailableException` | same names in common |

* **Config Server:** Compose runs it with `SPRING_PROFILES_ACTIVE=native` and
  `SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=file:/config-repo` (mounted read-only). It
  needs actuator for the healthcheck. Please put `jwt.secret: ${JWT_SECRET}`, the Eureka URL and
  the Resilience4J `configs.default` block in `config-repo/application.yml`. Treasury ships the
  same values as local defaults, so it also starts without the Config Server.
* **Parent POM:** the root `pom.xml` is a plain aggregator. If you turn it into a real parent with
  `dependencyManagement`, point `treasury-service/pom.xml`'s `<parent>` at it.

## 6. Aki: gateway, reporting client, frontend shell

* **Gateway routes:** `/api/v1/treasury/**` and `/api/v1/transfers/**` → `lb://treasury-service`
  (no path rewrite; treasury serves the full `/api/v1/...` path). Deny `/api/v1/internal/**`.
  Swagger: `lb://treasury-service/v3/api-docs`. Compose passes `CORS_ALLOWED_ORIGINS`
  (`http://localhost:3000,http://localhost:5173`).
* **reporting → treasury:** `GET /api/v1/internal/treasury/aggregate` returns raw JSON:

```json
{ "balance": 97500.00, "currency": "PHP", "totalDisbursed": 2500.00,
  "completedTransfers": 1, "pendingTransfers": 0, "failedTransfers": 0,
  "byEvent": [ { "eventId": 3, "totalDisbursed": 2500.00, "transferCount": 1 } ],
  "generatedAt": "2026-11-24T08:00:00Z" }
```

* **Frontend shell:** my pages import only `default` from `src/api/client.ts`: an Axios instance
  whose `baseURL` already ends in `/api/v1` (`import.meta.env.VITE_API_BASE_URL`, which
  `frontend/Dockerfile` sets to `/api/v1`; nginx proxies `/api` to the gateway) and which attaches
  the JWT. Please add:
  * route `/treasury` → `pages/treasury/TreasuryPage` (roles `FINANCE_HEAD`)
  * route `/transfers/me` → `pages/treasury/TransferHistoryPage` (any signed-in role)
  * nav links for both; `QueryClientProvider` in `main.tsx`
  * dependencies: `@tanstack/react-query@5`, `axios@1`; dev: `@testing-library/user-event@14`,
    `@testing-library/jest-dom@6`, `jsdom`
  * The tests call `cleanup` explicitly, so they work with or without Vitest `globals`.
* **Postman:** treasury requests for your collection: `GET /api/v1/treasury`,
  `GET /api/v1/treasury/disbursable`, `POST /api/v1/transfers {"expenseId": n}`,
  `GET /api/v1/transfers?status=&page=&size=`, `GET /api/v1/transfers/me`,
  `GET /api/v1/transfers/{reference}`.

## 7. Rain: RFC and diagrams

`docs/rfc/RFC-001-sections-treasury.md` is ready to paste into RFC-001 (§3 context #4, §4 saga,
§7 trade-offs). It includes the saga sequence diagram as PlantUML, which you can copy into
`docs/architecture/disbursement-saga-sequence.puml`; I left that file untouched since you own the
diagrams. Eureka also needs actuator for the Compose healthcheck.

## 8. How it was verified

* `./mvnw verify` at the root: 182 tests, 0 failures, JaCoCo 99.3% lines / 93.2% branches.
* Docker: built the treasury image from its Dockerfile, ran `mysql` + `treasury-service` with
  Compose, and called it with a JWT signed with the `.env` secret: 200 balance for
  `FINANCE_HEAD`, 401 without a token, 403 for `ASSOCIATE`, and **503 on disburse while
  expense-service is down, with the balance unchanged**. The init script created all 5 users, and
  each user is confined to its schema.
* nginx config passed `nginx -t`, the SPA fallback returned 200, and `/api` was proxied.
* Frontend: 37 Vitest tests and `tsc --strict` pass in a scratch Vite project (the shell's
  `package.json` is still empty, so they have not run inside `frontend/` yet).
* **Not verified yet:** the full 10-container `docker compose up` (the other modules are empty), and
  the end-to-end flow against the real expense-service. That is the Day-11 run-through.
