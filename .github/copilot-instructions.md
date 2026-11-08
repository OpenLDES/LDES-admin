# LDES-admin

Read [AGENTS.md](../AGENTS.md) before changing this repository, then follow its task routing table
and read the relevant guides under [docs/ai/](../docs/ai/README.md).

Preserve the framework-free `model` and `application` rings. Controllers call input ports;
outbound adapters implement output ports. Keep frontend HTTP and mock APIs consistent.
Do not modify `_analysis/README.md`. Do not commit or push unless explicitly requested.

Use the existing test/build commands in [docs/ai/testing.md](../docs/ai/testing.md). Never report
unexecuted checks as passing. Optional custom agents are documented in
[docs/ai/agentic-workflows.md](../docs/ai/agentic-workflows.md); they are not a mandatory workflow.
