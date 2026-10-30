# Java style

## Follow the code that exists

Use Java 25 and the versions managed by [the root POM](../../pom.xml). Production Java under
`org.openldes.ldesadmin` uses two-space indentation, explicit imports, descriptive domain names
and short methods. Google Java Format is the authoritative formatter, run through Spotless in
Maven. Use its standard `GOOGLE` style (two-space indentation), not AOSP or manual wrapping rules.
The plugin and formatter versions are pinned in the root POM and inherited by every module.

## Formatting commands

Run from the repository root with JDK 25:

```shell
./mvnw spotless:apply   # Format production and test Java sources in every module
./mvnw spotless:check   # Check formatting without changing files
```

The check also runs in Maven's `validate` phase, so `test`, `verify` and existing CI builds reject
unformatted Java. Applying formatting is explicit; builds never rewrite sources automatically.
Generated sources under `target/` and non-Java files are outside the configured scope.

For a focused change, use `./mvnw -pl <module> spotless:apply` or Spotless's regular-expression
selector, for example `./mvnw -pl model '-DspotlessFiles=.*InstanceId[.]java' spotless:apply`.
Avoid unrelated formatting churn
after the initial repository-wide adoption. IDE formatting is optional; configure a matching
Google Java Format version/style and use Maven as the final authority.

Prefer records for immutable values, commands and results. Put domain invariants in compact
constructors or domain factory methods; callers should not be able to construct an invalid
instance. Use existing value types such as `InstanceId` and `RdfDocument` rather than flattening
them into untyped strings internally.

Use explicit constructor injection and `private final` dependency fields. Lombok is restricted
to JPA entities. Explicit local types are common in production and tests; follow the enclosing
file rather than imposing a new `var` or `final` policy. Do not invent a three-parameter limit
or introduce builders merely to satisfy one.

Prefer guards and small named helpers over deep nesting. Reuse shared helpers, but do not extract
an abstraction for a single trivial expression. Document only non-obvious intent, compatibility
constraints or invariants. Short Javadoc explaining why is welcome where the code already uses it.

## Boundaries and dependencies

`model` and `application` must remain free of Spring, Jakarta, Jackson, Lombok, Jena, LDI,
rdf4j, carml and Testcontainers. Use JDK facilities, domain types and ports there. Inject a
`Clock` for time-dependent behaviour as existing services do.

Use Jackson 3 (`tools.jackson`) at the adapter boundary; do not import Jackson 2 APIs by habit.
Use Spring `RestClient`, not a new HTTP library, for integration code. Framework annotations
and transaction placement are covered in [annotations.md](annotations.md).

Before adding a dependency, inspect the relevant module POM and dependency management.
LDI 3.1.1 libraries currently require a local Maven installation (see the root README);
do not mask missing dependencies by disabling the sandbox or silently changing versions.
Do not import `jena-bom` or `rdf4j-bom` without resolving their Spring 5 management conflict.

## Examples to follow

* `model/.../instance/Instance.java`: value validation, registered/local instances and URL
  behaviour.
* `application/.../service/instance/RegisterInstanceService.java`: plain Java orchestration.
* `application/.../service/instance/RegisterInstanceServiceTest.java`: constructor-based test setup.

Here and in other guides, `module/.../` abbreviates that module's
`src/main/java/org/openldes/ldesadmin/` package path, or `src/test/java/` for test classes.
