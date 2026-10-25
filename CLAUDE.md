# LDES-admin – context for Claude

Administration, monitoring and sandboxing for OpenLDES servers and LDIO (Linked Data Interaction
Orchestrator) pipelines. What it does: `analysis/README.md` (the specification; **never modify
it**). How it is built: `analysis/architecture.md` (keep it up to date when the architecture
changes). How to build and run: `README.md`.

## Ground rules

* Don't commit or push unless asked. The work so far is uncommitted on `main`.
* Write code like the surrounding code: comment density, naming (domain language, full words),
  idioms. Javadoc and comments explain *why*, in plain English, briefly.
* Verify against real servers when behaviour of OpenLDES is involved; many assumptions turned out
  wrong (see "Verified behaviour of OpenLDES" below).
* Finish with all builds green: `./mvnw verify` (backend + frontend unit tests + integration tests)
  and, for frontend changes, `pnpm build`, `pnpm test`, `pnpm test:e2e` in `frontend/`.

## Stack

* Backend: Java 25, Spring Boot 4.1.2 (Jackson 3 = `tools.jackson`, JUnit 6, `RestClient`,
  `MockMvcTester`, `RestTestClient`, `@MockitoBean`), Flyway, Postgres, Apache Jena 6.2.0,
  Testcontainers 2 (`org.testcontainers.postgresql.PostgreSQLContainer`), ArchUnit, Lombok only in
  JPA entities. Modules with Mockito load it as a Java agent (`maven-dependency-plugin` `properties`
  goal + `-javaagent:${org.mockito:mockito-core:jar}` in surefire/failsafe `argLine`).
* Boot 4 package moves: `org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest`,
  `org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest`,
  `org.springframework.boot.persistence.autoconfigure.EntityScan`,
  `org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient`,
  `org.springframework.boot.test.web.server.LocalServerPort`.
* Frontend: Vue 3.5, TypeScript 5.9, Vite 7, PrimeVue 4.5 (Aura, teal primary, dark mode class
  `.app-dark`), Vue Flow 1.48, CodeMirror 6, N3.js, `yaml`, Chart.js, Pinia 3, vue-router 4 (HTML5
  history), Jest 29 (+ `@vue/vue3-jest`, ts-jest), Cypress 13.17, pnpm 9.15.3.
* Local instances: `openldes/ldes-server:4.2.1` (+ `postgres:16-alpine`),
  `openldes/ldi-orchestrator:3.1.1`, started through the Testcontainers API.

## Architecture (hexagonal, one Maven module per ring)

```
model        domain records with invariants (plain Java)
application  port.in (use cases + exceptions), port.out, service (@ApplicationService, plain Java)
adapter      in.rest (controllers, package-private), in.scheduling (monitor),
             out.persistence.jpa | ldes | ldio | health | runtime | http
frontend     Vue app; Maven builds it and packages dist/ as static/ in a jar
bootstrap    Spring Boot app: wiring (ApplicationServiceConfig scans @ApplicationService),
             SecurityConfig, SchedulingConfig, FrontendConfig (serves the frontend), application.yaml
```

* `DependencyRuleTest` (ArchUnit) enforces: inner rings framework-free; adapters independent;
  inbound adapters use use cases, never services; outbound adapters only implement ports.
* Errors: `RemoteInstanceException(Reason NOT_FOUND|REJECTED|UNAVAILABLE)` from outbound adapters;
  `RestExceptionHandler` maps to RFC 9457 problem details (404/409/400/502/503).
* State LDES-admin stores: instances, activity events, ingest samples (Flyway `V1`, `V2`).
* `ActivityRecorder` records actions (never fails the action); `InstanceMonitorService` runs every
  minute (health transitions, throughput samples kept 48 h) and on startup removes local instances
  whose container is gone.

### Key domain concepts

* `Instance`: REGISTERED (known by URL) or LOCAL (container started by LDES-admin, has
  `runtimeReference` and `internalUrl`). `reachable(iri)` translates IRIs a local server publishes
  on its internal URL to the URL LDES-admin reaches; `serves(url)` guards fragment fetching (no
  open proxy).
* Sandbox: local "Sandbox LDES server" + "Sandbox LDIO" on one Docker network. Host names come from
  the instance name (`sandbox-ldes-server`, `sandbox-ldio`). Seeded idempotently by
  `SandboxService`: event stream `sandbox-parcels` (views `by-page`, `by-time`), LDIO pipeline
  `sandbox-ingest` (HttpIn + RdfAdapter → HttpOut to
  `http://sandbox-ldes-server:8080/sandbox-parcels`). Frontend templates and component defaults
  point there.

## Frontend

* `src/api/LdesAdminApi.ts` is the port; `http/HttpLdesAdminApi` (backend, `/api/v1`) and
  `mock/MockLdesAdminApi` (sample data that behaves like the backend, incl. error messages and the
  sandbox). `api/index.ts` exports a proxy `api` that forwards to the current mode.
* Demo/production switch in the top bar (`stores/mode.ts`): remembered in localStorage
  (`ldes-admin.mode`); without a choice, production when `/actuator/health` answers. Switching
  re-keys the RouterView and reloads stores.
* Sandbox editor: `stores/sandbox.ts` (graph, simulation), `pipeline/graph.ts` (graph ⇄ pipeline,
  validation; `valid` blocks deploy, `runnable` only structural errors block simulation),
  `pipeline/catalog.ts` (every LDIO component and its typed properties: type, required, default,
  options, group, `when`, cross-property `check`), `pipeline/config.ts` (read/write/validate
  config; keys matched like LDIO does), `components/sandbox/ComponentConfigForm.vue` + `ConfigField.vue`
  (form kept in step with the component YAML), `pipeline/simulator.ts` (in-browser approximation).
