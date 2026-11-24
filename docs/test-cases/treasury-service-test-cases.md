# Treasury Service: Test Cases

**Owner:** Lui · **Module:** `treasury-service` + treasury frontend pages · **Last run:** 2026-11-24

| Suite | Tests | Result | Coverage (JaCoCo) |
|---|---|---|---|
| Backend (JUnit 5, Mockito, MockMvc, `@DataJpaTest`/H2, WireMock) | **182** | all pass | **99% lines**, 93% branches (gate: 80% lines) |
| Frontend (Vitest + React Testing Library) | **37** | all pass | n/a |

## How to run

```bash
./mvnw -pl treasury-service verify          # tests + JaCoCo report and 80% gate
open treasury-service/target/site/jacoco/index.html
cd frontend && npm test -- src/__tests__/pages/TreasuryPage.test.tsx src/__tests__/pages/TransferHistoryPage.test.tsx src/__tests__/api/treasuryApi.test.ts
```

Test IDs: `TA` TreasuryAccount, `TR` TransferRecord, `TRR`/`TFR` repositories, `TS` TransferService,
`DS` DisbursementSaga, `RC` reconciler, `TB` bootstrap, `EC`/`CB`/`FB`/`FI` Feign client, circuit breaker,
fallback and interceptor, `JT`/`JF`/`SC`/`SH` security, `TC`/`TF`/`IT` controllers, `GE` exception handler,
`SG` saga end-to-end, `FE-*` frontend. Every ID below is the `@DisplayName` / `it()` name of a real test,
so this document can be regenerated from the code.

## Coverage matrix: every method has 3 or more test cases

Direct tests come first. Tests that exercise the method through a higher layer are in *italics*.

