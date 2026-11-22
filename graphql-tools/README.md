# micronaut-graphql-tools

Gradle project `:micronaut-graphql-tools`, folder `graphql-tools/`, artifact `io.micronaut.graphql.tools:micronaut-graphql-tools`.

## Purpose

The runtime library. At startup it reads the GraphQL schema files, binds them to Micronaut beans (root resolvers, type
resolvers, input classes, enums, scalars, directives) and builds the graphql-java `GraphQL` instance that the application
serves. Applications depend on this module. Other modules support it or check it.

Start with the guide: `src/main/docs/guide/quickStart.adoc` and `binding.adoc`.

## Responsibility

- Schema discovery (`internal.discovery.SchemaDiscovery`) and the runtime wiring that connects fields to beans
  (`GraphQLRuntimeWiringGenerator`, `RuntimeWiringCustomizers`, the data fetchers in `internal.resolver`).
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
| `io.micronaut.graphql.tools` | Public API | No types: the namespace of the packages below |
| `io.micronaut.graphql.tools.config` | Public API | The configuration class, `GraphQLToolsConfiguration` |
| `io.micronaut.graphql.tools.customizer` | Public API | The five `*Customizer` interfaces |
| `io.micronaut.graphql.tools.mapping` | Public API | `SchemaMappingDictionary`, `GraphQLScalarMapping` and the mapping contexts |
| `io.micronaut.graphql.tools.directive` | Public API | `SchemaDirective` and `CustomWiring` |
| `io.micronaut.graphql.tools.input` | Public API | `ArgumentValue` |
| `io.micronaut.graphql.tools.manifest` | Public API | `BindingManifest` |
| `io.micronaut.graphql.tools.annotation` | Public API | The annotations of resolvers and input classes |
| `io.micronaut.graphql.tools.exceptions` | Public API | The startup failure, `SchemaBindingException` |
| `io.micronaut.graphql.tools.internal` | Internal | The entry points of the binding: the factories, the eager binding, the bean selection and the shared helpers |
| `io.micronaut.graphql.tools.internal.exceptions` | Internal | The base classes of the failures and the aggregate of one binding run; the classes of each kind are in the sub-packages |
| `io.micronaut.graphql.tools.internal.exceptions.mapping` | Internal | The failures of the mapping dictionary and of class and scalar mappings |
| `io.micronaut.graphql.tools.internal.exceptions.resolver` | Internal | The failures of binding a field to a resolver method, its arguments and its return type |
| `io.micronaut.graphql.tools.internal.exceptions.implementation` | Internal | The failures of finding the implementation and the type resolver of an interface, union or abstract type |
| `io.micronaut.graphql.tools.internal.exceptions.input` | Internal | The failures of input types, their properties and enum values |
| `io.micronaut.graphql.tools.internal.exceptions.wiring` | Internal | The failures of directive wiring and runtime wiring |
| `io.micronaut.graphql.tools.internal.exceptions.checks` | Internal | The failures that the startup checks report: strict mode, unreachable types, schema coordinates |
| `io.micronaut.graphql.tools.internal.exceptions.configuration` | Internal | The failures of the setup: configuration, the schema definition and schema discovery |
| `io.micronaut.graphql.tools.internal.registry` | Internal | The runtime method processors that register the resolvers |
| `io.micronaut.graphql.tools.internal.discovery` | Internal | Schema discovery: reads the schema files into the type registry |
| `io.micronaut.graphql.tools.internal.input` | Internal | The conversion of argument and input values, and the input type binder |
| `io.micronaut.graphql.tools.internal.resolver` | Internal | The data fetchers and the type resolvers, and the planning of resolver arguments |
| `io.micronaut.graphql.tools.internal.wiring` | Internal | The runtime wiring generator, the wiring factory, the interface field binder and the directive wirings |
| `io.micronaut.graphql.tools.internal.checks` | Internal | The startup checks and validators |
| `io.micronaut.graphql.tools.internal.manifest` | Internal | Recording and writing the binding manifest |
| `io.micronaut.graphql.tools.internal.observer` | Internal | The observer SPI of the request hooks (`RequestObserver`) and the one `Instrumentation` bean that calls the observers; the Micrometer and OpenTelemetry modules contribute the observers |
| `io.micronaut.graphql.tools.internal.model` | Internal | The runtime registries and models of the beans and types |

## Who uses it

Library users: applications built with Micronaut that serve GraphQL. Maintainers develop it here. The three example suites
run it in their tests.

## Public and internal

The public API is what applications import. The package layout is reviewed in #354.

- `io.micronaut.graphql.tools.annotation`: the public annotations (`@GraphQLType`, `@GraphQLRootResolver`,
  `@GraphQLTypeResolver`, `@GraphQLInput`, `@GraphQLField`, `@GraphQLArgument`, `@GraphQLIgnore`,
  `@GraphQLSchemaCoordinate`).
- `io.micronaut.graphql.tools.config`, `customizer`, `mapping`, `directive`, `input` and `manifest`: the extension points
  and the types around them: `GraphQLToolsConfiguration`; `RuntimeWiringCustomizer`, `SchemaMappingDictionaryCustomizer`,
  `GraphQLBuilderCustomizer`, `GraphQLSchemaCustomizer` and `TypeDefinitionRegistryCustomizer`; `SchemaMappingDictionary`
  and `GraphQLScalarMapping`; `SchemaDirective` and `CustomWiring`; `ArgumentValue`; `BindingManifest`. Every type of
  these packages is public API; the root package `io.micronaut.graphql.tools` holds no types.
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
