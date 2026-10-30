# AI-assisted workflow

The main session owns scope, decisions, integration and the final handoff. Agents assist;
they do not authorize deployment, alter product requirements or bypass review.

## Normal change flow

1. Inspect `git status`, relevant code/tests and the matching guides from `AGENTS.md`.
   Preserve existing work and identify the observable outcome.
2. For a substantial or ambiguous change, write a short task-local specification with scope,
   decisions, affected contracts and acceptance examples. Use the
   [templates](templates/change-spec.md); do not create tracked planning files by default.
3. Settle contract changes before implementation. This repository's contract is controllers,
   tests and `LdesAdminApi`, not a generated OpenAPI document.
4. Implement a coherent path through domain/ports, adapters and clients. Keep demo and production
   parity and update user documentation for visible changes.
5. Run focused tests during iteration, then the relevant final gates in [testing.md](testing.md).
   Verify remote assumptions with the actual supported server images.
6. Review the diff for correctness, unintended changes, credentials and architecture violations.
   Report the result and any remaining uncertainty. Commit only when explicitly requested.

A small change needs no spec, roster or delegation. Avoid splitting a continuous investigation
among agents simply to make it look parallel. Reuse context and batch independent reads.

## Optional custom agents

The repository includes four manually selectable personas in `.github/agents/`.
They inherit the same root instructions and topic guides; no fixed model/provider is required.

| Persona                | Bounded responsibility                                             | Expected handoff                                              |
|------------------------|--------------------------------------------------------------------|---------------------------------------------------------------|
| `feature-planner`      | Read-only scope, affected boundaries, decisions and test plan      | Proposed plan, acceptance examples, unresolved questions      |
| `feature-implementer`  | Implement an agreed scope and its tests; no commit/push            | Changed paths, behaviour, checks and blockers                 |
| `code-reviewer`        | Read-only review of the diff against the request and architecture  | Actionable findings with paths, lines and confidence          |
| `quality-gates-runner` | Run applicable existing checks; no fixes or application deployment | Exact command, pass/fail/blocked and concise failure evidence |

Invoke only the role that is useful for the task. There is no automatic orchestrator or mandatory
chain. Custom agent support and tool permissions depend on the client; if unsupported, work
inline or use the persona as a prompt. Read-only personas must not edit files or run commands
with side effects. Verifiers may create normal ignored build outputs, not modify tracked source.

Give delegated work a complete bounded prompt: objective, relevant paths, accepted decisions,
allowed edits/commands, expected output and a stopping condition. Delegates must not spawn
other agents or make product decisions. Reviewers report findings; the implementation owner
fixes them. Do not have multiple agents edit the same files concurrently.

Use a specialist security review when the requested scope warrants one, not on every prose
change. Extra personas, context files and expensive parallel reviews are not prerequisites.

## Safety and blocked work

Read-only inspection and existing local tests/builds are normal operations. Publishing,
releasing, pushing, destructive database/container actions and changes to real remote data
need explicit authorization. A request to edit CI is not permission to trigger a release.
Never commit or push solely because checks passed.

Ask before irreversible actions or material scope/behaviour decisions with no established
answer. For routine implementation choices, follow existing patterns and state consequential
assumptions. If the required tool, Docker or LDI artifact is unavailable, report the exact blocker
and unexecuted checks; do not disable functionality or fake a passing result.

Stop processes started for the task and clean up temporary task files. Do not stop services
that were already running. Store durable task notes in the tool's task workspace unless the
user explicitly wants them versioned.