| Class | Method | Test cases |
|---|---|---|
| `TreasuryAccount` | constructor | TA-01, TA-02, TA-03, TA-11 |
| | `debit` | TA-04, TA-05, TA-06, TA-07, TA-09 |
| | `credit` | TA-08, TA-09, TA-10, *TS-10* |
| `TransferRecord` | `builder().build()` | TR-01 – TR-05 |
| | `markCompleted` | TR-06, TR-07, TR-08, TR-14 |
| | `markFailed` | TR-09, TR-10, TR-11 |
| | `confirmRevert` | TR-12, TR-13, TR-14 |
| | `equals` / `hashCode` / `touch` | TR-15, TR-17, TFR-01, TFR-03 |
| `TransferStatus` | `isActive` | TR-16, TFR-02, *TS-03* |
| `TreasuryRepository` | `findFirstByOrderByIdAsc` | TRR-01, TRR-02, *TS-16*, *SC-03* |
| | `findByIdForUpdate` | TRR-03, TRR-04, TRR-05, *SG-01* |
| `TransferRepository` | `findByReference` / `findByReferenceForUpdate` | TFR-01, *TS-07*, *TS-22*, *SG-02* |
| | `existsByExpenseIdAndStatusIn` | TFR-02, *TS-03*, *SG-06* |
| | `findByStatus` / `findByRecipientId` | TFR-03, *TS-20*, *TS-21*, *TF-08* |
| | `findByStatusAndCreatedAtBeforeOrderByCreatedAtAsc` | TFR-04, *TS-31*, *TS-32* |
| | `findByStatusAndRevertPendingTrue` | TFR-05, *TS-33*, *SG-03* |
| | `findExpenseIdsWithStatusIn` | TFR-06, *TS-25*, *TS-26* |
| | `sumAmountByStatus` / `countByStatus` / `sumCompletedByEvent` | TFR-07, TFR-08, *TS-28*, *TS-29* |
| `TransferService` | `reserveFunds` (saga step 1) | TS-01 – TS-06, *SG-01*, *SG-07* |
| | `completeTransfer` (step 3a) | TS-07, TS-08, TS-09, *RC-01* |
| | `compensateTransfer` (step 3b) | TS-10, TS-11, TS-12, *SG-02*, *SG-03*, *SG-04* |
| | `confirmRevert` | TS-13, TS-14, TS-15, *RC-07* |
| | `getTreasuryAccount` | TS-16, TS-17, TS-18, *TC-01* |
| | `getTransfers` | TS-19, TS-20, *TF-05*, *TF-06* |
| | `getTransfersForRecipient` | TS-21, *TF-08*, *TF-09* |
| | `getTransfer` | TS-22, TS-23, TS-24, *TF-11* – *TF-13* |
| | `getDisbursableExpenses` | TS-25, TS-26, TS-27, *TC-04* – *TC-06* |
| | `getAggregate` | TS-28, TS-29, TS-30, *IT-01* |
| | `findStalePendingTransfers` | TS-31, TS-32, *RC-01* |
| | `findTransfersAwaitingRevert` | TS-33, *RC-07*, *RC-08* |
| `DisbursementSaga` | `disburse` | DS-01 – DS-10, SG-01 – SG-07 |
| `PendingTransferReconciler` | `reconcile` | RC-01 – RC-08, RC-11 |
| | `scheduledRun` | RC-09, RC-10, RC-11 |
| `TreasuryBootstrapConfiguration` | `bootstrap` | TB-01, TB-02, TB-03 |
| | `treasuryBootstrap` (runner bean) | TB-04, *SC-03*, *SG-01* |
| `ExpenseClient` (Feign) | `getExpense` | EC-01, EC-02, EC-03, CB-02 |
| | `getExpensesByStatus` | EC-04, EC-05, CB-01 |
| | `markDisbursed` | EC-06, EC-07, *SG-01*, *SG-04* |
| | `revertDisbursement` | EC-08, EC-09, *SG-02*, *SG-03* |
| `ExpenseClientFallbackFactory` | `create` / `translate` | FB-01 – FB-08, CB-01, CB-02 |
| `FeignAuthForwardingInterceptor` | `apply` | FI-01 – FI-04, *SG-01* |
| `JwtTokenService` | `parse` | JT-01 – JT-04 |
| | `generateServiceToken` | JT-05, JT-06, JT-07, FI-02, IT-02 |
| `JwtPrincipal` | `hasAnyRole` | JT-08, *TS-22*, *TS-23*, *DS-09* |
| `JwtAuthenticationFilter` | `doFilterInternal` | JF-01 – JF-04, *SC-02* |
| `CurrentOfficer` | `get` | JF-05, *TF-01*, *TF-08* |
| `SecurityConfig` | `securityFilterChain` | SC-01 – SC-04 |
| | `noUsers` / `jwtFilterRegistration` | SC-05, *SC-03*, *TC-01* |
| `RestAuthenticationEntryPoint` | `commence` | SH-01, SH-03, *SC-02*, *TC-03* |
| `RestAccessDeniedHandler` | `handle` | SH-02, SH-03, *SC-03* |
| `TreasuryController` | `getTreasury` | TC-01, TC-02, TC-03 |
| | `getDisbursable` | TC-04, TC-05, TC-06 |
| `TransferController` | `disburse` | TF-01 – TF-04, SG-01 – SG-07 |
| | `getTransfers` | TF-05, TF-06, TF-07 |
| | `getMyTransfers` | TF-08, TF-09, TF-10 |
| | `getTransfer` | TF-11, TF-12, TF-13 |
| `InternalTreasuryController` | `getAggregate` | IT-01, IT-02, IT-03 |
| `GlobalExceptionHandler` | 404 / 409 / 422 / 503 / saga-failed | GE-01 – GE-05, *TF-04*, *TC-06*, *SG-02*, *SG-05* – *SG-07* |
| | 400 (validation, bad JSON, bad parameter) | GE-06, GE-07, *TF-02*, *TF-06*, *TF-09* |
| | 401 / 403 / 500 | GE-08, GE-09, *TC-02*, *TF-12* |
| Frontend `treasuryApi` | request helpers | FE-API-01 – FE-API-06 |
| | `describeTreasuryError` | FE-API-07, FE-API-08, FE-API-09 |
| | `formatMoney` | FE-API-10, FE-API-11, FE-API-12 |
| Frontend `TreasuryPage` | balance / approved list / disburse / ledger | FE-TR-01 – FE-TR-15 |
| Frontend `Pager` | render and paging | FE-PG-01, FE-PG-02, FE-PG-03 |
| Frontend `TransferHistoryPage` | list / totals / states / paging | FE-TH-01 – FE-TH-07 |

