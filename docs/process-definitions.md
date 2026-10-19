# Process definition REST service

`process-api` exposes `ProcessDefinitionService` and the existing `ProcessDef` model.
`process-service` implements validation and persistence in `ProcessDefinitionServiceImpl` and
exposes a thin Chenile `ProcessDefinitionController`. The serialized JPA `ProcessDefinition`
entity remains internal. Responses contain definition objects, never double-encoded JSON strings.

## Endpoints

| Method | URL | API operation | Success |
| --- | --- | --- | --- |
| POST | `/process-definitions` | `create(ProcessDef)` | 201 |
| PUT | `/process-definitions/{processType}` | `update(processType, ProcessDef)` | 200 |
| GET | `/process-definitions/{processType}` | `retrieve(processType)` | 200 |
| GET | `/process-definitions` | `findAll()` | 200 |

The service ID is `processDefinitionService`; the implementation bean is
`_processDefinitionService_` with `processDefinitionService` as an alias. Each controller method
binds arguments and calls `ControllerSupport.process(...)`. `ChenileResponseCodes` supplies the
create status, and the usual `GenericResponse` envelope carries `payload`, `code`, and success/errors.

```json
{
  "processType": "File",
  "leaf": true,
  "args": "com.example.model.FileInput",
  "config": {"queue": "file-work", "chunkSize": "100"}
}
```

POST requires `processType`. PUT replaces the definition and takes identity from the path;
omitting `processType` in the body is allowed, but a conflicting value is rejected. Listing is
sorted by process type. Defaults such as `predecessorArgs = BOTH` still apply when omitted.

## Storage, validation, and scope

Definitions retain their current **global** scope: `process_definition` is keyed by `processType`,
not tenant. Requests may carry the normal `x-chenile-tenant-id` context, but definitions are shared.
The host must apply its administrative authorization policy to these endpoints. The stock admin
server protects them through its existing all-routes API-key filter. Registering these routes does
not require enabling the optional management dashboard.

With `chenile.process.configurator=database`, create and update persist transactionally. Creation
uses an explicit JPA insert instead of an assigned-ID merge, so duplicate creation cannot silently
overwrite an existing definition. Successful commits invalidate the local runtime configurator
cache, including cached misses and predecessor mappings. Rollback leaves the persisted definition
and existing runtime cache unchanged. Invalidation remains local to the server JVM; this API does
not introduce cross-replica cache coordination or definition revision pinning for running processes.

With the JSON configurator, retrieve and list expose the loaded definitions; both write operations
return 409. No database change is represented as an effective JSON configuration change.

Errors use Chenile exceptions and envelopes:

- 400: missing/blank/overlong process type, null configuration or predecessor argument selection,
  URL/body identity mismatch, or a parent/predecessor cycle.
- 404: missing definition on retrieve or update.
- 409: duplicate create or writes against JSON configuration.

Parent and predecessor forward references remain allowed so a definition graph can be entered
incrementally; cycles are rejected as definitions are added or replaced. No server-side check
assumes that a worker implementation or input class is available in the server JVM. Defining a
new worker type still requires generating, implementing, and deploying its worker code.

Deletion is intentionally absent because definitions may be referenced by other definitions or
running processes. No schema migration, tenant partitioning, or running-process version migration
is introduced by this REST service.

## Existing management clients

`/process-management/api/definitions` retains its existing URLs, response format, status codes, and
manual refresh endpoint. Its list/create/update methods now delegate to `ProcessDefinitionService`
instead of maintaining separate validation and persistence logic. The manual refresh endpoint
continues to clear only the local database configurator cache. New clients can use the dedicated
Chenile service or discover `processDefinitionService` through normal service-registry publication.

## Verification

The Cucumber feature in `process-service/src/test/resources/definition-features` exercises actual
HTTP bindings, the Chenile highway, service transactions, and H2 persistence with the dashboard
disabled. It covers success/envelope statuses, global visibility, duplicate creation, missing
records, invalid values, parent/predecessor cycles, URL identity, cache misses, and rollback.
A companion HTTP regression checks JSON read-only behavior; existing management integration tests
verify compatibility of the old endpoints.
