# MCO2 Plan: Splitting the Orgwise monolith into a full-stack microservices app

## Context
MCO2 asks the group to turn one MCO1 monolith into **5 Spring Boot microservices (one per member)**, plus a **Eureka Server, an API Gateway and a Spring Cloud Config Server**. The services must talk to each other through **OpenFeign** (no shared databases), protect those calls with **Resilience4J circuit breakers that have fallbacks**, keep the MCO1 standards (DTOs, JPA, validation, a global exception handler, JWT, tests), and ship with Javadoc, architecture diagrams, system docs and **3+ documented test cases for every method**. LO6 also calls for a **full-stack** capstone, but Orgwise is backend-only today, so this plan adds a **web frontend**. The deadline is **Dec 1, 2026**, which is 12 days away. Commit history has to show work from every member.

The design reasoning below is written as an **RFC** (`docs/rfc/RFC-001-orgwise-decomposition.md`), so the panel can see *why* each split was made, not only *what* was built. That document is the main evidence for the 20-point "Designing Microservices Architecture" criterion and the 15-point "Consistency of Design and Code" criterion.

---

## Tech stack

| Layer | Choice | Notes |
|---|---|---|
| Language/runtime | **Java 17** (the monolith's current version; 21 is also allowed) | Same as MCO1 |
| Framework | **Spring Boot 4.1.x** (the monolith runs 4.1.2) | Check on start.spring.io which Spring Cloud train supports it. If none does, pin all modules to the latest Boot 4.0.x + Cloud 2025.1.x pair. |
| Microservice infra | **Spring Cloud**: Netflix Eureka Server/Client, Gateway (`spring-cloud-starter-gateway-server-webflux`), Config Server (native profile), OpenFeign, Circuit Breaker **Resilience4J** | Required by the spec |
| Persistence | Spring Data JPA + Hibernate, **MySQL 8** (one schema and DB user per service), **H2** for tests | Same as MCO1 |
| Security | Spring Security, **JJWT 0.11.5** (already in use), BCrypt | Identity issues the tokens; every service validates them |
| API docs | springdoc-openapi per service, combined in the gateway's Swagger UI | Reuses `OpenApiConfiguration` |
| Build/test | Maven multi-module + wrapper, JUnit 5, Mockito, MockMvc, Spring Security Test, **WireMock** (stubs for Feign tests), JaCoCo ≥80% | |
| Frontend | **React 18 + TypeScript + Vite**, React Router, TanStack Query, Axios (JWT interceptor), Tailwind CSS, **Vitest + React Testing Library** | Calls **only** the gateway at `:8080` |
| Containers | **Docker** (multi-stage Dockerfile per module), **Docker Compose**, frontend served by **nginx** | |
| Tooling | Postman, draw.io/PlantUML, MySQL Workbench, GitHub (PR per feature) | |

---

## RFC-001: Decomposing Orgwise (summary of the document to write)

**Status:** Accepted · **Authors:** Pat, Rain, Meg, Lui, Aki · **Location:** `docs/rfc/RFC-001-orgwise-decomposition.md`

### 1. Problem: why break up the monolith
- **One deployable, one database, one failure domain.** A bug or load spike in report aggregation (`AnalyticsService` runs 10+ `SUM` queries) shares a JVM and connection pool with money movement (`TransferService.disburse`).
- **Tangled ownership.** `ExpenseRepository` is used by 5 classes (`ExpenseService`, `EventService`, `TransferService`, `AnalyticsService`, `AccountSecurity`), so any change to the expense schema affects the whole app. Nobody owns the table.
- **Different change rates and scaling needs.** Officer/role management rarely changes. The approval workflow changes with org policy (thresholds, stages). Reporting is read-heavy. Treasury needs the strictest consistency. A single deployable forces all of them onto the same release and scaling cycle.
- **Security blast radius.** Treasury balance and ledger rows live in the same schema and process as public registration.

### 2. Goals and non-goals
- **Goals:** keep every MCO1 business rule (approval stages, budget reservation, no double disbursement, role rules); independent deployment; each service owns its data; graceful degradation when a dependency is down; a single entry point.
- **Non-goals:** event sourcing or message brokers (Kafka), multi-org tenancy, real bank integration, refresh tokens.

### 3. How the boundaries were picked (DDD)
Three tests decided the boundaries: **(a)** aggregate ownership: which entity's invariants does this logic protect; **(b)** ubiquitous language: each word means one thing inside its context (to Identity an "Officer" is an account, to Expenses it is a "requester/approver"); **(c)** consistency needs: which data must change in one ACID transaction and which can be eventually consistent.

| # | Bounded context → service | Aggregate it owns | Why it is its own service | Why it isn't merged with a neighbour |
|---|---|---|---|---|
| 1 | **Identity & Access** → `identity-service` | `Officer` (+`Role`) | Generic supporting subdomain: login, registration, roles. Security-sensitive (passwords) and the only issuer of JWTs. | Every other context only needs *who* and *what role*, which the JWT carries. Merging it would hand password hashes to unrelated services. |
| 2 | **Events & Budget Allocation** → `event-service` | `Event` (plannedBudget) | Owned by the PRESIDENT and planned ahead of spending. Its invariant is "the planned budget is the cap". | Events exist without expenses, and their lifecycle (plan, edit, delete) differs from the request workflow. Merging with expenses would rebuild the shared-table problem. |
| 3 | **Expense Approval Workflow** → `expense-service` | `ExpenseRequest` (stages, statuses, reservation) | **Core domain**: the multi-stage finance → ops → president state machine, threshold classification and budget reservation. It changes most often. | It must not move money (separation of duties: approving ≠ paying). Keeping it apart from treasury mirrors the real "approver vs. disburser" control. |
| 4 | **Treasury & Ledger** → `treasury-service` | `TreasuryAccount`, `TransferRecord` | Highest-integrity context: balance, pessimistic locks, an append-only ledger. It needs its own security and audit boundary. | Its consistency rules (lock, debit, record) stay inside one local ACID transaction. Splitting it further would spread a money transaction across services. |
| 5 | **Reporting & Analytics** → `reporting-service` | `ReportSnapshot` (read model) | A read-only CQRS-style projection over three contexts. Its heavy aggregation queries are kept away from transactional workloads. | It owns no business invariants, so it can serve stale data when a source is down. That makes no sense for any write-side service. |

**Splits that were considered and rejected:**
- A separate "Approval" service apart from "Expense": the stages are the expense's own state, and splitting them would need a distributed transaction on every decision.
- A separate "Officer profile" service apart from Auth: too thin, same data.
- A "Notification" service: no notifications exist in MCO1 (scope creep).

### 4. Handling consistency across services
- `@ManyToOne` across contexts becomes **ID references plus snapshot fields** (`requesterId`, `requesterName`, `eventId`).
- **Event budget reservation:** expense-service owns the reservations, gets `plannedBudget` from event-service through Feign, and serializes concurrent drafts with a local `EventBudgetLock(eventId)` row (`PESSIMISTIC_WRITE`). This replaces the cross-table lock on the `Event` row.
- **Disbursement saga** (replaces the single DB transaction):
  1. Treasury debits the balance and writes a `TransferRecord` with status `PENDING`, in one local transaction.
  2. A Feign call marks the expense `DISBURSED`. The call is idempotent, keyed by the transfer reference.
  3. On success the transfer becomes `COMPLETED`. On failure, a **compensating transaction** refunds the balance and marks it `FAILED`.

### 5. Resilience policy
- **Reads degrade:** reporting serves the last snapshot with `stale: true`.
- **Writes that move money or check budgets fail closed:** the fallback returns `503`, nothing changes, and no money leaves without confirmation.
- Resilience4J settings (sliding window, failure rate, timeouts) live centrally in the Config Server.

### 6. Security
- Identity signs JWTs that carry `sub`, `role` and `officerId`.
- The gateway pre-validates tokens and blocks `/internal/**`. Every service re-validates them (defense in depth).
- A Feign interceptor forwards the caller's token. Role checks stay at the controller and the service layer, as in MCO1.

### 7. Trade-offs we accept
- More moving parts (8 backend processes plus the frontend).
- Eventual consistency in reports.
- The saga is harder to reason about than one ACID transaction.
- Slower local startup.

These are explained in the defense as the price of independent deployment and fault isolation.

---

## Target architecture

```
React SPA (nginx :5173/:3000) ──► API Gateway :8080 ──(lb:// via Eureka)──► domain services
                                    │ JWT pre-check, CORS, blocks /internal/**
Config Server :8888 ◄── all services     Eureka Server :8761 ◄── all services + gateway

identity-service  :8081  orgwise_identity   /auth/**, /officers/**, /admin/officers/**
event-service     :8082  orgwise_event      /events/**
expense-service   :8083  orgwise_expense    /expenses/**
treasury-service  :8084  orgwise_treasury   /treasury/**, /transfers/**
reporting-service :8085  orgwise_reporting  /reports/**
```

Feign call map, with the circuit-breaker policy on each call:
- expense → event: get budget (fail closed)
- expense → identity: requester info (fallback: name from the JWT)
- event → expense: reserved/exists (fail closed)
- treasury → expense: get, disburse, revert (fail closed + compensate)
- reporting → expense, event, treasury: aggregates (fallback: stale snapshot)

**Repo layout:** one Maven multi-module repo containing `config-server/`, `discovery-server/`, `api-gateway/`, `orgwise-common/`, the five `*-service/` modules, `frontend/`, `config-repo/`, `docker-compose.yml`, `docs/` (rfc, architecture, test cases) and `postman/`. The monolith is kept under a `mco1` git tag for reference.

**Shared `orgwise-common` module** reuses these from the monolith: `JwtUtil`, `JwtFilter`, `CustomAuthenticationEntryPoint`, `CustomAccessDeniedHandler`, `ApiResponseDTO`, `ErrorResponseDTO`, the `GlobalExceptionHandler` base, plus a new Feign auth interceptor.

---

## Five member assignments
Each member owns **one domain service**, **the frontend pages for that service**, **one shared piece**, their **RFC section**, and their **Javadoc and 3+ tests per method** (backend JUnit and frontend Vitest).

| Member | Service | Shared piece | Frontend pages |
|---|---|---|---|
| **Pat** | identity-service | Config Server + `orgwise-common` | Login/Register, Officer Management |
| **Rain** | event-service | Eureka Server + RFC/diagrams lead | Events list + President event form |
| **Meg** | expense-service | Internal API contract | My Expenses, Approval Queue |
| **Lui** | treasury-service | Docker/Compose/MySQL schemas | Treasury, Transfer History |
| **Aki** | reporting-service | API Gateway + frontend shell + README | Reports dashboard |

### Pat: Identity Service + Config Server + `orgwise-common`
- **Backend:**
  - `Officer`, `AuthService`, `OfficerService`, `AuthController`, `OfficerController`, `CustomUserDetailsService`, and the admin bootstrap.
  - Adds the `role`/`officerId` JWT claims.
  - Internal: `GET /internal/officers/{username}` and `GET /internal/officers/by-id/{id}`.
- **Infra:** Config Server + `config-repo/` (shared JWT secret, Eureka URL, Resilience4J defaults), and `orgwise-common` (**due Day 2**, everyone depends on it).
- **Frontend:** Login and Register pages, the admin Officer Management page (list, change role, deactivate).
- **RFC:** §6 Security; context #1 rationale.

### Rain: Event Service + Eureka Server + RFC/architecture docs lead
- **Backend:** `Event`, `EventService`, `EventController`. Internal: `GET /internal/events/{id}`.
- Feign → expense: reserved sum and exists-by-event, both **fail closed**.
- **Infra:** Eureka Server (`discovery-server`).
- **Frontend:** Events list (paginated), plus the President's create/edit/delete event form with budget validation feedback.
- **Docs lead:**
  - edits RFC-001 and writes §1–3
  - owns the draw.io/PlantUML diagrams: context map, service responsibilities, gateway request flow, Feign + circuit-breaker map, saga sequence diagram
- **RFC:** context #2 rationale.

### Meg: Expense (Approval Workflow) Service + internal API contract
This is the largest service.
- **Backend:**
  - `ExpenseRequest` and its enums, `ExpenseService` (drafts, submit, staged `decide`), the queue `search` (moved from `AnalyticsService`), `AccountSecurity`.
  - The ID/snapshot refactor and the `EventBudgetLock` row.
  - Feign → event (**fail closed**) and → identity (fallback to JWT data).
  - Internal: get-by-id, reserved, exists, idempotent `disburse` and `revert-disburse`, aggregates.
- **Contract:** writes `docs/internal-api-contracts.md` on **Day 1**. Everyone codes their Feign clients against it.
- **Frontend:**
  - Associate: My Expenses (create, edit, delete draft, submit; filter by status)
  - Reviewers: Approval Queue (filters, approve/reject with comment, decision history)
- **RFC:** §4 consistency (budget reservation); context #3 rationale.

### Lui: Treasury & Transfer Service + Docker/DB
- **Backend:**
  - `TreasuryAccount`, `TransferRecord`, `TransferService`, `TransferController`, and the treasury bootstrap.
  - The **disbursement saga** with compensation; `TransferRollbackIntegrationTest` is rewritten as a compensation test.
- **Infra:**
  - a multi-stage Dockerfile per module, plus the nginx image for the frontend
  - `docker-compose.yml`: MySQL with an init script for 5 schemas/users, startup ordering, healthchecks
  - `.env.example`
- **Frontend:** Finance Head Treasury page (balance, "Disburse" on approved requests, error and 503 messages), and the Associate Transfer History page.
- **RFC:** §4 saga, §7 trade-offs; context #4 rationale.

### Aki: Reporting Service + API Gateway + frontend shell
- **Backend:**
  - `/reports/summary` and `/reports/events/{id}`, built from Feign aggregates
  - a `ReportSnapshot` table, with a **stale-snapshot fallback** (`stale: true`)
- **Infra:** Spring Cloud Gateway:
  - routes for `/api/v1/**`
  - global JWT filter (login/register stay public)
  - deny `/internal/**`
  - CORS for the SPA origin
  - aggregated Swagger UI
  - Postman collection pointed at `:8080`
- **Frontend shell (Day 1–3, everyone builds on it):**
  - Vite/React/TS scaffold, Tailwind
  - Axios client with JWT interceptor and 401 → login redirect
  - auth context, role-based routes and nav
  - shared table/form/toast components
- **Frontend pages:** Finance Reports dashboard (summary totals, per-event budget bars, a "data may be stale" banner).
- **Docs:** root `README.md` (service ownership table, gateway endpoint list, how to run with compose and locally).
- **RFC:** §5 resilience; context #5 rationale.

---

## Timeline (Nov 19 → Dec 1)
| Days | Milestone |
|---|---|
| 1–2 (Nov 19–20) | Parent POM and skeletons; common and config server (Pat); Eureka (Rain); API contract (Meg); compose and schemas (Lui); gateway and frontend shell (Aki); RFC draft with boundaries agreed. |
| 3–6 (Nov 21–24) | Each service ported with its own entities and endpoints, MCO1 tests passing on H2; frontend pages built against the gateway. |
| 7–8 (Nov 25–26) | Feign clients, circuit breakers and fallbacks, saga; end-to-end flows through the UI. |
| 9–10 (Nov 27–28) | Fill the 3-cases-per-method gaps (backend and frontend), Javadoc/TSDoc pass, `docs/test_cases.md` per service, diagrams finalized to match the code. |
| 11 (Nov 29) | Full `docker compose up` run-through via the UI and Postman; code freeze. |
| 12 (Nov 30) | Defense rehearsal: each member presents their RFC section, service, pages and one failure demo. Submit. |

**Git:** each member works on a `feat/<service>` branch and merges through PRs that one other member reviews. Everyone commits under their own name.

---

## Verification
1. `./mvnw verify` at the root: every module is green and JaCoCo is ≥80%. `npm test` in `frontend/` passes.
2. `docker compose up`: the Eureka dashboard shows all 5 services and the gateway UP, and the SPA loads.
3. Run the MCO1 flow **through the UI**:
   - admin login → register users → assign roles
   - president creates an event
   - associate drafts and submits a major expense
   - finance, ops and president approve
   - finance disburses → the balance drops, transfer history shows the record, and reports show the totals
   - Repeat the same flow in Postman via `:8080`.
4. Resilience demos:
   - Stop expense-service: the reports page shows the stale banner, and disburse/event-delete show 503 with no balance change.
   - Stop event-service: draft creation returns 503.
   - Force the internal disburse call to fail: the balance is restored and the transfer is `FAILED`.
5. Calling `/api/v1/internal/**` from outside is blocked, and a missing JWT returns 401.
6. Each MySQL schema holds only its own service's tables.
7. RFC-001's diagrams and endpoint lists match the deployed routes (cross-check against the gateway config and Swagger).

---

## After approval: deliverable
Once this plan is approved, turn it into a formatted PDF, `docs/MCO2-Microservices-Plan.pdf`, in the repo. Use the `anthropic-skills:pdf` skill: convert this markdown into a styled PDF with the tables, the RFC section and the per-member assignments intact. Check the rendered pages before handing it over.
