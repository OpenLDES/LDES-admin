# LDES-admin

Administration, monitoring and sandboxing for [OpenLDES](https://openldes.org/) servers and Linked
Data Interaction Orchestrator (LDIO) pipelines.

![The dashboard: health, throughput and recent activity of all LDES servers and LDIOs](docs/screenshots/dashboard.png)

## Features

### Monitor

* **Dashboard**: which LDES servers and LDIOs are up, how many pipelines run, the members ingested
  per hour over the last 24 hours, what needs attention, and a feed of recent activity.
* **Health checks**: every instance is checked every minute; LDES-admin records when one goes down
  and when it comes back.
* **Event stream statistics**: members ingested, bucketised and paginated per view, and the backlog
  still waiting to be paginated.
* **Background processes**: the fragmentation and maintenance jobs of a server (bucketisation,
  pagination, retention, compaction, deletion) with their runs, failures and durations.

| ![Views of an event stream and how far they are paginated](docs/screenshots/event-stream.png) | ![The background processes of an LDES server](docs/screenshots/background-processes.png) |
|:---:|:---:|
| Views of an event stream, and how far they are paginated | Background processes, with a failed retention run |

### Manage LDES servers

* **Add** a server that runs elsewhere by its URL, or **start a local one** in Docker with one click.
* **Event streams and views**: create them in a form and as Turtle side by side, validated the way
  the server validates them. Templates for pagination, time-based, geospatial and reference
  fragmentation, and retention policies.
* **Shapes and DCAT**: edit the SHACL shape of an event stream and the DCAT catalog of the server,
  and see the catalog it publishes.
* **Browse** a view page by page: follow its relations and read its members.
* **Ingest** members directly on an event stream, in Turtle, JSON-LD, N-Quads or N-Triples.

| ![LDES servers, registered and local](docs/screenshots/ldes-servers.png) | ![The event streams of an LDES server](docs/screenshots/ldes-server.png) |
|:---:|:---:|
| LDES servers, registered and local | Event streams with their statistics |
| ![Creating a view: a form and Turtle that stay in step](docs/screenshots/create-view.png) | ![Browsing the pages and members of a view](docs/screenshots/fragment-browser.png) |
| Creating a view: form and Turtle stay in step | Browsing the pages and members of a view |

### Manage LDIOs

* **Add** an LDIO that runs elsewhere, or **start a local one** in Docker.
* **Pipelines**: their status, members in and out and the progress of LDES clients. Halt, resume,
  edit and delete them.
* **Component catalog**: the inputs, adapters, transformers and outputs an LDIO has installed.

![The pipelines of an LDIO](docs/screenshots/ldio.png)

### Experiment in the sandbox

* **Design** a pipeline by dragging components onto a canvas, or start from a template. Configure
  every LDIO component in a form or as YAML, validated as you type.
* **Simulate**: run a member through the pipeline in the browser and see what each step makes of
  it.
* **Deploy** the pipeline to an LDIO, or start the **sandbox environment**: a local LDES server and
  LDIO in Docker that work together out of the box.

| ![Designing a pipeline in the sandbox](docs/screenshots/sandbox.png) | ![Simulating a pipeline step by step](docs/screenshots/simulation.png) |
|:---:|:---:|
| Designing a pipeline, with a form for every component | Running a member through the pipeline |

### Try it without servers

**Demo mode** runs on sample data in the browser, to explore LDES-admin without any server;
**production mode** manages real LDES servers and LDIOs. Light and dark themes included.

## Documentation

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

## Usage of AI

This project was developed with the help of AI tools. These tools were used to assist in code
generation, documentation, and problem-solving. The developers reviewed and edited the AI-generated
content to ensure accuracy and quality.
