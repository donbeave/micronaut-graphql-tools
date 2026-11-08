# Micronaut GraphQL Tools

[![Maven Central](https://img.shields.io/maven-central/v/io.micronaut.project-template/micronaut-graphql-tools.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:%22io.micronaut.project-template%22%20AND%20a:%22micronaut-graphql-tools%22)
[![Build Status](https://github.com/micronaut-projects/micronaut-graphql-tools/workflows/Java%20CI/badge.svg)](https://github.com/micronaut-projects/micronaut-graphql-tools/actions)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=micronaut-projects_micronaut-template&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=micronaut-projects_micronaut-template)
[![Revved up by Gradle Enterprise](https://img.shields.io/badge/Revved%20up%20by-Gradle%20Enterprise-06A0CE?logo=Gradle&labelColor=02303A)](https://ge.micronaut.io/scans)

Schema-first approach for building GraphQL applications with Micronaut.

## Documentation

See the [Documentation](https://micronaut-projects.github.io/micronaut-graphql-tools/latest/guide/) for more information.

See the [Snapshot Documentation](https://micronaut-projects.github.io/micronaut-graphql-tools/snapshot/guide/) for the current development docs.

## Quick start

1. Add the necessary dependencies in build.gradle or pom.xml (no version is on Maven Central yet, see [Artifacts and versions](#artifacts-and-versions)):

Gradle:

```groovy
implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-SNAPSHOT")
```

Maven:

```xml
<dependencies>
    <dependency>
        <groupId>io.micronaut.graphql.tools</groupId>
        <artifactId>micronaut-graphql-tools</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

2. Define GraphQL Schema

Create a GraphQL schema file, `schema.graphqls` to define types, queries, and mutations, for example in `src/main/resources` resources folder.

3. Configuring GraphQL Tools

Define a `TypeDefinitionRegistry` bean to load the GraphQL schema.

```java
@Bean
@Singleton
public TypeDefinitionRegistry typeDefinitionRegistry(ResourceResolver resourceResolver) {
    InputStream inputStream = resourceResolver.getResourceAsStream("classpath:schema.graphqls").get();
    return new TypeDefinitionRegistry()
            .merge(new SchemaParser().parse(new BufferedReader(new InputStreamReader(inputStream))));
}
```

4. Using Annotations for GraphQL Components

- `@GraphQLRootResolver` Defines a root-level resolver, representing a GraphQL entry point, such as a query or mutation.
- `@GraphQLType` Maps a Java class to a GraphQL type.
- `@GraphQLTypeResolver` Specifies a resolver for a specific GraphQL type field, enabling mapping of nested or complex fields within types.
- `@GraphQLInput` Defines a GraphQL input type, representing structured input data for mutations or queries.
- `@GraphQLField` Marks a method of a `@GraphQLType` class as a field resolver, so that methods with arguments (or a `DataFetchingEnvironment` parameter) can back a GraphQL field.

These are all the annotations the library provides (package `io.micronaut.graphql.tools.annotation`). GraphQL enums map to Java enums and unions to Java interfaces without an annotation of their own; the concrete classes of a union are registered as shown in [Custom types mapping](#custom-types-mapping).

## Return types

The declared return type of a field (a resolver method or a bean property) may wrap the mapped class in the
containers graphql-java resolves at runtime, outermost first and each at most once:

- `CompletionStage<T>`, for the field itself only;
- `Publisher<T>` (`org.reactivestreams.Publisher`, any subtype such as `Flux`), required on fields of the subscription
  type and allowed nowhere else; the wrappers below then apply to each event;
- `DataFetcherResult<T>`, for the field or each list element; its errors and local context are applied;
- `Optional<T>`, for the field or each list element, and only where the GraphQL type is nullable (an empty
  `Optional` becomes `null`).

The mapping then checks `T`. Anything else fails at startup with `UnsupportedReturnTypeException`. In particular:

- `List<CompletionStage<T>>` is rejected: graphql-java does not wait for futures inside a list, and the client would
  receive the future's `toString()`. Return `CompletionStage<List<T>>` instead.
- `Optional<T>` on a non-null type (`String!`, `[String!]`) is rejected.
- A field of the subscription type must return a `Publisher` (optionally inside a `CompletionStage`); otherwise the
  startup fails (graphql-java would fail only on the first subscription request). A `Publisher` on any other field,
  or inside a list, is rejected.
- `DataFetcherResult<Publisher<T>>` is not supported on a subscription field, although graphql-java would unwrap it:
  return the `Publisher` and, if needed, a `DataFetcherResult` per event.

## Configuration

| Property | Values (default first) | Meaning |
|---|---|---|
| `micronaut.graphql.tools.unreachable-types` | `fail` \| `ignore` | Object, interface and union types of the schema that no field reachable from a root operation type returns have no binding. `fail` stops the startup with one error that lists all of them; `ignore` keeps them in the schema with graphql-java's default data fetchers and logs them as a warning. |
| `micronaut.graphql.tools.allow-unreachable` | list of type names (empty) | Unreachable types that are allowed even with `fail`; they are logged at INFO. Every entry must be an unreachable object, interface or union type of the schema, otherwise the startup fails, so the list cannot go stale. |
| `micronaut.graphql.tools.inert-directives` | list of directive names, without `@` (empty) | Directives the schema may apply without a wiring, because they have no effect at runtime; they are logged at INFO. Every other applied directive needs a wiring (see [Schema directives](#schema-directives)). Every entry must be a directive the schema declares, that is not built in and that has no wiring, otherwise the startup fails, so the list cannot go stale. |

```yaml
micronaut:
  graphql:
    tools:
      allow-unreachable:
        - LegacyReport
```

## Custom types mapping

GraphQL interfaces and union types often have multiple concrete implementations.

Define a `SchemaMappingDictionaryCustomizer` bean to register custom types if any.

In the example below, the `SchemaMappingDictionaryCustomizer` bean is used to register the custom implementations of the `Error` interface type. This associates the GraphQL types `SecurityError` and `ValidationError` with their respective Java implementations (`SecurityError.class` and `ValidationError.class`).

```java
@Bean
@Singleton
public SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer() {
return schemaMappingDictionary -> schemaMappingDictionary
        .registerType("SecurityError", SecurityError.class)
        .registerType("ValidationError", ValidationError.class);
}
```

## Customizing the schema

Three kinds of beans change what the library builds. Each kind may have any number of beans; they run lowest order
first, by `@Order` on the bean class or factory method, or by `Ordered#getOrder()` if the bean implements `Ordered`.

| Bean type | Receives | Runs |
|---|---|---|
| `TypeDefinitionRegistryCustomizer` | a copy of the application's `TypeDefinitionRegistry` bean (the bean itself stays unchanged) | before the binding: types and fields added here need a binding like any other |
| `RuntimeWiringCustomizer` | the `RuntimeWiring.Builder` that holds the library's wiring | after the binding, before the schema is generated |
| `GraphQLSchemaCustomizer` | the generated `GraphQLSchema`; returns the schema to use | last |

A `RuntimeWiringCustomizer` may only **add** (scalars, directive wirings, field visibility, ...). If it replaces a data
fetcher, default data fetcher, type resolver or scalar that the library registered, or the wiring factory, or puts a
data fetcher or type resolver for a field or type the library wired into a code registry (`builder.codeRegistry(...)`,
which graphql-java lets win), the startup fails with `RuntimeWiringOverrideException`, which names every replacement
and the customizer that made it. Code registry entries for fields the library does not wire, and enum value
providers, are allowed.
(On graphql-java 25.1 the builder itself already rejects the first replaced data fetcher, default data fetcher, type
resolver or scalar with `StrictModeWiringException`; 21.3 accepts them silently.) To wrap data fetchers, use a
`SchemaDirectiveWiring`; to change the generated schema, use a `GraphQLSchemaCustomizer` (it replaces
`RuntimeWiring.Builder#transformer`, which graphql-java 25 removed). The library does not check what a
`GraphQLSchemaCustomizer` changes.

```java
@Bean
@Singleton
@Order(10)
public RuntimeWiringCustomizer hideInternalFields() {
    return builder -> builder.fieldVisibility(BlockedFields.newBlock().addPattern("Query.internal.*").build());
}
```

## Schema directives

The library applies an SDL directive wiring (`graphql.schema.idl.SchemaDirectiveWiring`) only through a
`SchemaDirective` bean:

- `SchemaDirective.named(name, wiring)` binds the wiring to **one** directive name; graphql-java calls it only for the
  elements that apply that directive (`RuntimeWiring.Builder#directive`). Two such beans for one name fail the
  startup with `DuplicateDirectiveWiringException`, and a `RuntimeWiringCustomizer` that replaces such a wiring fails
  it with `RuntimeWiringOverrideException`.
- `SchemaDirective.everyElement(names, wiring)` registers a wiring that graphql-java calls for **every** schema element
  and that decides itself what to do (`RuntimeWiring.Builder#directiveWiring`); the names say which directives it
  implements. These run lowest order first (`@Order` or `Ordered` on the `SchemaDirective` bean), so the first one
  wraps a data fetcher innermost.

A `SchemaDirectiveWiring` bean is **not** applied on its own: if no `SchemaDirective` bean wraps that instance, the
startup fails with `UnwrappedDirectiveWiringException`, which names the bean. A wiring therefore never runs on every
element just because it is a bean. To keep a wiring as a bean, inject it into the factory method of its
`SchemaDirective`.

**Every directive the schema applies must have a wiring**, or the startup fails with `UnwiredDirectivesException`,
which lists every such directive and where the schema applies it. A directive counts as wired if a `SchemaDirective`
bean names it, or if a `RuntimeWiringCustomizer` registers a wiring for it with `RuntimeWiring.Builder#directive`. A
directive that is meant to have no effect at runtime (documentation, or enforced elsewhere) is listed in
`micronaut.graphql.tools.inert-directives`. `SchemaDirectiveWiring` has no hook for the schema definition or for
directive arguments, so a directive applied there can only be listed as inert, or be handled by a
`GraphQLSchemaCustomizer`. Exempt: graphql-java's built-in directives (`@deprecated`, `@specifiedBy`, `@oneOf`,
`@include`, `@skip`), directives the schema declares but never applies, and directives it applies without declaring
them (graphql-java reports those). `SchemaDirective` names that the schema does not declare are logged at INFO and not
used, so a shared module may wire more directives than one schema applies.

```java
@Bean
@Singleton
public SchemaDirective upper(UpperCaseWiring upperCaseWiring) { // UpperCaseWiring may be a bean
    return SchemaDirective.named("upper", upperCaseWiring);
}
```

graphql-java-extended-validation plugs in as a wiring for every element that names its constraint directives, and a
`TypeDefinitionRegistryCustomizer` can add their declarations unless the SDL declares them:

```java
@Bean
@Singleton
public SchemaDirective validation() {
    DirectiveConstraints constraints = DirectiveConstraints.newDirectiveConstraints().build();
    return SchemaDirective.everyElement(constraints.getConstraints().keySet(),
            new ValidationSchemaWiring(ValidationRules.newValidationRules().build()));
}

@Bean
@Singleton
public TypeDefinitionRegistryCustomizer validationDirectives() {
    return registry -> registry.merge(DirectiveConstraints.newDirectiveConstraints().build().getDirectivesDeclaration());
}
```

Use extended-validation 21.0 with graphql-java 21.3 and 24.0 with 25.1 (21.0 calls a `GraphQLCodeRegistry` method
that graphql-java 25 removed); the library's tests run both.

## Building the GraphQL bean yourself

The library creates two beans: the bound `GraphQLSchema`, and the `GraphQL` engine that runs it. Each one is created
only when the application has no bean of that type:

- An application that configures the engine itself (instrumentation, execution strategies, its own transport)
  provides a `GraphQL` bean built from the library's `GraphQLSchema` bean. The library then creates no `GraphQL`
  bean, so there is exactly one.
- An application that provides its own `GraphQLSchema` bean gets no binding from the library (no
  `TypeDefinitionRegistry` is needed); the library's `GraphQL` bean, unless the application has one too, runs that
  schema.

```java
@Bean
@Singleton
public GraphQL graphQL(GraphQLSchema schema, MyInstrumentation instrumentation) {
    return GraphQL.newGraphQL(schema).instrumentation(instrumentation).build();
}
```

The library brings `io.micronaut.graphql:micronaut-graphql` as an API dependency, and its HTTP controller serves
whichever `GraphQL` bean exists. The library's own code does not use it, so an application with its own transport
can exclude that dependency.

## Examples

Examples can be found in the [test-suite](test-suite) directory.

## Compatibility

The library is built and tested on Java 21 against two graphql-java lines (CI runs both):

| graphql-java | graphql-java-extended-scalars | java-dataloader | Micronaut / micronaut-graphql | Java |
|---|---|---|---|---|
| 21.3 (default build) | 21.0 | 3.2.1 | 4.10.x / 4.8.0 | 21 |
| 25.1 | 24.0 | 6.0.0 | 4.10.x / 4.8.0 | 21 |

The published POM does not pin graphql-java: it arrives through `micronaut-graphql` (4.8.0 brings 22.4). Pin
graphql-java in your build to the line you run, and use the matching extended-scalars version from the table
(extended-scalars has no 25.x release; 24.0 works with graphql-java 25.1).

To build or test against a leg locally:

```shell
./gradlew build                            # graphql-java 21.3
./gradlew build -PgraphqlJavaVersion=25.1  # graphql-java 25.1
```

## Artifacts and versions

| Artifact | Coordinates |
|---|---|
| Library | `io.micronaut.graphql.tools:micronaut-graphql-tools` |
| BOM | `io.micronaut.graphql.tools:micronaut-graphql-tools-bom` |

The version comes from `projectVersion` in `gradle.properties` (`1.0.0-SNAPSHOT` on `master`). To build a fixed
version, override it on the command line; a version without `-SNAPSHOT` is published as a release version:

```shell
# into the local Maven repository (~/.m2/repository, or -Dmaven.repo.local=<dir>)
./gradlew publishToMavenLocal -PprojectVersion=1.0.0-internal.1

# into a Maven repository of your own (Nexus, Artifactory, GitHub Packages, ...)
./gradlew publishAllPublicationsToExternalRepository -PprojectVersion=1.0.0-internal.1 \
    -Dio.micronaut.publishing.uri=https://repo.example.com/maven-releases \
    -Dio.micronaut.publishing.username=<user> \
    -Dio.micronaut.publishing.password=<password>
```

The `io.micronaut.publishing.*` settings are JVM system properties (`-D`), not Gradle project properties. Publish from
the default graphql-java leg only (no `-PgraphqlJavaVersion`): the published POM takes its versions from the build's
resolved dependencies, so the build refuses to run a publish task on another leg.

Both commands publish the library (jar, sources, javadoc, POM and Gradle module metadata) and the BOM. Consumers then
add the repository and depend on `io.micronaut.graphql.tools:micronaut-graphql-tools:<version>`.

The binary-compatibility check (`findBaseline`/`japiCmp`) is disabled until the first version is released; see the
`binaryCompatibility` block in the build files.

## Why GraphQL Java Tools?

* **Micronaut**:  Leverage the Micronaut framework effectively, ensuring high performance by avoiding reflection, which can slow down applications.
* **Minimal Boilerplate**:  Skip manual configuration of GraphQL types, resolvers and options by using annotations like `GraphQLType`, `@GraphQLRootResolver`, and others.
* **Schema First**:  Micronaut GraphQL Tools allows you to directly use your schema by [GraphQL schema language](http://graphql.org/learn/schema/) instead of code-driven approach.
* **Class Validation**:  Micronaut GraphQL Tools will warn you if you provide classes/types that you don't need to, as well as erroring if you use the wrong Java class for a certain GraphQL type when it builds the schema.

## Snapshots and Releases

Snapshots are not published automatically at the moment. The CI workflow (`gradle.yml`) builds and tests only; the
snapshot workflow (`publish-snapshot.yml`) is started manually and still runs on Java 11, so it cannot build this
project until it moves to Java 21. Build a version yourself as described in
[Artifacts and versions](#artifacts-and-versions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-graphql-tools/actions).

Releases are completely automated. To perform a release use the following steps:

* [Publish the draft release](https://github.com/micronaut-projects/micronaut-graphql-tools/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3ARelease) to check it passed successfully.
* If everything went fine, [publish to Maven Central](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3A"Maven+Central+Sync").
* Celebrate!
