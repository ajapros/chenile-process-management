# Chenile service and controller conventions

New controllers follow the `bp-service` blueprint in `chenile-gen`: API models and public
contracts live in `name-api`; implementations, repositories, configuration, and controllers
live in `name-service`. API consumers do not depend on the service implementation.

A controller uses `@RestController`, `@ChenileController`, and `ControllerSupport`. Bind its
public service ID to the implementation bean (the blueprint uses `_nameService_`) and delegate
every operation through `process(...)`, passing every body/path argument in service-signature
order. It must not call service implementations or repositories directly. Return typed
`ResponseEntity<GenericResponse<T>>`; keep validation, tenant checks, and transactions in the
service. Use Chenile exceptions for intended HTTP errors, `ChenileResponseCodes` for configured
status, and `ResponseHeadersAware` for service-provided response headers.

Framework configuration never declares entity or repository scans. Discovery belongs to the
application or test host. Custom headers begin with `x-`, and tests pass tenant identity through
`x-chenile-tenant-id`. Preserve transaction boundaries for external side effects.

BDD follows the same blueprint: a Cucumber runner, Spring glue with
`@CucumberContextConfiguration`/`@SpringBootTest`/`@AutoConfigureMockMvc`, and features using
`org.chenile.cucumber.rest` from `cucumber-utils`. Exercise the actual highway and service, with
real persistence where relevant; mock external adapters only. Cover normal behavior, errors,
tenant isolation, and meaningful side effects. See `crontab-service/src/test` as the concrete
reference. After module moves, clean-build the reactor to avoid stale class files.

The reusable `chenile-service-controller` Codex skill is installed in
`~/.codex/skills/chenile-service-controller/SKILL.md` and applies to every new Chenile controller.