## Saga failure scenarios (resilience demo)

| ID | Failure injected (WireMock) | HTTP to client | Balance | Transfer row |
|---|---|---|---|---|
| SG-01 | none (happy path) | 201 | debited | `COMPLETED` |
| SG-02 | `POST /disburse` returns 500 | 503 + `transferReference` | **restored** | `FAILED`, revert confirmed |
| SG-03 | `/disburse` times out, `/revert-disburse` returns 503 | 503 | **restored** | `FAILED`, `revertPending=true` (reconciler retries) |
| SG-04 | `/disburse` returns 409 | 409 | **restored** | `FAILED` |
| SG-05 | `GET /expenses/{id}` connection reset | 503 "No money was moved" | unchanged | none |
| SG-06 | second disburse of the same expense | 409 | debited once | 1 row |
| SG-07 | amount above balance | 422 | unchanged | none |

## Detailed test cases: backend

### Entities


#### `TransferRecordTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TR-01 | builder() | builds a PENDING transfer with a UUID reference and snapshot fields | `buildsPending` |
| TR-02 | builder() | every transfer gets a different reference | `uniqueReferences` |
| TR-03 | builder() | rejects a missing expenseId | `rejectsMissingExpense` |
| TR-04 | builder() | rejects a zero or negative amount | `rejectsNonPositiveAmount` |
| TR-05 | builder() | rejects a blank disbursing username | `rejectsBlankActor` |
| TR-06 | markCompleted() | moves PENDING to COMPLETED and stamps completedAt | `completes` |
| TR-07 | markCompleted() | is idempotent on a COMPLETED transfer | `idempotent` |
| TR-08 | markCompleted() | refuses to complete a FAILED transfer | `refusesFailed` |
| TR-09 | markFailed() | moves PENDING to FAILED with reason and revert flag | `fails` |
| TR-10 | markFailed() | truncates long reasons and defaults blank ones | `normalisesReason` |
| TR-11 | markFailed() | is idempotent on FAILED and refuses COMPLETED | `idempotentAndGuarded` |
| TR-12 | confirmRevert() | clears revertPending on a FAILED transfer | `clears` |
| TR-13 | confirmRevert() | is harmless when nothing was pending | `harmless` |
| TR-14 | confirmRevert() | refuses PENDING and COMPLETED transfers | `refusesNonFailed` |
| TR-15 | equals()/hashCode() and TransferStatus.isActive() | equality is by reference | `equalityByReference` |
| TR-16 | equals()/hashCode() and TransferStatus.isActive() | PENDING and COMPLETED are active, FAILED is not | `activeStatuses` |
| TR-17 | equals()/hashCode() and TransferStatus.isActive() | touch() refreshes updatedAt | `touchUpdates` |

#### `TreasuryAccountTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TA-01 | constructor | creates an account with the opening balance | `creates` |
| TA-02 | constructor | allows a zero opening balance | `zeroAllowed` |
| TA-03 | constructor | rejects negative balance, blank name or blank currency | `rejectsInvalid` |
| TA-04 | debit() | subtracts the amount | `subtracts` |
| TA-05 | debit() | can debit the exact balance down to zero | `exactBalance` |
| TA-06 | debit() | throws InsufficientFundsException and leaves the balance unchanged | `insufficient` |
| TA-07 | debit() | rejects zero, negative and null amounts | `rejectsNonPositive` |
| TA-08 | credit() | adds the amount | `adds` |
| TA-09 | credit() | debit followed by credit of the same amount restores the balance | `roundTrip` |
| TA-10 | credit() | rejects zero, negative and null amounts | `rejectsNonPositive` |
| TA-11 | credit() | unsaved accounts are only equal to themselves | `equality` |

### Repositories


