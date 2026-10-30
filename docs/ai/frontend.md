# Frontend conventions

The frontend is Vue 3 with TypeScript, Vite, PrimeVue, Vue Flow, Pinia, vue-router, CodeMirror,
N3.js, YAML and Chart.js. Read [package.json](../../frontend/package.json) and the lockfile for
current versions rather than relying on an old environment summary. Maven packages `dist/`
as static resources; bootstrap serves the SPA and its history routes.

## API and mode parity

`frontend/src/api/LdesAdminApi.ts` is the frontend port. `HttpLdesAdminApi` talks to `/api/v1`;
`MockLdesAdminApi` provides demo data and equivalent operations/errors. The exported `api`
proxy in `api/index.ts` forwards to the active implementation. Components should use the shared
API abstraction rather than adding direct backend calls.

`stores/mode.ts` remembers the choice in `ldes-admin.mode`. With no stored choice it uses
production when `/actuator/health` answers. Switching re-keys the router view and reloads stores.
Preserve this behaviour and the demo-only build, which must not call the backend.

Every contract change needs the port, HTTP implementation, mock implementation and affected
consumers updated together. Demo sandbox execution is an approximation, not proof of real LDIO
compatibility. Test both modes where behaviour differs.

## Sandbox editor

* `stores/sandbox.ts` owns graph state, input fetching, polling and simulation sessions.
* `pipeline/graph.ts` converts graph and pipeline definitions and validates them. `valid` gates
  deployment; `runnable` blocks simulation only for structural errors. Preserve the distinction.
* `pipeline/catalog.ts` defines typed component properties: required/default/options/group,
  conditional `when` and cross-property `check`. `pipeline/config.ts` reads, writes and validates
  configuration with LDIO-compatible key matching.
* `ComponentConfigForm.vue` and shared `ConfigField.vue` keep forms in step with YAML.
  Do not maintain a second independent property schema in a dialog.
* `pipeline/simulator.ts` is the demo engine. Production uses `api.runPipeline` and backend LDI
  components. Update both surfaces when a supported component's contract changes.
* `pipeline/poller.ts` and `cron.ts` handle URLs, requests and Spring cron. Cron wins over interval;
  interval polls immediately. URLs run in order, then the store runs the pipeline.

`SimulationPanel` stops polling on unmount. Loading/clearing renews the session. A run sends
`{pipeline, input, session, executeOutputs}`; step `outputs` and result `members` are arrays of
serialized models, not a single combined graph. Keep the output pager and dry-run semantics.
Demo polling uses browser `fetch` and remains subject to CORS.

## RDF forms and examples

The event stream/view form and Turtle editor edit one graph via `useTurtleDraft.ts`.
`ldes/turtle.ts` handles N3 parsing, graph edits and nested blank-node/list output;
`catalog.ts` describes predicates; `definition.ts`, `eventStream.ts` and `view.ts` read, write and
validate definitions. Do not replace semantic graph edits with string substitutions.

Validation errors block creation. The view template chooses its fragmentation; the form edits
properties but does not add/remove/swap the strategy. Pagination and recent-members presets
have no fragmentation.

`examples/sensors.ts` provides SOSA/SSN observations; `parking.ts` maps polled Ghent parking data
to Mobivoc. `examples/install.ts` installs streams, views, pipelines and data idempotently through
the API, skipping existing resources. Preserve that when changing starter dialogs.
`exampleFor(stream)` supplies suitable members for ingestion. `ldes/formats.ts` converts Turtle,
JSON-LD, N-Quads and N-Triples; `ldes/jsonld.ts` supports inline contexts only. Sandbox input
conversion follows its content type.

## UI and browser conventions

Reuse `useAsync`/`useAction` and existing feedback/loading patterns. Use `data-cy` attributes for
stable browser selectors. PrimeVue uses Aura, teal primary and `.app-dark`; support both themes.
Charts use the established palettes (light: `#2a78d6`, `#eb6834`, `#1baf7a`; dark: `#3987e5`,
`#d95926`, `#199e70`), legends, non-colliding end labels, crosshair tooltips and a table view.
Keep controls labelled and keyboard accessible.

Bind static attribute values containing `//`, for example `:placeholder="'https://'"`, where
vue-tsc otherwise misparses the surrounding `v-for`. Jest's jsdom setup polyfills
`crypto.randomUUID` and `matchMedia`, but lacks `structuredClone`; use existing `cloneConfig`
or the established JSON-copy pattern for JSON-compatible data.

See [testing.md](testing.md) for build, Jest and Cypress gates.
