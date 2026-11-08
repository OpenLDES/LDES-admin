# Controllers and API contracts

Controllers live in `adapter.in.rest`, grouped by domain area, and are generally package-private.
They bind and validate HTTP input, call input ports and map domain results to web models.
They do not perform persistence, remote HTTP calls or business orchestration.

Use the existing request records, `*WebModel.fromDomain` factories and use-case command records.
For a new endpoint, follow a nearby controller with similar content types and lifecycle semantics.
Do not add a DTO layer where the current boundary already has a suitable record.

## Contract ownership

The implemented contract is expressed by controllers, their tests and
[LdesAdminApi.ts](../../frontend/src/api/LdesAdminApi.ts). No checked-in OpenAPI contract currently
drives generation. Do not claim one was updated or introduce one as an incidental change.

Endpoints are rooted at `/api/v1`. Use the existing collection names, identifier handling,
response shapes and statuses. Registration returns `201` with a `Location` header; unregistering
returns `204`. Do not wrap existing list responses in an invented pagination envelope or rename
`list` methods to `search` to match a generic guide.

RDF endpoints and pipeline ingestion are not all JSON. Preserve accepted and returned content
types, raw RDF bodies and `RdfDocument` format selection. Reject invalid requests at the
appropriate boundary without converting upstream failures to successful empty results.

## Changing a contract

1. Describe the request, result, statuses, validation and compatibility impact.
2. Update the input port/domain types and controller mapping as required.
3. Keep `LdesAdminApi`, `HttpLdesAdminApi` and `MockLdesAdminApi` consistent, including errors.
4. Update affected stores/components and the user-facing documentation.
5. Cover the wire shape and failure mapping in MVC tests and affected frontend tests.

Errors belong in `RestExceptionHandler`, not controller-local handlers. See
[exceptions.md](exceptions.md). Authentication/filter failures are a separate Spring Security
boundary; do not assume they pass through MVC advice.

Relevant examples: `adapter/.../in/rest/instance/InstanceController.java`,
`adapter/.../in/rest/sandbox/SandboxController.java` and their controller tests.
