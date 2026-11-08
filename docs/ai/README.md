# AI contributor guide

This is the repository's working example of AI-assisted development documentation, tailored to
LDES-admin rather than a generic Spring application. It documents existing conventions; it does
not add application features, install tools or configure an autonomous deployment service.

The organisation is inspired by
[Jose A. Lopez's article on AGENTS.md for Java and Spring Boot](https://josealopez.dev/en/blog/agents-md-java-spring-boot):
a short root router, detailed topic files and optional focused agents. The guidance here is
written for this codebase. In particular, the article's feature-layered services, general Lombok
usage and tracing setup are not adopted. Java formatting is enforced separately with Spotless
and Google Java Format; see [Java style](java-style.md).

## Structure

```text
AGENTS.md                          Shared starting point and task router
CLAUDE.md                          Claude entry point
.github/
  copilot-instructions.md           Copilot entry point
  agents/                          Optional, manually selected Copilot agents
    feature-planner.agent.md
    feature-implementer.agent.md
    code-reviewer.agent.md
    quality-gates-runner.agent.md
docs/ai/
  README.md                        This index and maintenance rules
  java-style.md                    Java idioms and dependency constraints
  annotations.md                   Framework placement and Boot 4 imports
  layered-architecture.md           Hexagonal modules, ports and persistence
  controllers.md                   HTTP boundary and client contract
  mappers.md                       Web/entity/domain conversion
  exceptions.md                    Failure semantics and problem details
  testing.md                       Test selection, commands and gates
  logging.md                       Technical logs versus activity history
  observability.md                 Health, metrics and scheduling
  frontend.md                      Vue, demo/production parity and editors
  openldes.md                       Verified LDES/LDIO behaviour and runtime
  security.md                       Trust boundaries and side effects
  agentic-workflows.md              Safe execution and optional delegation
  local-environment.md              Non-portable workstation notes
  templates/
    change-spec.md                 Scope and decisions for a larger change
    acceptance.feature             Concrete acceptance examples
    review.md                      Findings and verification handoff
```

## Using the documents

Start with [AGENTS.md](../../AGENTS.md) and follow its routing table. Copilot's repository
instructions and Claude's entry point both route there; agents without automatic discovery should
be given that path explicitly. Linked topic documents must be read when applicable: simply
mentioning them in a prompt does not guarantee a particular tool loads them.

Use the optional custom agents in clients that support `.github/agents/*.agent.md`. They are
personas with bounded responsibilities, not a workflow that runs automatically. Other clients can
use their instructions as prompts. No provider, model, hooks or extra permissions are required.

For a substantial feature, copy the [change specification](templates/change-spec.md),
[acceptance examples](templates/acceptance.feature) and, when needed,
[review handoff](templates/review.md) to a task workspace. Replace every placeholder before
handoff. These are templates, not approved requirements or executed tests. The `.feature` file
is a specification aid; the repository has no Cucumber runner.

Keep temporary plans and command output outside the tracked tree unless the user asks for a
versioned design document. The existing [_analysis/README.md](../../_analysis/README.md) remains
the product specification, not a place for AI-generated task notes.

## Maintenance

* Treat source, build manifests and tests as evidence of implemented behaviour. Flag a conflict
  with the product specification rather than silently rewriting either.
* Put shared rules in one topic file; keep `AGENTS.md`, `CLAUDE.md`, Copilot instructions and
  agent personas short. Avoid duplicating version numbers from manifests.
* When a convention changes, update the owning guide and any affected links or agent instructions.
  Keep observed upstream behaviour versioned in [openldes.md](openldes.md).
* Keep [_analysis/architecture.md](../../_analysis/architecture.md) current with architecture,
  [README.md](../../README.md) with setup, and the user-facing Jekyll pages with features.
* AI guides are excluded from Jekyll in [docs/_config.yml](../_config.yml); they remain readable
  in the repository. User documentation links remain relative; assets use `relative_url`.
* Check relative links, agent YAML front matter, and the Jekyll build when changing this setup.
  Instructions are guidance, not a security sandbox or an alternative to CI and human review.
