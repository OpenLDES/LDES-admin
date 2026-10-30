# Architecture: hexagonal, not feature-layered Spring

This filename matches the article's topic, but LDES-admin does **not** use its
`controller -> ServiceImpl -> JpaRepository` design. The authoritative architecture description
is [_analysis/architecture.md](../../_analysis/architecture.md).

## Module and package contract

| Module | Owns | Dependency direction |
|--------|------|----------------------|
| `model` | Domain records, identifiers, invariants and domain behaviour | JDK only |
| `application` | `port.in` use cases/exceptions, `port.out` integration contracts, `service` orchestration | `model` |
| `adapter` | `in.rest`, `in.scheduling`, `out.persistence.jpa`, `out.ldes`, `out.ldio`, `out.health`, `out.runtime`, `out.sandbox`, shared `out.http` helpers | `application`, domain types and integration libraries |
| `frontend` | Vue application packaged as static resources in a jar | REST contract, not Java internals |
| `bootstrap` | Spring Boot entry point, bean wiring, security, scheduling and SPA serving | Backend adapters and frontend jar |

Packages start at `org.openldes.ldesadmin`; the domain areas are `instance`, `ldes`, `ldio`,
`sandbox`, `monitoring` and `dashboard`. Extend the appropriate area rather than creating a
parallel generic feature framework.

## Request flow

```text
HTTP request
  -> adapter.in.rest request/web model
  -> application.port.in use case
  -> application.service implementation
  -> application.port.out
  -> adapter.out implementation
  -> database, remote server, Docker or LDI runtime
```

Map representations at their boundary. Controllers never reach a repository, JPA entity,
concrete service or outbound adapter. Outbound adapters never import input ports or services.
Adapters do not wire directly to other adapters; check the explicit shared HTTP grouping in
`bootstrap/.../DependencyRuleTest.java` rather than treating all outbound packages as one layer.
Bootstrap owns cross-ring wiring.

For example, registration uses `RegisterInstanceUseCase` and `RegisterInstanceService`;
`InstanceRepository` is an output port implemented by `JpaInstanceRepository`, which delegates to
the Spring Data interface `InstanceJpaRepository`. There is no need for a `ServiceImpl` suffix
or an additional public service interface beyond the input port.

## Persistence and state

LDES-admin persists instances, activity events and ingest samples. Event stream definitions and
pipelines live on the remote servers, not in new local JPA aggregates. Schema migrations live in
`adapter/src/main/resources/db/migration`. Add a migration for a schema change; do not rewrite an
already-applied migration. `spring.jpa.hibernate.ddl-auto=validate` and `open-in-view=false` are
intentional.

`Instance` distinguishes registered instances from locally owned containers. Preserve
`runtimeReference`, `internalUrl`, URL translation and ownership when modifying lifecycle code.
Unregistering a remote instance must not stop a container it does not own.

## Adding a feature

First decide which domain invariant, input port and output port are actually needed. Reuse
existing contracts where the responsibility is already represented. Implement application
behaviour without framework imports, then adapt it at the HTTP/integration edges and wire it
in bootstrap. Update both frontend API implementations if the wire contract changes.

Extend tests at the affected boundaries and keep `DependencyRuleTest` green; do not relax an
architecture rule to accommodate misplaced code. Update the architecture document only when
the design changes, not for every new method.