#### `TransferRepositoryTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TFR-01 | - | findByReference / findByReferenceForUpdate find by reference, empty when unknown | `findByReference` |
| TFR-02 | - | existsByExpenseIdAndStatusIn sees PENDING and COMPLETED but not FAILED | `existsActive` |
| TFR-03 | - | findByStatus and findByRecipientId page results | `paging` |
| TFR-04 | - | stale PENDING query respects the cut-off | `stalePending` |
| TFR-05 | - | findByStatusAndRevertPendingTrue returns only FAILED rows awaiting revert | `awaitingRevert` |
| TFR-06 | - | findExpenseIdsWithStatusIn returns only busy expense IDs | `busyIds` |
| TFR-07 | - | sums, counts and per-event totals only include the requested status | `aggregates` |
| TFR-08 | - | sumAmountByStatus returns zero when nothing matches | `sumZero` |
| TFR-09 | - | the reference column is unique | `uniqueReference` |

#### `TreasuryRepositoryTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TRR-01 | - | findFirstByOrderByIdAsc returns the first account created | `findFirstReturnsOldest` |
| TRR-02 | - | findFirstByOrderByIdAsc is empty before bootstrap | `findFirstEmpty` |
| TRR-03 | - | findByIdForUpdate loads the account under a row lock | `findForUpdate` |
| TRR-04 | - | findByIdForUpdate is empty for an unknown ID | `findForUpdateUnknown` |
| TRR-05 | - | balance changes are persisted with 2-decimal precision | `persistsBalance` |

### Services


#### `DisbursementSagaTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| DS-01 | - | happy path runs reserve -> markDisbursed -> complete in order and returns COMPLETED | `happyPath` |
| DS-02 | - | sends the transfer reference as idempotency key with the actor's identity | `sendsReference` |
| DS-03 | - | outage in step 2 compensates (revert confirmed) and throws 503 | `outageCompensates` |
| DS-04 | - | when the revert also fails, compensation records revertPending=true | `revertFailureFlagged` |
| DS-05 | - | a business rejection in step 2 compensates and maps to 409 | `rejectionIsConflict` |
| DS-06 | - | expense-service down at step 0 fails closed: nothing reserved | `failsClosedBeforeReserve` |
| DS-07 | - | unknown or null expense -> 404, nothing reserved | `unknownExpense` |
| DS-08 | - | insufficient funds in step 1 propagates and never calls step 2 | `insufficientFunds` |
| DS-09 | - | only the finance head may disburse (service-layer role check) | `roleCheck` |
| DS-10 | - | rejects a null or non-positive expense ID | `badId` |

#### `PendingTransferReconcilerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| RC-01 | - | completes a stuck PENDING transfer that expense-service shows as DISBURSED by it | `completesConfirmed` |
| RC-02 | - | compensates a stuck transfer whose expense is still APPROVED | `compensatesUnconfirmed` |
| RC-03 | - | compensates when DISBURSED by a different reference | `compensatesOtherReference` |
| RC-04 | - | compensates when the expense no longer exists | `compensatesMissing` |
| RC-05 | - | leaves the transfer PENDING while expense-service is down | `skipsWhenDown` |
| RC-06 | - | a local failure while resolving is logged and counted as unchanged | `localFailure` |
| RC-07 | - | retries the revert of FAILED transfers and confirms on success | `retriesRevert` |
| RC-08 | - | keeps revertPending when the revert still fails | `revertStillFails` |
| RC-09 | - | scheduledRun does nothing when reconciliation is disabled | `disabled` |
| RC-10 | - | scheduledRun reconciles when enabled | `enabled` |
| RC-11 | - | scheduledRun with nothing stale or awaiting revert changes nothing | `nothingToDo` |

