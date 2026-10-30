# OpenLDES integration and verified behaviour

These observations were verified for **LDES server 4.2.1 and LDIO/LDI 3.1.1**, or read in their
matching upstream sources where noted. They are compatibility evidence, not promises about
future releases. Recheck against real servers when changing integration behaviour; mocks and
the browser simulator alone cannot establish compatibility.

The configured image versions live in
[application.yaml](../../bootstrap/src/main/resources/application.yaml); LDI/Jena versions live
in [pom.xml](../../pom.xml). Real-server coverage includes `RestLdesServerClientIT`,
`RestLdioClientIT` and bootstrap `SandboxIT`.

## Local instances and the prepared sandbox

LDIO's `Ldio:LdesClient` cannot follow relative IRIs. Local LDES servers therefore publish
`LDESSERVER_HOSTNAME=http://<host name>:8080`, not relative URLs. Hostnames derive from instance
names. `Instance.reachable` translates published internal IRIs to reachable URLs; `serves` guards
fragment fetching.

`SandboxService` idempotently prepares a local "Sandbox LDES server" and "Sandbox LDIO" on one
Docker network. It creates `sandbox-parcels` with views `by-page` and `by-time`, and pipeline
`sandbox-ingest`: HttpIn + RdfAdapter -> HttpOut to
`http://sandbox-ldes-server:8080/sandbox-parcels`. Starting it again must not create duplicates;
the pipeline is redeployed if its target server changed. Frontend defaults/templates use these
names and URLs.

## LDES pages, metrics and DCAT

* Fragmentation normally runs every 30 seconds; local instances use
  `LDESSERVER_FRAGMENTATIONCRON=*/5 * * * * *`.
* Immutable pages advertise `Cache-Control: ...immutable`. Members are `tree:member` statements
  on the event stream subject, not necessarily the page subject.
* Time buckets (`?year=...&month=...&day=...`) link to their own pages (`&pageNumber=1`).
  Preserve repeated relations. A `tree:value` date-time may omit seconds, for example
  `"2026-10-24T00:00"^^xsd:dateTime`; parse it without spurious Jena warnings.
* Remote `/actuator/prometheus` metrics include `ldes_server_ingested_members_count`,
  `ldes_server_bucket_members_count`, `ldes_server_pagination_members_count`,
  `spring_batch_step_seconds_{count,sum,max}` and `spring_batch_step_active_seconds_count`.
  Batch labels are `spring_batch_step_job_name`, `spring_batch_step_name` and
  `spring_batch_step_status`.
* Batch jobs are `fragmentation` (bucketisation, pagination) and `maintenance`
  (viewRetentionStep, eventSourceRetentionStep, compactionStep, deletionStep and single-worker
  steps). Preserve units and step identities when parsing metrics.
* `POST /admin/api/v1/dcat` accepts the catalog as a blank node and returns its identifier as
  plain text. `GET` names the catalog after the server base URL, includes `dct:identifier`, and
  lists datasets/data services only where they have DCAT descriptions.

## Definitions and SHACL

The admin API derives a name from the subject IRI's Jena local name: collection `2026-data`
becomes `data`; view `2026` yields an empty name and breaks view listing. Do not assume arbitrary
URL path segments are safe names.

Event stream definitions require `tree:shape` referring to a `sh:NodeShape` (a blank node is
valid), plus IRI-valued `ldes:timestampPath` and `ldes:versionOfPath`.
`ldes:createVersions` must be `xsd:boolean`; `ldes:versionDelimiter` applies only when it is true,
and `ldes:skolemizationDomain` must be an IRI. Statements on unreferenced subjects are rejected
by the closed shape. An invalid event-source retention policy can return `400`/`500` **after the
stream has been created**. `ldes:kafkaSource` without the Kafka module returns `500`.
Do not blindly retry creation after such a failure without checking state.

Views support at most one fragmentation in the editor even though the remote API accepts several
with `201`. `tree:fragmentationStrategy` is mandatory even when its value is the empty list `()`.
Unknown fragmentation types can be silently accepted; successful HTTP status alone does not
prove the intended strategy exists. There is at most one retention policy of each type
(`ldes:DurationAgoPolicy`, `ldes:LatestVersionSubset`). The server accepts page size zero and
ignores unknown predicates.

SHACL datatypes are exact:

