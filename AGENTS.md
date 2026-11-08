# LDES-admin: AI contributor instructions

Administration, monitoring and sandboxing for OpenLDES servers and LDIO pipelines.
Read this file first, then the topic guides relevant to the task.

## Non-negotiables

* Preserve the hexagonal boundaries: `bootstrap -> adapter -> application -> model`.
  The inner rings are framework-free; inbound adapters call use cases, not services.
* Follow nearby code, not generic Spring templates. Lombok belongs only in JPA entities.
* Never modify [_analysis/README.md](_analysis/README.md), the product specification.
  Update [_analysis/architecture.md](_analysis/architecture.md) when architecture changes.
* Do not commit, push, publish or deploy unless explicitly asked. Preserve existing work.
* Verify OpenLDES behaviour against real servers when changing an integration.
* Never expose secrets or send private code/data to external services.
* Report what changed and any unverified or blocked work accurately; human review is still required.

## Stack and sources of truth

Java 25, Spring Boot 4, Maven, Postgres/Flyway, Jena, LDI, Testcontainers and ArchUnit;
Vue 3, TypeScript, Vite, PrimeVue, Vue Flow, Pinia, Jest and Cypress.
Exact versions live in [pom.xml](pom.xml), [frontend/package.json](frontend/package.json),
[frontend/pnpm-lock.yaml](frontend/pnpm-lock.yaml) and
[application.yaml](bootstrap/src/main/resources/application.yaml).
[README.md](README.md) explains setup and commands.

## Task routing

| Task                                                | Read first                                                                     |
|-----------------------------------------------------|--------------------------------------------------------------------------------|
| Java code or dependency changes                     | [Java style](docs/ai/java-style.md), [annotations](docs/ai/annotations.md)     |
| Add a feature, port, service or persistence path    | [Architecture](docs/ai/layered-architecture.md), [mappers](docs/ai/mappers.md) |
| REST endpoint or API contract                       | [Controllers](docs/ai/controllers.md), [exceptions](docs/ai/exceptions.md)     |
| Vue, TypeScript, demo mode or sandbox editor        | [Frontend](docs/ai/frontend.md)                                                |
| LDES/LDIO, RDF, polling or sandbox execution        | [OpenLDES behaviour](docs/ai/openldes.md), [security](docs/ai/security.md)     |
| Tests, builds or verification                       | [Testing](docs/ai/testing.md)                                                  |
| Logs, monitoring or outgoing HTTP                   | [Logging](docs/ai/logging.md), [observability](docs/ai/observability.md)       |
| Authentication, URLs, credentials or Docker         | [Security](docs/ai/security.md)                                                |
| Planning, delegation, handoff or commit preparation | [Agentic workflows](docs/ai/agentic-workflows.md)                              |
| Documentation or AI instructions                    | [Guide index and maintenance](docs/ai/README.md)                               |

## Working and verifying

Inspect the worktree and relevant code before editing. Make the smallest complete change, including
the HTTP and mock frontend implementations when a contract changes. Use focused tests while
iterating. For code changes, finish with `./mvnw verify`; for frontend changes also run
`pnpm build`, `pnpm test` and `pnpm test:e2e` in `frontend/`. Documentation-only changes use the
documentation checks in [testing.md](docs/ai/testing.md).

Use `./mvnw spotless:apply` for Google Java Format; Maven checks formatting during `validate`.
Do not invent an OpenAPI file, tracing stack or layered `ServiceImpl` convention:
none is the current project contract. See [local environment notes](docs/ai/local-environment.md)
for machine-specific workarounds, not portable requirements.
