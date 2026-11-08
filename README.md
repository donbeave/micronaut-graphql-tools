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
- `DataFetcherResult<T>`, for the field or each list element; its errors and local context are applied;
- `Optional<T>`, for the field or each list element, and only where the GraphQL type is nullable (an empty
  `Optional` becomes `null`).

The mapping then checks `T`. Anything else fails at startup with `UnsupportedReturnTypeException`. In particular:

- `List<CompletionStage<T>>` is rejected: graphql-java does not wait for futures inside a list, and the client would
  receive the future's `toString()`. Return `CompletionStage<List<T>>` instead.
- `Optional<T>` on a non-null type (`String!`, `[String!]`) is rejected.

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
