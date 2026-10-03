# Chenile Process Management

Chenile process manager takes care of managing a long process along with sub-processes.
Typical map-reduce processes follow the splitter-aggregator pattern of breaking down a problem
into smaller problems which are solved in their own process. 

We need an overall Process Orchestrator that keeps track of kicking off all the sub-processes 
and ensures that all the sub-processes are completed before the overall process can be marked 
as complete.

Chenile Process Manager addresses this need. 
It is super flexible and can be controlled using a JSON file. 

## Creating and observing processes

A [React operations UI and tenant-scoped management API](docs/process-management-ui.md)
now supports cron schedules, trigger history, process definitions, process/subprocess
drill-downs, completion-event inspection and end-to-end execution graphs. See the guide
for the opt-in administrator API, required history migration and local UI startup.

The [standalone API launcher](process-admin-server/README.md) can now run directly from this repository:

```sh
export PROCESS_MANAGEMENT_API_KEY="$(openssl rand -hex 32)"
mvn install -q -pl process-admin-server -am -DskipTests
java -jar process-admin-server/target/process-admin-server-exec.jar --spring.profiles.active=dev
```

It listens on localhost:8080, persists local development data in H2, and enqueues JDBC
worker jobs. Business worker execution requires your application's worker implementations.

`POST /process` and the `ProcessCreate` event accept a `ProcessDto`:

```json
{"processDefName":"feed","args":{"file":"input.csv"},"triggerId":"run-123"}
```

`chenile-trigger` schedules crontabs and dispatches events; its `x-chenile-trigger-id`
header supplies correlation when the payload omits `triggerId`. `TriggerLog` in
`chenile-trigger` stores only trigger dispatch idempotency and status. Its table is
`trigger_log`, with a unique constraint on `(trigger_id, event_name)`.
Direct process creation is not deduplicated using this table and does not write to it.
Tenant identity comes from `x-chenile-tenant-id`, and terminal processes emit
`ProcessCompleted` with the tenant header for subscribers.

The process manager subscribes to `ProcessCompleted` and exposes `POST /process/completed`.
`ProcessCompletedEvent` extends `ProcessDto`, so it can be used as the creation DTO.
A definition such as the following starts an `index` process after an `import` completion:

```json
{"processMap":{"index":{"leaf":true,"predecessorProcessType":"import","predecessorArgs":"BOTH","config":{}}}}
```

Every matching definition starts a new root, for both successful and failed completions.
No match starts none. The new process retains the trigger and tenant and records the
completed process ID as `predecessorId`. Set `predecessorArgs` on each successor definition
to choose `INPUT`, `OUTPUT`, or `BOTH` (the default when omitted or null).
`INPUT` supplies only the `input` key; `OUTPUT` supplies only the `output` key;
`BOTH` supplies both keys:

```json
{"input":{"batch":7},"output":{"rows":100}}
```

Valid JSON values are decoded; plain text stays a string and absent values become null.
The completion event is not mutated when starting processes. Chaining does not consult
`TriggerLog`; repeated completion ingress can create additional processes unless durable mode is enabled.

Every splitter, executor, and aggregator receives a copy of `ProcessDef.config`.
Use this single map for all worker settings for a process type.

See [the process-management guide](docs/process-management.md) for the lifecycle,
module wiring, tests, and remaining durability limitations.

## Opt-in durable process consequences

Set `chenile.process.outbox.enabled=true` after migrating the PostgreSQL outbox and receipt schema.
State saves and child/parent/worker/chaining commands commit atomically. The poller retries failures;
receipts prevent repeated parent counting and successor creation. The default remains synchronous.
Framework chaining is durable; arbitrary application completion listeners remain best-effort.
See [setup, monitoring, replay, and PostgreSQL tests](docs/process-outbox.md).

## Database-backed process definitions

`process-service` can load the same `ProcessDef` JSON structure from a database
instead of a classpath JSON file. Set the following property to select it:

```properties
chenile.process.configurator=database
```

The service reads `process_definition`, keyed by `process_type`. Its
`definition` column contains the JSON that would otherwise be the value in the
`processMap` for that type. For example:

```sql
create table process_definition (
  process_type varchar(255) primary key,
  definition text not null
);

insert into process_definition (process_type, definition) values
('chunk', '{"leaf":true,"config":{"batchSize":"100"}}');
```

`DatabaseProcessConfigurator` caches definitions and missing names in a synchronized
`HashMap`. Named lookups load on demand; the first predecessor lookup loads all
definitions, and subsequent lookups use that same cache. After changing database
definitions, call `clearCache()` on each configurator instance to reload on the next
lookup. If the property is absent, the classpath JSON `ProcessConfigurator` remains
the default.

## Production Worker Execution

The framework supports three worker launch modes:

- `invm-process-starter`: posts work to the in-VM event processor. This is useful for unit tests and simple local flows.
- `q-based-process-starter`: publishes work to Chenile pub/sub. This remains useful when a message broker is the platform standard.
- `jdbc-process-starter`: persists work into `chenile_process_work_item` so workers can be scaled by a database backlog using KEDA.

Use `jdbc-process-starter` for production bulk/batch use cases where work must be idempotent, retryable, and non-ambiguous without RabbitMQ.

## JDBC Starter Tables

`jdbc-process-starter` ships `chenile-process-work-schema.sql`.

The main table is `chenile_process_work_item`:

- `process_id`, `process_type`, `worker_type`: identify the process worker.
- `idempotency_key`: unique key derived from process ID, type, and worker type.
- `payload`: serialized `WorkerDto`.
- `status`: `PENDING`, `RUNNING`, `SUCCESS`, or `DEAD`.
- `attempt`, `locked_by`, `locked_until`: retry and lease metadata.

Workers claim rows with `FOR UPDATE SKIP LOCKED`. Expired `RUNNING` rows are eligible for retry until `chenile.process.worker.jdbc.max-attempts`.

## Configuration

```properties
spring.sql.init.schema-locations=classpath:chenile-process-work-schema.sql
chenile.process.worker.jdbc.run-worker=false
chenile.process.worker.jdbc.lock-seconds=300
chenile.process.worker.jdbc.max-attempts=5
chenile.process.worker.jdbc.poll-interval-millis=5000
chenile.process.worker.jdbc.worker-id=${HOSTNAME:local-process-worker}
```

API pods normally set `run-worker=false`; worker pods set `run-worker=true`.

## KEDA Backlog Query

Use Postgres KEDA scaling with this query:

```sql
select count(*)
from chenile_process_work_item
where status = 'PENDING'
   or (status = 'RUNNING' and locked_until < now())
```

This scales workers from actual durable backlog rather than broker depth.

## Bulk Upload Reference Sample

See `chenile-samples/bulk-upload-process-sample` for a complete reference:

- CSV upload stored in object storage.
- Postgres metadata and row-level result tables.
- splitter creates deterministic chunk subprocesses.
- executors validate row ranges and store idempotent row results.
- aggregator stores a final result summary.
- Docker Compose and Kubernetes/KEDA manifests are included.
