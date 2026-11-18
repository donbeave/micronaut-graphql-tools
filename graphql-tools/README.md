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
| `io.micronaut.graphql.tools` | Mixed | The configuration, the customizers, the mapping and directive interfaces (public) and the binding implementation (`@Internal`) |
| `io.micronaut.graphql.tools.annotation` | Public API | The annotations of resolvers and input classes |
| `io.micronaut.graphql.tools.exceptions` | Mixed | The startup failures (`SchemaBindingException` and subclasses) |
| `io.micronaut.graphql.tools.processor` | Internal | The runtime method processors that register the resolvers |
| `io.micronaut.graphql.tools.schema` | Internal | Schema discovery, the data fetchers, the type resolvers, the value conversion |

## Who uses it

Library users: applications built with Micronaut that serve GraphQL. Maintainers develop it here. The three example suites
run it in their tests.

## Public and internal

The public API is what applications import. It is not fixed for 1.0 yet: the package layout is reviewed in #354, and the
public surface is reduced in #109.

- `io.micronaut.graphql.tools.annotation`: the public annotations (`@GraphQLType`, `@GraphQLRootResolver`,
  `@GraphQLTypeResolver`, `@GraphQLInput`, `@GraphQLField`, `@GraphQLArgument`, `@GraphQLIgnore`,
  `@GraphQLSchemaCoordinate`).
- `io.micronaut.graphql.tools` (top level): mixed. The extension points and the types around them, for example `RuntimeWiringCustomizer`,
  `SchemaMappingDictionaryCustomizer`, `GraphQLBuilderCustomizer`, `GraphQLSchemaCustomizer`,
  `TypeDefinitionRegistryCustomizer`, `SchemaDirective`, `GraphQLScalarMapping`, `ArgumentValue`, `BindingManifest`
  and `GraphQLToolsConfiguration`, are not annotated `@Internal`. This list is not complete. Most other types in the package are annotated `@Internal`. Check the annotation on the class
  before you use it.
- `io.micronaut.graphql.tools.exceptions`: the startup failures. Not annotated `@Internal` today.
- `io.micronaut.graphql.tools.processor` and `io.micronaut.graphql.tools.schema`: internal. Every class is annotated
  `@Internal`.

Application code should not import an internal class. The package layout will be documented in `package-info.java` (#354).

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

`check` runs the unit tests, `DocsCoverageSpec` (a configuration key, public exception or public annotation that the guide
or `README.md` does not name fails it) and `ModuleReadmesSpec` (the module map matches `settings.gradle.kts`). Warnings in
the main code are errors (`-Werror`).

To run only the documentation specs:

```
./gradlew :micronaut-graphql-tools:test --tests '*DocsCoverageSpec*' --tests '*ModuleReadmesSpec*'
```

Add `-PgraphqlJavaLeg=floor|ceiling` for another graphql-java leg. The guide is built with `./gradlew docs`.
