# Observability and monitoring

## What exists

LDES-admin exposes its Actuator `health` and `info` endpoints by default. It also reads health
and Prometheus text metrics from managed LDES servers and LDIOs. These are separate surfaces:
parsing a remote `/actuator/prometheus` response does not mean LDES-admin exposes Prometheus.

`InstanceMonitorScheduler` invokes the monitoring input port. `InstanceMonitorService` checks
health every minute by default, records transitions and collects ingest samples retained for
48 hours. It removes stale local-instance registrations on startup when their container is gone.
Use the configured interval and injected clock; avoid hard-coded sleeps or duplicate pollers.

Remote metrics are parsed by shared HTTP helpers and mapped by LDES/LDIO outbound clients.
Preserve metric names and labels, counters versus gauges, units, reset behaviour, and the
distinction between an unavailable observation and a genuine zero.
See [openldes.md](openldes.md) for verified remote metric names.

The frontend's health, throughput, statistics and background-process views depend on these
semantics. Changing calculations requires matching mock data, chart/table behaviour and tests.

## HTTP and concurrent work

Use the injected `RestClient.Builder` and existing `InstanceRestClients` helpers rather than
constructing unrelated static clients. Preserve shared timeouts and error translation.
Do not cache an unreachable internal URL where an instance's reachable URL is needed.

Sandbox work and local startup may use virtual threads; component calls are bounded by the
sandbox timeout. Preserve lifecycle cleanup and per-session component state. A timeout or failed
poll must remain visible; do not turn it into a healthy state or an empty successful result.

## What is not configured

There is no established Micrometer/OpenTelemetry tracing bridge, exporter, trace-ID problem
extension, MDC task decorator or verified JWT user-ID logging convention in this repository.
Do not document them as existing or add dependencies for them as incidental cleanup.

If tracing becomes a requested feature, design its scope explicitly: incoming/outgoing
propagation, security/filter errors, asynchronous context, privacy and tests. Prefer standard
W3C Trace Context and supported instrumentation over hand-built client-supplied log identifiers.