* Patterns: `useAsync`/`useAction` composables; `data-cy` attributes for Cypress;
  `cy.dialogOpened(dataCy)` before typing in a PrimeVue dialog; Cypress pins demo mode in
  `cypress/support/e2e.ts`; e2e for production mode stubs the backend with `cy.intercept`.
* Charts follow the dataviz rules: validated palette (light #2a78d6 #eb6834 #1baf7a, dark #3987e5
  #d95926 #199e70), legend + non-colliding end labels, crosshair tooltip, table view.

## Commands

```shell
# JAVA_HOME must point to a JDK 25 (this machine: ~/.sdkman/candidates/java/25.0.4-tem)
./mvnw install -DskipTests                 # everything, incl. the frontend (downloads Node into frontend/node)
./mvnw -pl bootstrap spring-boot:run       # http://localhost:8080 (frontend on /, API on /api/v1), Postgres from compose.yaml
./mvnw install -DskipTests -Dfrontend.skip # backend only
./mvnw verify                              # all tests, needs Docker
cd frontend && pnpm dev                    # :5173, proxies /api and /actuator to :8080 (LDES_ADMIN_BACKEND)
pnpm build | pnpm test | pnpm test:e2e     # type-check+build | Jest | Cypress on vite preview :4173
```

After running the app, stop it and `docker compose stop` (compose Postgres was not running before).

## Environment gotchas (this machine)

* pnpm lives in `$HOME/.nvm/versions/node/v21.5.0/bin`; it runs scripts with Node 24.21.0 via
  `use-node-version` in `frontend/.npmrc`. Node 24 is in `$HOME/.nvm/versions/node/v24.21.0/bin`.
* GNU sed: use `sed -i.bak … && rm *.bak`, not `sed -i ''`. zsh: quote globs; `echo` interprets
  `\n` in strings (use `printf '%s'`, e.g. for JSON); no `timeout` command.
* Docker images above are cached locally. `/tmp/ldesserver-src` and `/tmp/ldi-src` hold the
  OpenLDES sources and docs (may be gone after a reboot).
* vue-tsc: a static attribute value containing `//` (e.g. `placeholder="https://"`) breaks
  type-checking of the surrounding `v-for`; bind it (`:placeholder="…"`).
* jsdom (Jest) lacks `structuredClone` (use JSON copy / `cloneConfig`), `crypto.randomUUID` and
  `matchMedia` (polyfilled in `tests/unit/setup.cjs`).
* Spring's test HTTP client decompresses transparently; check `Content-Encoding` with the JDK
  `HttpClient`. Jest prints to stderr, so the Maven log shows its summary as `[ERROR]` (cosmetic).

## Verified behaviour of OpenLDES (LDES server 4.2.1, LDIO 3.1.1)

* LDIO's `Ldio:LdesClient` cannot follow relative IRIs: local LDES servers run with
  `LDESSERVER_HOSTNAME=http://<host name>:8080` (no `use-relative-url`).
* LDES server: fragmentation every 30 s by default; local servers use
  `LDESSERVER_FRAGMENTATIONCRON=*/5 * * * * *`. Pages are immutable via
  `Cache-Control: …immutable`; members hang off `tree:member` on the event stream subject;
  time-based buckets (`?year=…&month=…&day=…`) link to their own pages (`&pageNumber=1`);
  relations can be repeated; `tree:value` like `"2026-10-24T00:00"^^xsd:dateTime` (no seconds,
  parse without Jena warnings).
* Metrics (`/actuator/prometheus`): `ldes_server_ingested_members_count`,
  `ldes_server_bucket_members_count`, `ldes_server_pagination_members_count`,
  `spring_batch_step_seconds_{count,sum,max}` and `spring_batch_step_active_seconds_count`
  (labels `spring_batch_step_job_name`, `spring_batch_step_name`, `spring_batch_step_status`).
  Jobs: `fragmentation` (bucketisation, pagination), `maintenance` (viewRetentionStep,
  eventSourceRetentionStep, compactionStep, deletionStep, single* worker steps).
* DCAT: `POST /admin/api/v1/dcat` takes the catalog as a blank node and returns its id as plain
  text; `GET /admin/api/v1/dcat` names the catalog after the server base URL, has
  `dct:identifier`, and lists datasets/data services only when they have DCAT.
* LDIO matches config keys ignoring case, `-` and `_` and returns them normalised
  (`membertype`, `rdfwriter.contenttype`). Deleting a pipeline returns 202 (wait until gone).
  `GET /admin/api/v1/pipeline/ldes-client` → `[{pipeline, status}]`. `POST /{pipeline}` feeds an
  `Ldio:HttpIn` pipeline (any content type its adapter reads).
* `Ldio:JsonToLdAdapter` needs an inline context wrapped as `{"@context": {...}}`;
  `Ldio:GeoJsonToWktTransformer` needs the geometry in the GeoJSON-LD vocabulary;
  `Ldio:VersionObjectCreator` only takes an `xsd:dateTime`-typed literal as observed time.

## Next steps (open)

* Authentication in the frontend (OIDC) for when `ldes-admin.security.enabled=true`; until then the
  served frontend loads but its API calls get 401.
* Showing what a deployed sandbox pipeline does step by step (capture endpoint in LDES-admin).