#### `TransferServiceTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TS-01 | reserveFunds() | debits the locked balance and saves a PENDING transfer with snapshot data | `reserves` |
| TS-02 | reserveFunds() | rejects an expense that is not APPROVED without touching the balance | `rejectsNotApproved` |
| TS-03 | reserveFunds() | rejects an expense that already has an active transfer (no double disbursement) | `rejectsDouble` |
| TS-04 | reserveFunds() | propagates InsufficientFundsException and saves nothing | `insufficient` |
| TS-05 | reserveFunds() | rejects a missing or zero amount | `rejectsNoAmount` |
| TS-06 | reserveFunds() | fails with 404 when the treasury was never bootstrapped | `noAccount` |
| TS-07 | completeTransfer() | marks a PENDING transfer COMPLETED | `completes` |
| TS-08 | completeTransfer() | is idempotent for an already COMPLETED transfer | `idempotent` |
| TS-09 | completeTransfer() | refuses a FAILED transfer and 404s on an unknown one | `refuses` |
| TS-10 | compensateTransfer() | refunds the amount and marks the transfer FAILED | `refunds` |
| TS-11 | compensateTransfer() | does not refund twice when the transfer is already FAILED | `noDoubleRefund` |
| TS-12 | compensateTransfer() | refuses to compensate a COMPLETED transfer | `refusesCompleted` |
| TS-13 | confirmRevert() | clears revertPending on a FAILED transfer | `clears` |
| TS-14 | confirmRevert() | 404s on an unknown transfer | `unknown` |
| TS-15 | confirmRevert() | refuses a PENDING transfer | `pending` |
| TS-16 | getTreasuryAccount() | maps the main account to a DTO | `maps` |
| TS-17 | getTreasuryAccount() | reflects the balance after a reservation | `reflectsDebit` |
| TS-18 | getTreasuryAccount() | 404s when no account exists | `missing` |
| TS-19 | getTransfers() / getTransfersForRecipient() | without a status filter lists the whole ledger | `all` |
| TS-20 | getTransfers() / getTransfersForRecipient() | with a status filter uses findByStatus | `filtered` |
| TS-21 | getTransfers() / getTransfersForRecipient() | recipient listing uses the officer ID; a token without officerId is rejected | `recipient` |
| TS-22 | getTransfer() | finance head and president can view any transfer | `privileged` |
| TS-23 | getTransfer() | the recipient can view their own transfer, another associate cannot | `recipientOnly` |
| TS-24 | getTransfer() | 404s on an unknown reference | `unknown` |
| TS-25 | getDisbursableExpenses() | removes approved expenses that already have an active transfer | `filtersBusy` |
| TS-26 | getDisbursableExpenses() | returns an empty list without querying the ledger when nothing is approved | `empty` |
| TS-27 | getDisbursableExpenses() | propagates 503 when expense-service is down (fail closed) | `failsClosed` |
| TS-28 | getAggregate() | combines balance, sums, counts and per-event totals | `combines` |
| TS-29 | getAggregate() | treats a null sum as zero | `nullSum` |
| TS-30 | getAggregate() | 404s when no account exists | `missing` |
| TS-31 | findStalePendingTransfers() / findTransfersAwaitingRevert() | uses now minus the timeout as cut-off | `cutoff` |
| TS-32 | findStalePendingTransfers() / findTransfersAwaitingRevert() | returns what the repository finds | `returnsRows` |
| TS-33 | findStalePendingTransfers() / findTransfersAwaitingRevert() | awaiting-revert queries FAILED rows with revertPending | `awaitingRevert` |

#### `TreasuryBootstrapConfigurationTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TB-01 | - | creates the account with the configured name, balance and currency when none exists | `createsAccount` |
| TB-02 | - | does not touch an existing account (restart keeps the balance) | `skipsExisting` |
| TB-03 | - | falls back to defaults (Main Treasury, 100000.00, PHP) when nothing is configured | `defaults` |
| TB-04 | - | the ApplicationRunner bean runs the bootstrap | `runnerBean` |

### Feign client & resilience


#### `ExpenseClientCircuitBreakerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| CB-01 | - | after repeated 500s the circuit opens and calls stop reaching expense-service | `opensOnOutage` |
| CB-02 | - | 4xx business answers do not open the circuit | `ignoresBusinessErrors` |

#### `ExpenseClientFallbackFactoryTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| FB-01 | - | 404 becomes ResourceNotFoundException with the remote message | `notFound` |
| FB-02 | - | 404 without a body gets a default message | `notFoundNoBody` |
| FB-03 | - | 400/409/422 become BusinessRuleException | `businessErrors` |
| FB-04 | - | 401/403 become AccessDeniedException | `authErrors` |
| FB-05 | - | 5xx becomes ServiceUnavailableException (fail closed) | `serverErrors` |
| FB-06 | - | connection errors, open circuit and unknown causes become ServiceUnavailableException | `outages` |
| FB-07 | - | the fallback never returns data: every method throws | `neverReturnsData` |
| FB-08 | - | translate() keeps escaped quotes in remote messages | `escapedQuotes` |

#### `ExpenseClientWireMockTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| EC-01 | - | getExpense decodes the snapshot and ignores unknown fields | `getExpense` |
| EC-02 | - | getExpense maps 404 and 500 through the fallback | `getExpenseErrors` |
| EC-03 | - | with no HTTP request in scope, calls carry a SERVICE token | `serviceToken` |
| EC-04 | - | getExpensesByStatus sends the status query parameter and decodes a list | `byStatus` |
| EC-05 | - | getExpensesByStatus returns an empty list and maps connection faults to 503 | `byStatusEdge` |
| EC-06 | - | markDisbursed POSTs the command JSON | `markDisbursed` |
| EC-07 | - | markDisbursed maps 409 to BusinessRuleException and a slow response to 503 | `markDisbursedErrors` |
| EC-08 | - | revertDisbursement POSTs to revert-disburse | `revert` |
| EC-09 | - | revertDisbursement maps 404 and 503 | `revertErrors` |

#### `FeignAuthForwardingInterceptorTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| FI-01 | - | forwards the caller's bearer token when serving a request | `forwardsUserToken` |
| FI-02 | - | uses a SERVICE token when there is no request (background job) | `serviceTokenWithoutRequest` |
| FI-03 | - | uses a SERVICE token when the request has no/non-bearer Authorization header | `serviceTokenForNonBearer` |
| FI-04 | - | leaves an explicitly set Authorization header alone | `keepsExplicitHeader` |

### Security


#### `JwtAuthenticationFilterTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| JF-01 | - | a valid token authenticates a JwtPrincipal with ROLE_<role> | `authenticates` |
| JF-02 | - | missing or non-bearer headers leave the request anonymous | `anonymous` |
| JF-03 | - | invalid or expired tokens leave the request anonymous (Spring then answers 401) | `invalid` |
| JF-04 | - | an already authenticated context is not overwritten | `keepsExisting` |
| JF-05 | - | CurrentOfficer returns the principal and throws when anonymous | `currentOfficer` |

#### `JwtTokenServiceTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| JT-01 | parse() | reads subject, role and officerId from a valid token | `valid` |
| JT-02 | parse() | strips a ROLE_ prefix, upper-cases the role and accepts a string officerId | `normalises` |
| JT-03 | parse() | rejects expired, wrongly signed and tampered tokens | `rejectsInvalid` |
| JT-04 | parse() | rejects blank tokens and tokens without a role | `rejectsIncomplete` |
| JT-05 | generateServiceToken() | produces a token this service accepts with role SERVICE | `roundTrip` |
| JT-06 | generateServiceToken() | a service token signed with another secret is rejected | `otherSecretRejected` |
| JT-07 | generateServiceToken() | respects the configured lifetime | `expires` |
| JT-08 | generateServiceToken() | JwtPrincipal.hasAnyRole is case-insensitive and toString is the username | `principal` |

#### `RestSecurityHandlersTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| SH-01 | - | entry point writes a JSON 401 with the request path | `entryPoint` |
| SH-02 | - | access-denied handler writes a JSON 403 | `accessDenied` |
| SH-03 | - | neither handler leaks the exception message or omits the timestamp | `noLeak` |

#### `SecurityConfigTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| SC-01 | - | health and OpenAPI docs are public | `publicEndpoints` |
| SC-02 | - | business and internal endpoints require a JWT and answer 401 as JSON | `protectedEndpoints` |
| SC-03 | - | a valid token reaches the controller; wrong role gets a JSON 403 | `roles` |
| SC-04 | - | the session is stateless and CORS pre-flight is not blocked by auth | `statelessAndPreflight` |
| SC-05 | - | there is no username/password login: the user store is empty | `noUsers` |

### Controllers


