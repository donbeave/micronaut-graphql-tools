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

1. Add the necessary dependencies in build.gradle or pom.xml:

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
- `@GraphQLInterface` Declares a GraphQL interface, allowing multiple types to share a common set of fields.
- `@GraphQLUnion` Defines a GraphQL union type, enabling a field to resolve to more than one type.
- `@GraphQLEnum` Maps a Java enum to a GraphQL enum type.

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

Examples can be found in the [examples](https://github.com/donbeave/micronaut-graphql-tools/tree/master/doc-examples/example-java) directory.

## Why GraphQL Java Tools?

* **Micronaut**:  Leverage the Micronaut framework effectively, ensuring high performance by avoiding reflection, which can slow down applications.
* **Minimal Boilerplate**:  Skip manual configuration of GraphQL types, resolvers and options by using annotations like `GraphQLType`, `@GraphQLRootResolver`, and others.
* **Schema First**:  Micronaut GraphQL Tools allows you to directly use your schema by [GraphQL schema language](http://graphql.org/learn/schema/) instead of code-driven approach.
* **Class Validation**:  Micronaut GraphQL Tools will warn you if you provide classes/types that you don't need to, as well as erroring if you use the wrong Java class for a certain GraphQL type when it builds the schema.

## Snapshots and Releases

Snapshots are automatically published to [Sonatype Snapshots](https://s01.oss.sonatype.org/content/repositories/snapshots/io/micronaut/) using [Github Actions](https://github.com/micronaut-projects/micronaut-graphql-tools/actions).

See the documentation in the [Micronaut Docs](https://docs.micronaut.io/latest/guide/index.html#usingsnapshots) for how to configure your build to use snapshots.

Releases are published to Maven Central via [Github Actions](https://github.com/micronaut-projects/micronaut-graphql-tools/actions).

Releases are completely automated. To perform a release use the following steps:

* [Publish the draft release](https://github.com/micronaut-projects/micronaut-graphql-tools/releases). There should be already a draft release created, edit and publish it. The Git Tag should start with `v`. For example `v1.0.0`.
* [Monitor the Workflow](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3ARelease) to check it passed successfully.
* If everything went fine, [publish to Maven Central](https://github.com/micronaut-projects/micronaut-graphql-tools/actions?query=workflow%3A"Maven+Central+Sync").
* Celebrate!
