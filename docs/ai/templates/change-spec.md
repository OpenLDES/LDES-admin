# Change specification: <short title>

Template only. Replace placeholders and keep this in the task workspace unless a versioned
design document was requested. Do not edit `_analysis/README.md`.

## Outcome and scope

Requested observable outcome: <what a user can do or what failure is resolved>.

In scope: <bounded behaviour and affected modules>.

Out of scope: <nearby work deliberately excluded>.

## Existing evidence

Relevant source/test paths: <actual paths and current behaviour>.

Relevant AI guides: <paths from AGENTS.md>.

Upstream assumptions: <server/image version, observed behaviour, evidence or verification needed>.

## Contract and design

Input/output: <request, result, validation, statuses, RDF content types where applicable>.

Domain and ports: <invariants, input/output ports and ownership>.

Adapters and wiring: <HTTP, persistence/Flyway, remote clients, sandbox or bootstrap changes>.

Frontend: <LdesAdminApi, HTTP/mock parity, stores, components and mode behaviour>.

Decision: <choice>. Why: <mechanism and tradeoff>. Alternative: <why not>.

Compatibility and side effects: <data migration, remote writes, dry runs, auth and URL boundaries>.

## Acceptance and verification

Acceptance examples: <link to the filled acceptance.feature or inline scenarios>.

Focused checks: <real tests/commands and what each proves>.

Final gates: <applicable commands from docs/ai/testing.md>.

Documentation updates: <README, architecture or user-facing pages; none if not needed>.

## Questions, risks and handoff

Unresolved decisions: <question, owner and whether implementation is blocked>.

Required authorization: <irreversible actions, remote writes, deployment; none by default>.

Implementation status: <not started / in progress / complete / blocked>.

Verification evidence: <actual commands and outcomes, or not yet run>.
