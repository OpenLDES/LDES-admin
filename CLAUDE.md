# LDES-admin: Claude entry point

Read [AGENTS.md](AGENTS.md) first and follow its task routing table. Shared conventions live in
[docs/ai/](docs/ai/README.md), so Claude and Copilot use the same application-specific guidance.

The former long-form context is now organised by topic:

* [Architecture](docs/ai/layered-architecture.md) and
  [_analysis/architecture.md](_analysis/architecture.md): hexagonal modules and ownership.
* [Frontend](docs/ai/frontend.md): API parity, editors, simulation and browser conventions.
* [OpenLDES behaviour](docs/ai/openldes.md): versioned integration observations and LDI shims.
* [Testing](docs/ai/testing.md) and [local environment](docs/ai/local-environment.md):
  commands, prerequisites and workstation-specific caveats.
* [Security](docs/ai/security.md): URL access, authentication limitations and real side effects.
* [Agentic workflows](docs/ai/agentic-workflows.md): scope, optional delegation and handoffs.

Never modify `_analysis/README.md`. Do not commit or push unless explicitly asked.
Do not treat unimplemented OIDC frontend login or live sandbox input execution as existing
features; their current limitations are documented in the relevant guides.
