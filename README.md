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
| `adapter`     | REST API, monitor, Postgres (JPA + Flyway), LDES server and LDIO clients, local Docker runtime |
| `bootstrap`   | Spring Boot application                                                    |
| `frontend`    | Vue 3 + TypeScript + PrimeVue + Vue Flow frontend, in demo or production mode |

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
instances (`ldes-admin.local-runtime.*`), how often the instances are monitored
(`ldes-admin.monitor.*`), and `ldes-admin.security.enabled` to require a JWT.

## Frontend

```shell
cd frontend
pnpm install
pnpm dev          # http://localhost:5173
pnpm test         # unit tests (Jest)
pnpm test:e2e     # end-to-end tests (Cypress) against a production build
pnpm build        # type-check and build to dist/
```

The switch in the top bar picks the mode:

* **Demo** runs on sample data in the browser, to explore LDES-admin without any server.
* **Production** talks to the backend, which manages real LDES servers and LDIOs. `pnpm dev` and
  `pnpm preview` proxy `/api` and `/actuator` to <http://localhost:8080>; set `LDES_ADMIN_BACKEND`
  to use another backend.

Without an earlier choice, the frontend starts in production mode when the backend answers, and in
demo mode otherwise.

## Sandbox

In production mode, *Start sandbox* (on the dashboard, or *Sandbox environment* in the sandbox)
starts a local LDES server and LDIO in Docker, on the machine running the backend. They work
together out of the box:

* the LDES server has an event stream `sandbox-parcels`, with a paginated view `by-page` and a
  time-based view `by-time`;
* the LDIO has a pipeline `sandbox-ingest` that receives members over HTTP and ingests them in
  `sandbox-parcels`;
* inside the sandbox, pipelines reach the event stream on
  `http://sandbox-ldes-server:8080/sandbox-parcels`, so pipelines designed in the sandbox can write
  to it (`Ldio:HttpOut`) and read from it (`Ldio:LdesClient`).

The first start pulls the Docker images, which takes a few minutes. The sandbox stops with *Stop*,
and with the backend.