#### `InternalTreasuryControllerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| IT-01 | - | returns the raw aggregate (no envelope) for a forwarded user token | `aggregateForUser` |
| IT-02 | - | accepts a SERVICE token | `aggregateForService` |
| IT-03 | - | rejects calls without a token and maps a missing account to 404 | `errors` |

#### `TransferControllerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TF-01 | POST /api/v1/transfers | finance head disburses: 201 with the COMPLETED transfer; the JWT identity is passed on | `created` |
| TF-02 | POST /api/v1/transfers | validation: missing, negative or malformed expenseId is 400 and the saga is not called | `validation` |
| TF-03 | POST /api/v1/transfers | non finance heads get 403 and anonymous callers get 401 | `security` |
| TF-04 | POST /api/v1/transfers | saga errors map to 409 / 422 / 404 / 503 with the transfer reference when compensated | `sagaErrors` |
| TF-05 | GET /api/v1/transfers | lists the ledger for the finance head | `lists` |
| TF-06 | GET /api/v1/transfers | passes the status filter and page request; invalid status is 400 | `filters` |
| TF-07 | GET /api/v1/transfers | associates cannot see the whole ledger | `forbidden` |
| TF-08 | GET /api/v1/transfers/me | returns the caller's transfers using the officerId claim | `mine` |
| TF-09 | GET /api/v1/transfers/me | a token without officerId is 400 | `noOfficerId` |
| TF-10 | GET /api/v1/transfers/me | anonymous is 401 | `anonymous` |
| TF-11 | GET /api/v1/transfers/{reference} | returns the transfer | `found` |
| TF-12 | GET /api/v1/transfers/{reference} | another officer's transfer is 403 | `notMine` |
| TF-13 | GET /api/v1/transfers/{reference} | unknown reference is 404 | `unknown` |

#### `TreasuryControllerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| TC-01 | - | GET /treasury returns the balance to the finance head | `balanceForFinance` |
| TC-02 | - | GET /treasury is allowed for the president, forbidden for an associate | `balanceRoles` |
| TC-03 | - | GET /treasury without a token is 401; missing account is 404 | `balanceErrors` |
| TC-04 | - | GET /treasury/disbursable lists approved expenses for the finance head | `disbursable` |
| TC-05 | - | GET /treasury/disbursable is finance-head only (president gets 403) | `disbursableRole` |
| TC-06 | - | GET /treasury/disbursable returns 503 when expense-service is down | `disbursableUnavailable` |

### Exception handling


#### `GlobalExceptionHandlerTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| GE-01 | - | ResourceNotFoundException -> 404 with message and path | `notFound` |
| GE-02 | - | BusinessRuleException -> 409 | `conflict` |
| GE-03 | - | InsufficientFundsException -> 422 with available/requested details | `insufficient` |
| GE-04 | - | ServiceUnavailableException -> 503 | `unavailable` |
| GE-05 | - | DisbursementFailedException -> its own status plus the transfer reference | `disbursementFailed` |
| GE-06 | - | validation errors -> 400 with one message per field | `validation` |
| GE-07 | - | malformed body, bad parameter type and IllegalArgumentException -> 400 | `badRequests` |
| GE-08 | - | AccessDeniedException -> 403, missing credentials -> 401 | `security` |
| GE-09 | - | unexpected exceptions -> 500 without leaking internals | `unexpected` |

### Integration (saga end-to-end)


#### `DisbursementCompensationIntegrationTest`

| ID | Group / method under test | Scenario and expected result | JUnit method |
|---|---|---|---|
| SG-01 | - | Happy path: balance drops, transfer COMPLETED, expense-service told with the reference and the user's token | `happyPathCompletesTransfer` |
| SG-02 | - | Disburse step returns 500: balance restored, transfer FAILED, 503 returned, revert attempted | `serverErrorOnDisburseIsCompensated` |
| SG-03 | - | Disburse step times out and revert fails: balance restored, FAILED with revertPending | `timeoutAndRevertFailureLeavesRevertPending` |
| SG-04 | - | Expense-service rejects the disburse step with 409: compensated and 409 returned | `conflictOnDisburseIsCompensated` |
| SG-05 | - | Expense-service down before any money moved: 503, no transfer row, balance unchanged | `expenseDownFailsClosed` |
| SG-06 | - | Second disbursement of the same expense is rejected with 409 and does not debit again | `doubleDisbursementRejected` |
| SG-07 | - | Amount above the balance: 422, no transfer row, balance unchanged | `insufficientFunds` |


