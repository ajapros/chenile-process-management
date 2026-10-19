# Process operations UI and management API

The standalone React UI in `process-ui` provides cron schedule creation/editing, manual
ProcessCreate generation, execution history for all trigger sources, process definition
inspection/editing, a paginated process dashboard, child/successor drill-downs, and a
clickable causal graph plus timeline from the first trigger through completion-driven successors.
Execution queries refresh every ten seconds; configuration queries refresh after changes
or an explicit refresh. It is an administrator console, not a public end-user application.

## Enable the backend

The repository now includes a runnable [process-admin-server](../process-admin-server/README.md).
For a local API on port 8080, from the repository root:

```sh
export PROCESS_MANAGEMENT_API_KEY="$(openssl rand -hex 32)"
mvn install -q -pl process-admin-server -am -DskipTests
java -jar process-admin-server/target/process-admin-server-exec.jar --spring.profiles.active=dev
```

The explicit dev profile uses file-backed H2 and initializes local tables. The standalone
host enables database definitions, outbox dispatch and JDBC work enqueueing; actual business
workers remain application-provided. Its administrator key protects **all** HTTP routes,
including the existing process workflow endpoints. See its README for PostgreSQL deployment.

Alternatively, use an existing Spring Boot host with `process-service`, Chenile's normal
configuration scan, a datasource and its worker implementations. In that host, enable:

```properties
chenile.process.management.enabled=true
chenile.process.management.api-key=${PROCESS_MANAGEMENT_API_KEY}
chenile.process.configurator=database
```

The key must contain at least 32 characters; an enabled API without a suitable key fails
startup. Use a randomly generated secret, supplied through the host's secret manager.
Every management endpoint requires `X-Process-Management-Key` and `x-chenile-tenant-id`.
The key grants administration across ALL tenants: the tenant header selects the view,
it is not an identity/authorization claim. Requests cannot inspect or update another
tenant's execution records through that view. Definitions are global by design.
Tenant identifiers accept 1–128 letters, digits, underscores, dots or hyphens.

In a library host this is a separate opt-in Spring MVC administration API, not a change to the existing
Chenile `/process` workflow/event ingress or `/q` named-query API. Its filter authenticates
before administration calls, scopes/restores Chenile's thread-local tenant context, and
uses parameterized JPA criteria for every execution query. Existing host security filters
still apply. Restrict this route to an administrator network/gateway and serve it over HTTPS.
Do not publish the shared key in browser bundles, URLs, source control, logs or Vite env files.
The UI asks for it interactively and keeps it in memory only. No permissive CORS is enabled.

Hosts using explicit imports instead of Chenile configuration-package scanning must import
`ProcessManagementConfiguration` and `ProcessManagementController`, alongside their normal
`ProcessConfiguration`, entity scans and repository scans. Include
`org.chenile.orchestrator.process.configuration.model` and `.configuration.dao` in those scans.

Hosts must include `trigger-service` and `crontab-service` explicitly, because these dependencies
are optional in `process-service`. Scan `org.chenile.configuration.trigger` and
`org.chenile.configuration.crontab`, or import `ChenileTriggerConfiguration` and
`CrontabTriggerConfiguration` respectively. The standalone admin host already does this.

The application owns persistence discovery; framework configuration classes do not declare
entity or repository scans. Include `org.chenile.trigger.model` and `org.chenile.trigger.cron`
in the application's entity scan, and `org.chenile.trigger.store` and `org.chenile.trigger.cron`
in its repository scan, alongside the process packages above. This also applies to hosts
using Chenile configuration-package scanning.

Apply the trigger schema/upgrade from `trigger-service` (see its README), and
`process-service/src/main/resources/chenile-process-management-history-schema.sql`
before deploying the updated libraries, even if the management API is disabled: history
capture is part of trigger production and independent of the optional UI API.
The migration is additive and re-runnable. It assumes existing process/trigger tables and
Spring's snake_case physical naming; adjust the process-table indexes for a different strategy.
Test environments with Hibernate create/update create the new entity tables automatically.
Do not use Hibernate auto-update as a production migration strategy.

## Run the UI

Node 22.12 or newer (Node 24 used for verification):

```sh
cd process-ui
npm ci
PROCESS_API_TARGET=http://localhost:8080 npm run dev
```

Open the Vite URL, enter the tenant and administrator key, and connect. The development
proxy forwards only `/process-management/api` to the host. Point the target at the host's
context path as needed. For production, `npm run build` creates `dist`; serve it as static
files behind the same-origin reverse proxy that forwards `/process-management/api/*`
to the host. `npm run preview` previews static assets only; it does not provide the API proxy.
Do not treat Vite's development/preview server as a production server.

## API contract

All paths below are relative to `/process-management/api`. JSON responses are plain
DTOs, not the Chenile workflow `GenericResponse` envelope. Errors include a `message`.
Pagination is zero-based, default size 25, maximum 100, with stable newest-first ordering.
Filters are exact matches and combined with AND.

