# Review and verification handoff: <change>

Template only. A review is not proof that checks ran.

## Scope

Request/specification: <reference>.

Diff/base reviewed: <working tree or commit range>.

Changed paths and boundaries: <actual paths; mention demo/production parity where relevant>.

## Findings

| Priority | Path and lines | Observable failure and evidence | Suggested correction | Confidence |
|----------|----------------|---------------------------------|----------------------|------------|
| <level> | <path:lines> | <reproducible issue, not a style preference> | <bounded fix> | <level> |

If none were found, remove the placeholder row and state that explicitly. Distinguish verified
bugs from questions; do not invent findings to fill the table.

## Verification

| Command/check | Outcome | Evidence or blocker |
|---------------|---------|---------------------|
| <exact command and working directory> | <passed / failed / not run / blocked> | <exit/result or actionable blocker> |

## Remaining work

Unverified behaviour or required follow-up: <specific item or none>.

Resources started and cleanup: <task-owned processes/containers or none>.

Human decisions/authorization needed: <specific decision or none>.
