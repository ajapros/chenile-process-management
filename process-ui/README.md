# Chenile process operations UI

React + Vite administration UI. Setup, security, schema migration, API contracts and
deployment details are in [the management UI guide](../docs/process-management-ui.md).

```sh
npm ci
PROCESS_API_TARGET=http://localhost:8080 npm run dev
```

Use `npm test` for component/client/graph tests and `npm run build` for production assets.
The backend must explicitly enable `chenile.process.management.enabled` and configure
an administrator key. Enter that key in the UI, never in a Vite environment variable.

For a backend that starts directly from this repository, follow
[the process-admin-server startup instructions](../process-admin-server/README.md).
