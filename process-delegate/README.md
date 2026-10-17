# Process delegate and worker support

`process-delegate` contains the process-manager client and the worker support
previously supplied by `process-utils`:

- `ProcessManagerClient`, its proxy implementation, and delegate configuration;
- `BatchService` and `IWorker` APIs;
- `BatchServiceBase`, `SplitterBase`, `ExecutorBase`, and `AggregatorBase`;
- process, error, and output helpers.

Depend on `org.chenile.orchestrator.process:process-delegate`. Its runtime
dependencies remain `process-api` and `chenile-proxy`; it does not depend on the
process-service implementation or the database delivery modules.

All Java packages are preserved. Replace the old `process-utils` Maven dependency
rather than including both artifacts. The client configuration continues to use
`process.manager.base-url` for remote manager calls.