| Property                          | Required form                              |
|-----------------------------------|--------------------------------------------|
| `tree:maxZoom`                    | `xsd:integer`, 0 through 23, not `xsd:int` |
| `ldes:amount`                     | `xsd:integer`, at least 1                  |
| `tree:value` in duration policies | `xsd:duration`; `P1W` is rejected          |
| `tree:maxGranularity`             | `xsd:string`, year through second          |
| `tree:fragmentationKey`           | `xsd:string`                               |

Time fragmentation reads `tree:fragmentationSubjectFilter`, despite different spellings in the
docs/shape. Geospatial fragmentation reads `tree:fragmenterSubjectFilter`. Reference defaults
are path `rdf:type` and key `reference`.

## Ingest

With `createVersions=false`, one request holds exactly one member: one named subject, other
resources only as objects, with blank nodes scoped to that member. The timestamp must be
`xsd:dateTime` or a string. A shape supplied during stream creation is not enforced immediately;
it is enforced after `PUT .../shape` or a server restart. SHACL sequence paths such as
`( sosa:hasResult qudt:numericValue )` work.

## LDIO components and administration

* Configuration keys match ignoring case, `-` and `_`, and are returned normalized, for example
  `membertype` and `rdfwriter.contenttype`. Reuse the existing normalization helpers.
* Pipeline deletion returns `202`; wait until the pipeline is gone before recreating it.
  LDIO has no direct update operation; LDES-admin implements replacement.
* `GET /admin/api/v1/pipeline/ldes-client` returns `[{pipeline, status}]`.
  `POST /{pipeline}` feeds HttpIn using a content type its adapter supports; HttpIn returns `202`.
* JsonToLdAdapter makes one model per JSON array object. Its inline context must be wrapped as
  `{"@context": {...}}`. JSON-LD numeric coercion to `xsd:double` canonicalizes lexical forms,
  for example `82` to `"8.2E1"`.
* RmlAdapter makes a model per named graph (`rr:graphMap`), not necessarily one for the entire
  input.
* GeoJsonToWktTransformer expects geometry in the GeoJSON-LD vocabulary.
* VersionObjectCreator selects the first subject of a configured member type and requires an
  `xsd:dateTime` observed-time literal. It uses that literal's lexical form in the version IRI;
  use `/` as delimiter when state IRIs contain `#`.
* The Ghent parking API used in the example returned 14 car parks and allowed CORS
  (`Access-Control-Allow-Origin: *`). This is external, changeable data, not a hard-coded count.

From LDIO 3.x source: HttpInPoller's Spring `cron` wins over deprecated `interval`
(`java.time.Duration`, fixed rate starting immediately). Each tick polls URLs in order. A non-2xx
response or missing `Content-Type` fails that poll. Requests include `http.headers`, an API key
(default header `X-API-KEY`) or an OAuth2 client-credentials token, with client ID/secret in the
token request form body.

## Sandbox polling and runtime compatibility

`PollUseCase` -> `PollService` -> `HttpPoller` / `RestHttpPoller` makes GET requests without an
Accept header, with component headers/credentials, a required response Content-Type and a
5 MB bound. It translates local internal URLs. `PollException` maps to `502`. Arbitrary HTTP (S)
targets are intentional; read [security.md](security.md) before changing this proxy boundary.

`RunPipelineService` fans each model through transformers and outputs like LDIO.
`ComponentRuntime` / `LdiComponentRuntime` runs LDI's core libraries, not Boot 3 LDIO connectors.
Components are configured per session and position, preserving change-detection state; every call
is bounded by `ldes-admin.sandbox.component-timeout`. Step outputs contain at most 100 models.

`LdioConfig` flattens nested maps to dotted keys and lists to `key.0`, matching LDIO's
`FlattenDeserializer`. It does not read queries or mappings from arbitrary host files.
Outputs are dry unless `executeOutputs`; Kafka, AMQP and RDB always remain dry.
HTTP Out serializes JSON-LD without the frame (`writeToOutputStream`); console/Kafka/AMQP use it.
A failed HTTP enricher request passes the model on.

LDI was built on Jena 5. Under this project's Jena 6, its `SparqlConstructTransformer` and carml
`JenaConverters` call removed methods; carml's streaming JSONPath parser (jsurfer/ANTLR 4.7)
conflicts with ANTLR 4.13 used by Hibernate/Spring Data. The sandbox adapter therefore has its
own `SparqlConstruct` (registering LDI functions) and `RmlRecordAdapter` (JSON record and
rdf4j-to-Jena conversion). Do not remove these compatibility shims without real runtime coverage.

The sandbox does not yet execute inputs such as an LDES client following a live event stream.
Do not describe the existing single-input execution as a full running LDIO deployment.
