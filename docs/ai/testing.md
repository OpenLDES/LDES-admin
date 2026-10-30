# Testing and verification

Use existing runners and helpers, including Spotless with Google Java Format. Do not add a
Cucumber runner, test framework or mocking library to satisfy a documentation template.

## Test at the responsible boundary

| Change | Coverage |
|--------|----------|
| Domain values and invariants | Plain JUnit tests in `model` |
| Use-case orchestration | JUnit, Mockito and AssertJ in `application`; mock output ports |
| HTTP binding, validation, status or response shape | `@WebMvcTest`, `MockMvcTester`, `@MockitoBean` in `adapter` |
| Remote request construction/parsing | Focused outbound adapter tests |
| LDES/LDIO protocol behaviour | `RestLdesServerClientIT`, `RestLdioClientIT` against real container images |
| Schema/entity/port persistence | Postgres/Testcontainers with Flyway, e.g. `JpaInstanceRepositoryIT` |
| Wiring, security or SPA serving | Bootstrap tests and applicable `*IT` tests |
| Cross-ring dependencies | `bootstrap/.../DependencyRuleTest.java` |
| Vue utilities, stores and components | Jest in `frontend/tests/unit` |
| Browser workflows and mode parity | Cypress in `frontend/cypress/e2e` |

Tests mirror production packages. Existing Java tests use descriptive camelCase method names,
explicit local types, fixed clocks and small fixture helpers. Follow them; snake_case names and
given/when/then comments are not mandatory. Assert observable behaviour, failure paths and
absence of forbidden side effects, not implementation details.

JUnit 6, Mockito and AssertJ are already configured. Mockito modules load a Java agent through
`maven-dependency-plugin`'s `properties` goal and Surefire/Failsafe `argLine`. Preserve that setup
and JaCoCo's `argLine` integration. Boot 4 imports are in [annotations.md](annotations.md).

## Commands

Run Maven from the root with JDK 25. For focused iteration, select the affected module plus
prerequisites. Formatting is checked in `validate`, before compilation and tests.
Use `./mvnw spotless:apply` to fix formatting and `./mvnw spotless:check` for a standalone,
non-mutating check (see [Java style](java-style.md)). For example:

```shell
./mvnw -pl application -am -Dtest=RegisterInstanceServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
```

For code changes, the final project gate is:

```shell
./mvnw verify
```

It includes Java formatting checks, backend tests, frontend build/unit tests and integration tests.
Docker and locally installed LDI artifacts are prerequisites; see [README.md](../../README.md). Maven `test` alone
does not run Failsafe integration tests. `-DskipTests` and `-Dfrontend.skip` are iteration shortcuts,
not evidence that the final gate passed.

For frontend changes, also run in `frontend/`:

```shell
pnpm build
pnpm test
pnpm test:e2e
```

`build` includes `vue-tsc`; `test:e2e` builds with Vite and manages a preview server on port 4173.
Do not leave a second preview server occupying that port. To iterate on one Jest file,
`pnpm test --runTestsByPath tests/unit/<file>.spec.ts` selects a test after replacing the path
with a real file.

Cypress uses `data-cy` selectors and `cy.dialogOpened(dataCy)` before editing PrimeVue dialogs.
Demo mode is pinned in the support file. Production-mode browser tests stub the API with
`cy.intercept`; a JSON object containing `body` must be wrapped as `{ body: {...} }` to avoid
being interpreted as a Cypress StaticResponse.

## Documentation-only changes

No Java or frontend build is needed for prose-only changes. Check relative links and referenced
paths. If changing Jekyll content/configuration, run:

```shell
cd docs
bundle exec jekyll build --baseurl /LDES-admin
```

The generated site is `docs/_site/` and is not committed. Changes to AI entry points should also
check agent YAML front matter and ensure `docs/ai/` stays excluded from the generated site.
The acceptance template is illustrative; it is not an executed test suite.

Report actual commands and outcomes in verification handoffs. Distinguish an unavailable
dependency or Docker daemon from a test assertion failure; never report skipped checks as passed.
Stop only servers/containers started for the task, leaving pre-existing resources untouched.