## Detailed test cases: frontend

#### `TreasuryPage.test.tsx`

| ID | Group | Scenario and expected result |
|---|---|---|
| FE-TR-01 | TreasuryPage: balance | shows the formatted treasury balance |
| FE-TR-02 | TreasuryPage: balance | shows a loading state before the balance arrives |
| FE-TR-03 | TreasuryPage: balance | shows an error when the balance cannot be loaded |
| FE-TR-04 | TreasuryPage: approved expenses | lists approved expenses with requester and amount |
| FE-TR-05 | TreasuryPage: approved expenses | shows an empty state when nothing is waiting |
| FE-TR-06 | TreasuryPage: approved expenses | explains a 503 from the expense service instead of showing a table |
| FE-TR-07 | TreasuryPage: approved expenses | disables Disburse when the balance is lower than the amount |
| FE-TR-08 | TreasuryPage: disburse flow | asks for confirmation, disburses, shows success and refreshes the data |
| FE-TR-09 | TreasuryPage: disburse flow | cancel closes the confirmation without calling the API |
| FE-TR-10 | TreasuryPage: disburse flow | a 503 says nothing was paid and names the FAILED (refunded) transfer |
| FE-TR-11 | TreasuryPage: disburse flow | a 409 rule violation shows the server message |
| FE-TR-12 | TreasuryPage: disburse flow | the confirm button shows progress and cannot be double-clicked |
| FE-TR-13 | TreasuryPage: ledger | lists recent transfers with status badges and failure reasons |
| FE-TR-14 | TreasuryPage: ledger | shows an empty ledger message |
| FE-TR-15 | TreasuryPage: ledger | pages through the ledger |
| FE-PG-01 | Pager | renders nothing for a single page |
| FE-PG-02 | Pager | disables Previous on the first page and Next on the last |
| FE-PG-03 | Pager | calls onChange with the neighbouring page |

#### `TransferHistoryPage.test.tsx`

| ID | Group | Scenario and expected result |
|---|---|---|
| FE-TH-01 | TransferHistoryPage | lists my transfers with amount, status and a short reference |
| FE-TH-02 | TransferHistoryPage | totals only COMPLETED transfers on the page |
| FE-TH-03 | TransferHistoryPage | explains FAILED transfers were not paid |
| FE-TH-04 | TransferHistoryPage | shows an empty state |
| FE-TH-05 | TransferHistoryPage | shows a loading state, then an error with the 503 message |
| FE-TH-06 | TransferHistoryPage | pages forward and back |
| FE-TH-07 | TransferHistoryPage | uses singular wording for one transfer |

#### `treasuryApi.test.ts`

| ID | Group | Scenario and expected result |
|---|---|---|
| FE-API-01 | treasuryApi requests | getTreasury GETs /treasury and unwraps data |
| FE-API-02 | treasuryApi requests | getDisbursableExpenses GETs /treasury/disbursable |
| FE-API-03 | treasuryApi requests | disburseExpense POSTs the expenseId to /transfers |
| FE-API-04 | treasuryApi requests | getTransfers sends defaults and an optional status |
| FE-API-05 | treasuryApi requests | getMyTransfers GETs /transfers/me with paging |
| FE-API-06 | treasuryApi requests | errors from the client propagate |
| FE-API-07 | describeTreasuryError | uses the server message and the FAILED transfer reference |
| FE-API-08 | describeTreasuryError | falls back to a friendly 503 message or a generic HTTP message |
| FE-API-09 | describeTreasuryError | reports network failures (no response) |
| FE-API-10 | formatMoney | formats pesos with grouping and two decimals |
| FE-API-11 | formatMoney | formats zero and other currencies |
| FE-API-12 | formatMoney | keeps negative amounts readable |