| Method / path | Purpose |
| --- | --- |
| GET `/info` | Definition source/write capability, cron availability and supported creation events |
| GET `/crontabs?page=0&size=25` | Tenant's schedules |
| POST `/crontabs` | Add a ProcessCreate schedule |
| PUT `/crontabs/{id}` | Edit/enable/disable an existing tenant's schedule |
| POST `/triggers` (202 acceptance) | Generate ProcessCreate through the normal trigger ingress |
| GET `/executions` | Trigger history; filters `triggerId`, `crontabId` (from context JSON), `status`, `page`, `size` |
| GET `/definitions` | Global active definitions (database or JSON configurator) |
| POST `/definitions` | Add a database-backed definition |
| PUT `/definitions/{name}` | Update an existing database-backed definition |
| POST `/definitions/refresh` | Clear this instance's database configurator cache |
| GET `/processes` | Filters `processType`, `status`, `triggerId`, `parentId`, `predecessorId`, `page`, `size` |
| GET `/processes/{id}` | Process input/output, errors, status and terminal completion time |
| GET `/trace?processId=...` or `?triggerId=...` | Tenant-scoped connected execution graph; exactly one identifier required |

Create/update cron body:

```json
{
  "name": "five-minute-import",
  "cronExpression": "0 0/5 * * * ?",
  "timezone": "UTC",
  "enabled": true,
  "payload": {"processDefName": "Import", "args": {"source": "feed-A"}}
}
```

The first UI/API version generates only ProcessCreate. The lower-level TriggerService
remains generic; history displays other registered trigger events without adding UI
creation support. Cron expressions use Quartz syntax including seconds, not five-field
Unix cron syntax. Quartz must be configured in the host; otherwise schedule writes return
503 while reads and manual event generation remain available. Payload definitions must exist.
Caller-supplied payload correlation/tenant headers cannot override API-assigned values.
Cron saves validate first and update Quartz after the database commits, so a rolled-back
schedule cannot be executed. A post-commit scheduler failure means the row is saved but
scheduling failed: retry the update or restart the scheduler host to reconcile persisted schedules.
The existing cron scheduler is intended for a single scheduler owner; distributed cron ownership
and durable Quartz job-store coordination are outside this first version.

Manual trigger body:

```json
{"idempotencyKey":"a-client-retry-key","payload":{"processDefName":"Import","args":{"source":"feed-A"}}}
```

The optional retry key is limited to 80 characters and namespaced by tenant. Without a key,
the API generates one. Retrying with the same key uses existing trigger idempotency and does
not create another process. After FAILED dispatch, the original idempotency claim still applies;
this UI does not reset claims or provide automatic replay of partially applied events.

Database definition body:

```json
{
  "processType":"Index", "leaf":true,
  "predecessorProcessType":"Import", "predecessorArgs":"OUTPUT",
  "config":{"batchSize":"100"}
}
```

`predecessorArgs` selects INPUT, OUTPUT or BOTH (default). The API rejects predecessor cycles.
Adding a definition does not install a splitter/executor/aggregator implementation. JSON-backed
definitions are inspectable but read-only (writes return 409); switch to the database configurator
and explicitly populate its definitions before using it. Saves invalidate this instance's HashMap
cache after commit; in a multi-instance deployment call `/definitions/refresh` on other instances
or restart them. Distributed cache invalidation and concurrent global configuration editing are
not implemented in this first version.

## History and execution relationships

Trigger history is the DISPATCH_TRIGGER view of the shared outbox. Acknowledgement contains
only the accepted command/trigger ID. Reusing the same submission idempotencyKey returns that ID;
it does not synchronously invoke the event. PENDING and RUNNING show queued/in-flight delivery;
DONE means subscriber changes committed; DEAD means attempts were exhausted. The original
payload and generic triggerContext metadata remain available for diagnosis. All queries are tenant-scoped.

Completion is read from terminal process state. Process views expose `completedAt` from the
terminal state-entry timestamp; nonterminal processes return null. There is no separate completion
snapshot table or `completionEvents` array in process detail responses. Durable outbox commands
retain their delivery payload until cleanup; process completion does not prove subscriber delivery.

Graph edges use stored `triggerId`, `parentId` and `predecessorId`, not timestamp guesses:
PROCESS_CREATE, SUBPROCESS and direct process-to-process SUCCESSOR. Starting from a child/successor walks upstream
and downstream, including legacy branches missing correlation IDs. Stored predecessor relationships work for older process rows too; absent timestamps are shown as not recorded. The graph refuses executions above
1000 processes or trigger records with HTTP 413 rather than silently presenting an incomplete execution;
use paginated process queries to inspect those branches. No archival/retention policy is imposed;
operators must choose one appropriate for argument data, errors and audit/privacy requirements.

## Verification

```sh
mvn test
cd process-ui
npm test
npm run build
npx playwright install chromium
npm run test:e2e
```

Backend HTTP integration contracts run against real repositories with mandatory outbox
delivery, covering authentication/context cleanup, tenant boundaries, trigger retries, definition
cache invalidation, cron validation/editing, completion/successor links, legacy subprocess
traversal, exact AND filters, pagination validation and oversized graph rejection. Frontend
tests cover API/header/error handling, graph layout/cycle safety and process→event drill-downs.
Desktop/mobile Playwright tests exercise the actual browser UI using synthetic API fixtures;
the backend integration tests independently verify the real HTTP/database contracts. To run
the PostgreSQL contracts and history migration too, pass
`-Dchenile.outbox.test.jdbc-url=jdbc:postgresql://localhost:5432/<database>` and
`-Dchenile.outbox.test.username=<user>` to Maven (and the password property if required).
PostgreSQL tests create and remove only their own randomly named schemas.
