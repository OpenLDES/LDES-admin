# Exceptions and failure semantics

Reuse the existing exception taxonomy instead of introducing a generic `AppException` and
global error-code enum. The inner rings express failures without Spring HTTP types.

`adapter.in.rest.RestExceptionHandler` extends Spring's `ResponseEntityExceptionHandler` and
maps application/integration failures to [RFC 9457 problem details](https://www.rfc-editor.org/rfc/rfc9457).

| Failure | Current HTTP status |
|---------|---------------------|
| `InstanceNotFoundException`, `PipelineNotFoundException` | `404` |
| `InstanceAlreadyRegisteredException`, `IllegalPipelineStateException` | `409` |
| `IllegalArgumentException` / invalid request | `400` |
| `RemoteInstanceException`: `NOT_FOUND` | `404` |
| `RemoteInstanceException`: `REJECTED` | `400` |
| `RemoteInstanceException`: `UNAVAILABLE` | `502` |
| `PollException` | `502` |
| `LocalRuntimeException` | `503` |

Translate HTTP/client exceptions inside outbound adapters into the output port's vocabulary.
Preserve the cause and useful, non-sensitive context. Do not leak Spring client types into
application contracts. Do not expose credentials or unrestricted remote response dumps in a
problem detail.

Sandbox component failures use `ComponentException` and are represented in run step results.
A failed non-output component stops that run; a failed output does not prevent the other outputs
from writing. This is different from treating the whole run as an unstructured HTTP failure.

Avoid broad catches, silent defaults and success-shaped fallbacks. Catch only where the project
has an explicit recovery contract. Important existing exceptions to fail-fast behaviour include
best-effort activity recording and an HTTP enricher passing its input on after a failed request.
Preserve and test those deliberately, rather than extending the pattern indiscriminately.

Do not claim all error paths already have a trace ID or a common security error body. JWT failures
occur in the security filter chain, outside MVC advice. A change to unify them is a separate
feature requiring security and client tests.
