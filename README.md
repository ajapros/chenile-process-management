# Chenile Process Management

Chenile process manager takes care of managing a long process along with sub-processes.
Typical map-reduce processes follow the splitter-aggregator pattern of breaking down a problem
into smaller problems which are solved in their own process. 

We need an overall Process Orchestrator that keeps track of kicking off all the sub-processes 
and ensures that all the sub-processes are completed before the overall process can be marked 
as complete.

Chenile Process Manager addresses this need. 
It is super flexible and can be controlled using a JSON file. 

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
