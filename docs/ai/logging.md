# Logging and activity history

The project has two distinct records of events: technical logs for operators and persisted
activity events shown in the UI. Do not substitute one for the other.

## Technical logs

Adapters such as `InstanceMonitorScheduler` and `DockerLocalInstanceRuntime` use explicit SLF4J
`LoggerFactory` loggers. Framework-free application classes such as `ActivityRecorder` and
`InstanceMonitorService` use `System.Logger`. Follow the local convention: do not introduce
Lombok `@Slf4j` or an SLF4J dependency into the inner rings.

Use parameterized messages in the logger's own syntax (SLF4J `{}`, `System.Logger` indexed
placeholders). Give enough context to identify the operation without logging request bodies,
RDF members, tokens, authorization headers, client secrets or credential-bearing URLs.
Avoid repetitive polling success logs and duplicate logging at each layer for the same failure.

Log a recoverable failure where recovery is owned. Propagate a normal failure through the
established exception path instead of logging and returning an empty result. There is no
repository-wide `[FEATURE] - ACTION` message format or automatic request-ID contract.

## User-visible activity

`ActivityRecorder` writes actions through the `ActivityLog` output port; the JPA adapter persists
them. Recording an activity must not fail the user's action. This is an intentional best-effort
boundary, not permission to swallow arbitrary service errors.

Lifecycle services record successful actions; monitoring records health transitions and samples
throughput. Preserve when activity is recorded and avoid recording a success before an operation
has actually completed. Use existing domain language so mock and production activity remain
understandable and comparable.

For changes affecting concurrent work or request correlation, read
[observability.md](observability.md); do not assume thread-local logging context propagates.
