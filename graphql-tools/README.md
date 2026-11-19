# micronaut-graphql-tools

Gradle project `:micronaut-graphql-tools`, folder `graphql-tools/`, artifact `io.micronaut.graphql.tools:micronaut-graphql-tools`.

## Purpose

The runtime library. At startup it reads the GraphQL schema files, binds them to Micronaut beans (root resolvers, type
resolvers, input classes, enums, scalars, directives) and builds the graphql-java `GraphQL` instance that the application
serves. Applications depend on this module. Other modules support it or check it.

Start with the guide: `src/main/docs/guide/quickStart.adoc` and `binding.adoc`.

## Responsibility

- Schema discovery (`schema/SchemaDiscovery`) and the runtime wiring that connects fields to beans
  (`GraphQLRuntimeWiringGenerator`, `RuntimeWiringCustomizers`, the data fetchers in `schema/`).
- The strict checks and the startup failures, which are reported as the exceptions of `io.micronaut.graphql.tools.exceptions`.
- Recording the binding manifest (`BindingManifestRecorder`, `BindingManifestWriter`).
- The Micronaut integration: `GraphQLFactory` builds the `GraphQL` bean, and the `processor/` package holds the method
  processors for `@GraphQLRootResolver`, `@GraphQLTypeResolver` and `@GraphQLSchemaCoordinate`.

Not in this module:

- The shared binding rules (which decide whether a schema binds to a set of classes). They live in
  `graphql-tools-binding`, and this module uses them.
- The compile-time checks. They live in `graphql-tools-processor`.

## Packages

Each package has a `package-info.java`. The [API map](../src/main/docs/guide/apiMap.adoc) says which packages are public API and which are internal, and the target layout.

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools` | Public API | The configuration, the customizers, the mapping and directive interfaces, `ArgumentValue` and `BindingManifest` |
| `io.micronaut.graphql.tools.annotation` | Public API | The annotations of resolvers and input classes |
| `io.micronaut.graphql.tools.exceptions` | Public API | The startup failure, `SchemaBindingException` |
| `io.micronaut.graphql.tools.internal` | Internal | The binding implementation: the factories, the wiring, the binders, the checks, the manifest recording |
| `io.micronaut.graphql.tools.internal.exceptions` | Internal | The classes that build each startup failure |
| `io.micronaut.graphql.tools.internal.registry` | Internal | The runtime method processors that register the resolvers |
| `io.micronaut.graphql.tools.internal.discovery` | Internal | Schema discovery: reads the schema files into the type registry |
| `io.micronaut.graphql.tools.internal.input` | Internal | The conversion of argument and input values |
| `io.micronaut.graphql.tools.internal.resolver` | Internal | The data fetchers and the type resolvers |
| `io.micronaut.graphql.tools.internal.wiring` | Internal | The wiring factory that picks the data fetcher and the type resolver of each field and type |

## Who uses it

Library users: applications built with Micronaut that serve GraphQL. Maintainers develop it here. The three example suites
run it in their tests.

## Public and internal

The public API is what applications import. The package layout is reviewed in #354.

- `io.micronaut.graphql.tools.annotation`: the public annotations (`@GraphQLType`, `@GraphQLRootResolver`,
  `@GraphQLTypeResolver`, `@GraphQLInput`, `@GraphQLField`, `@GraphQLArgument`, `@GraphQLIgnore`,
  `@GraphQLSchemaCoordinate`).
- `io.micronaut.graphql.tools` (top level): the extension points and the types around them, for example `RuntimeWiringCustomizer`,
  `SchemaMappingDictionaryCustomizer`, `GraphQLBuilderCustomizer`, `GraphQLSchemaCustomizer`,
  `TypeDefinitionRegistryCustomizer`, `SchemaDirective`, `GraphQLScalarMapping`, `ArgumentValue`, `BindingManifest`
  and `GraphQLToolsConfiguration`. Every type of the package is public API.
- `io.micronaut.graphql.tools.exceptions`: the startup failure, `SchemaBindingException`. Match on its code.
- `io.micronaut.graphql.tools.internal` (and its sub-packages): internal. Every
  class is annotated `@Internal`.

Application code should not import an internal class.

## Relation to other modules

It depends on `graphql-tools-binding`, `micronaut-graphql`, `micronaut-jackson-databind` and
`graphql-java-extended-scalars` (all `api`). `graphql-tools-bom` manages its version. `graphql-tools-processor` uses it in
its tests only. `benchmarks` and the three `test-suite-*` modules use it as the application library.

## Build and test

Run from the repository root (see `mise.toml`):

```
./gradlew :micronaut-graphql-tools:check
```

The library compiles to Java 17 bytecode and supports JDK 17. On JDK 17 run the library and its binding module only; the
processor and the test suites need JDK 21 or newer:

```
./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check
```

`check` runs the unit tests, `ArchitectureRulesSpec` (no reflection outside a short allowlist, no Jackson, no internal type in the
public API; see CONTRIBUTING.md), `DocsCoverageSpec` (a configuration key, public exception or public annotation that the guide
or `README.md` does not name fails it) and `ModuleReadmesSpec` (the module map matches `settings.gradle.kts`). Warnings in
the main code are errors (`-Werror`).

To run only the documentation specs:

```
./gradlew :micronaut-graphql-tools:test --tests '*DocsCoverageSpec*' --tests '*ModuleReadmesSpec*'
```

Add `-PgraphqlJavaLeg=floor|ceiling` for another graphql-java leg. The guide is built with `./gradlew docs`.
