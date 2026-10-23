# LDES-admin

Administration, monitoring and sandboxing for [OpenLDES](https://openldes.org/) servers and Linked
Data Interaction Orchestrator (LDIO) pipelines.

* [analysis/README.md](analysis/README.md) – what LDES-admin does
* [analysis/architecture.md](analysis/architecture.md) – how it is built

## Project layout

| Directory     | What                                                                       |
|---------------|----------------------------------------------------------------------------|
| `model`       | Domain model (plain Java)                                                  |
| `application` | Use cases, ports and services (plain Java)                                 |
| `adapter`     | REST API, Postgres (JPA + Flyway), LDES server and LDIO clients, local Docker runtime |
| `bootstrap`   | Spring Boot application                                                    |
| `frontend`    | Vue 3 + TypeScript + PrimeVue + Vue Flow frontend, running on sample data  |

## Requirements

* JDK 25
* Docker, for the database during development, the integration tests and local LDES servers/LDIOs
* pnpm 9. The frontend needs Node.js 22.12 or later (Vite 7); pnpm downloads and uses Node.js
  24.21.0 for it automatically, whatever Node.js your shell runs (`use-node-version` in
  `frontend/.npmrc`)

## Backend

```shell
./mvnw install -DskipTests
./mvnw -pl bootstrap spring-boot:run
```

This starts Postgres from `compose.yaml` and the API on <http://localhost:8080/api/v1>. In the IDE,
run `TestLdesAdminApplication` (bootstrap test sources) instead to get Postgres from Testcontainers.

```shell
./mvnw test     # unit, slice and architecture tests
./mvnw verify   # also the integration tests (Docker; pulls the LDES server and LDIO images)
```

Configuration is in `bootstrap/src/main/resources/application.yaml`: the Docker images for local
instances (`ldes-admin.local-runtime.*`), and `ldes-admin.security.enabled` to require a JWT.

## Frontend

```shell
cd frontend
pnpm install
pnpm dev          # http://localhost:5173
pnpm test         # unit tests (Jest)
pnpm test:e2e     # end-to-end tests (Cypress) against a production build
pnpm build        # type-check and build to dist/
```

The frontend runs on in-memory sample data for now; see
[analysis/architecture.md](analysis/architecture.md#frontend) for connecting it to the backend.
