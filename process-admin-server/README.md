# Process administration API server

Runnable Spring Boot host for the management API, process workflow/event ingress,
Quartz cron triggers, database process definitions, durable process commands and
JDBC worker delivery. No separate service registry is required.

## Start locally

Requires the repository's Java 25 toolchain and Maven. From the repository root:

```sh
export PROCESS_MANAGEMENT_API_KEY="$(openssl rand -hex 32)"
mvn install -q -pl process-admin-server -am -DskipTests
java -jar process-admin-server/target/process-admin-server-exec.jar --spring.profiles.active=dev
```

The API listens on `http://127.0.0.1:8080`. Keep that terminal running. The dev profile
creates/updates the JPA tables and initializes the outbox/worker tables in a file-backed
H2 database at `./.process-admin/data`, relative to your launch directory. Restarting
preserves definitions, schedules and history; Git ignores that directory. This is local
development configuration, not a production migration strategy. Do not run two servers
against the same H2 file, and do not use `dev` with a production datasource.

To use Maven instead, after the build above, run:

```sh
mvn -pl process-admin-server spring-boot:run -Dspring-boot.run.profiles=dev
```

Do not add `-am` to `spring-boot:run`: the upstream library modules have no application
main class. The normal jar remains a Maven dependency; the `-exec.jar` is the executable.

In a second terminal, start the UI:

```sh
cd process-ui
npm ci
PROCESS_API_TARGET=http://127.0.0.1:8080 npm run dev
```

Open the Vite URL. Enter the tenant (for example `default`) and the same key generated
in the first terminal. If you need to see the locally generated value for entry into the UI,
use `printf '%s\n' "$PROCESS_MANAGEMENT_API_KEY"` in the first terminal **before** starting
Java; that explicitly displays a secret, so do not copy its output into logs or messages.
There is no generated/default key inside the application. Missing/short keys prevent startup.
Generating a new key on the next start changes access credentials but does not delete data.

To test the API from the terminal where the key is available (before launching Java,
or with that variable securely supplied in another terminal):

```sh
curl http://127.0.0.1:8080/process-management/api/info \
  -H "X-Process-Management-Key: $PROCESS_MANAGEMENT_API_KEY" \
  -H "x-chenile-tenant-id: default"
```

The response identifies the database configurator, writable definitions and available
Quartz scheduler. The database starts empty: add your definitions from the UI before
generating their ProcessCreate events. No sample/business definitions are installed automatically.

## What executes work?

The server enqueues splitter/executor/aggregator jobs into `chenile_process_work_item`
through the existing JDBC starter. It does **not** ship business worker implementations
or run the worker poller. A new leaf process can therefore show EXECUTING while its job
waits in PENDING; composite processes wait at SPLIT_PENDING until their split is performed.

Run your application's JDBC workers with access to the same database and registered
`BatchService` implementations. Workers reporting results through this host's `/process`
HTTP endpoints must include the administrator key and tenant headers. Do not enable
`chenile.process.worker.jdbc.run-worker` in this stock host without supplying business workers.
The outbox dispatcher is enabled by default and delivers process commands to the JDBC queue.
If you explicitly disable outbox delivery, inline commands still use the JDBC starter.

## PostgreSQL deployment

Without the dev profile, the server requires an explicit PostgreSQL JDBC URL and
validates rather than creates the JPA schema. It also verifies that delivery tables exist.

```sh
export PROCESS_DATABASE_URL='jdbc:postgresql://localhost:5432/process_management'
export PROCESS_DATABASE_USERNAME='process_admin'
# Supply PROCESS_DATABASE_PASSWORD and PROCESS_MANAGEMENT_API_KEY from your secret manager.
java -jar process-admin-server/target/process-admin-server-exec.jar
```

Provision the complete existing process/trigger/JPA schema first, plus:

- `process-outbox/src/main/resources/chenile-process-outbox-schema.sql`
- `jdbc-process-starter/src/main/resources/chenile-process-work-schema.sql`
- `process-service/src/main/resources/chenile-process-management-history-schema.sql`

The history migration assumes that `process_table` already exists. This server does not
replace your schema migration tooling or automatically upgrade existing production tables.
Apply earlier trigger/process/outbox migrations as required by your deployed version.
Use a dedicated schema and one scheduler owner for the current cron implementation.

`PROCESS_ADMIN_PORT` overrides port 8080. `PROCESS_ADMIN_BIND_ADDRESS` overrides the
loopback bind address; only expose it through an appropriately secured HTTPS gateway.
Production defaults remain loopback-bound, suitable for a local reverse proxy.

## Security

The shared key grants administrator access across all tenants. Every HTTP route in this
standalone host is protected—including `/process`, `/processChildren`, `/info` and error
routes—so the old workflow endpoints cannot bypass the dashboard's authentication.
Both management enablement and all-route protection are required for this launcher.
Existing library hosts still protect only the management routes unless they opt in to
`chenile.process.management.protect-all-http-routes=true`.

The H2 console is disabled and external API payload/header logging is disabled. Never
put the key in Git, frontend environment variables or URLs. The React UI stores it only
in memory. This first version has no per-user roles, automatic expiry or rotation service;
rotate the deployment secret and restart the server to replace it.

For API contracts, history semantics, tenant selection and deployment limitations, see
[the management UI guide](../docs/process-management-ui.md).
