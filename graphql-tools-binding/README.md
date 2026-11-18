# micronaut-graphql-tools-binding

Gradle project `:micronaut-graphql-tools-binding`, folder `graphql-tools-binding/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-binding`.

## Purpose

The binding rules, in one place. The rules decide whether a GraphQL schema binds to a set of Java classes, which
findings a failed binding reports, and how the binding manifest is written. The runtime library (`graphql-tools`) uses
them at startup. The compile-time processor (`graphql-tools-processor`) reads the schema files with the same reader and
runs the per-class rules in its visitor (`ResolverClassFindings`, #150); the visitor replaces the JSR-269 code at #291.

## Responsibility

The module owns, under `io.micronaut.graphql.tools.binding.internal`:

- `schema/`: reading the schema files (`SchemaSources`), the schema model and the schema coordinates.
- `model/`: a model of Java classes, members, parameters and annotations, and the type references the rules use. The
  rules read this model, not the Micronaut inject API.
- `rules/`: the per-class rules (resolver classes, input types, enum types, arguments, return types).
- `plan/`: the binding plan, the resolver beans and the phases of the binding.
- `finding/`: findings and how they are rendered.
- `manifest/`: the binding manifest renderer and the schema hash.
- `meta/`: resolver contracts.

`io.micronaut.graphql.tools.binding.BindingCode` is the registry of the codes of the startup and compile-time failures.

Not in this module: the runtime wiring, the Micronaut bean lookup and the compile-time processor. Those use the rules
and stay in their own modules.

## Packages

Each package has a `package-info.java`. All packages below `internal` are implementation; see the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.binding` | Public API | `BindingCode` |
| `...binding.internal.finding` | Internal | Findings and their rendering |
| `...binding.internal.manifest` | Internal | The manifest renderer and the schema hash |
| `...binding.internal.meta` | Internal | The resolver contracts annotation |
| `...binding.internal.model` | Internal | The model of classes and members that the rules read |
| `...binding.internal.plan` | Internal | The binding plan and the bean model |
| `...binding.internal.rules` | Internal | The per-class rules and their verdicts |
| `...binding.internal.schema` | Internal | Reading the schema files and the schema model |

## Who uses it

Maintainers, and the two modules that depend on it. Application code does not use this module directly: applications
get it as a transitive dependency of `micronaut-graphql-tools`.

## Public and internal

- `io.micronaut.graphql.tools.binding.BindingCode` is the only top-level type outside `internal`. It is not annotated
  `@Internal`. The public status of the error codes is decided in #109.
- Every class under `io.micronaut.graphql.tools.binding.internal` is internal. Most are annotated `@Internal`;
  `internal/model/NoAnnotations` is not. Do not use any class of the `internal` packages from application code.

`BindingArchitectureSpec` fails when a class of this module imports an API of `io.micronaut.inject`. The module does not
publish or compile against `micronaut-inject`.

## Relation to other modules

Dependencies (see `build.gradle.kts`): `graphql-java` and `micronaut-core` only, both `api`.

Depending on this module:

- `graphql-tools` (`api`): the runtime uses the rules at startup.
- `graphql-tools-processor` (`implementation`): the compile-time visitor reads the schema files with `SchemaSources`, and
  runs the per-class rules of `rules/` through `ResolverClassFindings`.
- `graphql-tools-bom` manages its version.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-binding:check
```

This module works on JDK 17, so `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check` is
the check that runs on JDK 17 (see `CONTRIBUTING.md`).

The tests are Spock specs in `src/test/groovy`. Two of them read files outside the module:

- `BindingArchitectureSpec` reads the sources of `src/main/java`.
- `ManifestFormatSpec` reads the examples of the format in `src/main/docs/guide/manifest.adoc`. Changing that page
  re-runs the spec.

The guide page for the manifest format is `src/main/docs/guide/manifest.adoc`; `binding.adoc` describes the binding.
