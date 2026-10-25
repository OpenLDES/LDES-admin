# LDES-admin

Administration, monitoring and sandboxing for [OpenLDES](https://openldes.org/) servers and Linked
Data Interaction Orchestrator (LDIO) pipelines.

* [analysis/README.md](analysis/README.md) – what LDES-admin does
* [analysis/architecture.md](analysis/architecture.md) – how it is built

## Project layout

| Directory     | What                                                                                                                        |
|---------------|-----------------------------------------------------------------------------------------------------------------------------|
| `model`       | Domain model (plain Java)                                                                                                   |
| `application` | Use cases, ports and services (plain Java)                                                                                  |
| `adapter`     | REST API, monitor, Postgres (JPA + Flyway), LDES server and LDIO clients, local Docker runtime                              |
| `frontend`    | Vue 3 + TypeScript + PrimeVue + Vue Flow frontend, in demo or production mode; Maven packages the build as static resources |
| `bootstrap`   | Spring Boot application, serving the API and the frontend                                                                   |

## Requirements

* JDK 25
* Docker, for the database during development, the integration tests and local LDES servers/LDIOs
* pnpm 9, only to run the frontend on its own (see [below](#2-backend-and-frontend-separately)).
  The frontend needs Node.js 22.12 or later (Vite 7); pnpm downloads and uses Node.js 24.21.0 for
  it automatically, whatever Node.js your shell runs (`use-node-version` in `frontend/.npmrc`).

## Running LDES-admin

There are two ways to run LDES-admin: as one application, built by Maven, or as a backend and a
frontend that run separately.

### 1. One application, from the Maven build

To use LDES-admin, or to work on the backend. Maven builds the frontend along with the backend, and
the Spring Boot application serves it. You need neither Node.js nor pnpm: Maven downloads Node.js
into `frontend/node/` and pnpm through corepack.

```shell
./mvnw install -DskipTests
./mvnw -pl bootstrap spring-boot:run
```

This starts Postgres from `compose.yaml`, and LDES-admin on <http://localhost:8080>: the frontend on
`/` and the API on `/api/v1`. In the IDE, run `TestLdesAdminApplication` (bootstrap test sources)
instead to get Postgres from Testcontainers.

The frontend is what the last Maven build packaged: a change to it shows after
`./mvnw -pl frontend install` and a restart. When you only change the backend,
`./mvnw install -Dfrontend.skip` skips building the frontend and keeps the one in `frontend/dist/`.

`./mvnw package` builds `bootstrap/target/ldes-admin-bootstrap-*.jar`, which holds the frontend
too; give it a database with `spring.datasource.*`.

### 2. Backend and frontend separately

To work on the frontend. Vite serves the frontend, with hot reloading: a change shows in the browser
as soon as you save it. The backend runs on its own, and Vite forwards the calls to its API.

Start the backend, without building the frontend:

```shell
./mvnw install -DskipTests -Dfrontend.skip
./mvnw -pl bootstrap spring-boot:run
```

Then, in another terminal, start the frontend with pnpm:

```shell
cd frontend
pnpm install
pnpm dev          # http://localhost:5173
```

Open the frontend on <http://localhost:5173>; <http://localhost:8080> still serves whatever the last
Maven build packaged. Vite proxies `/api` and `/actuator` to the backend on
<http://localhost:8080>. To use a backend elsewhere, set `LDES_ADMIN_BACKEND`:

```shell
LDES_ADMIN_BACKEND=https://ldes-admin.example.org pnpm dev
```

The frontend works without a backend too: it then starts in demo mode, on sample data.

## Demo and production mode

The switch in the top bar picks the mode, whichever way LDES-admin runs:

* **Demo** runs on sample data in the browser, to explore LDES-admin without any server.
* **Production** talks to the backend, which manages real LDES servers and LDIOs.

Without an earlier choice, the frontend starts in production mode when the backend answers, and in
demo mode otherwise.

## Tests and builds

With Maven, for the whole project:

```shell
./mvnw test     # unit, slice and architecture tests, and the frontend unit tests
./mvnw verify   # also the integration tests (Docker; pulls the LDES server and LDIO images)
```

With pnpm, for the frontend:

```shell
cd frontend
pnpm test         # unit tests (Jest)
pnpm test:e2e     # end-to-end tests (Cypress) against a production build
pnpm build        # type-check and build to dist/
```

## Configuration

Configuration is in `bootstrap/src/main/resources/application.yaml`: the Docker images for local
instances (`ldes-admin.local-runtime.*`), how often the instances are monitored
(`ldes-admin.monitor.*`), and `ldes-admin.security.enabled` to require a JWT.

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
