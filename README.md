# Micronaut GraphQL Tools

[![Build Status](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml/badge.svg)](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml)

Schema-first approach for building GraphQL applications with Micronaut.

This is not an official Micronaut module yet: it is developed here and is intended for later contribution to the Micronaut Foundation. Milestone previews (`1.0.0-M<n>`) are published to the GitHub Packages Maven registry of this repository (see [Snapshots and Releases](src/main/docs/guide/releaseHistory.adoc)); nothing is published to Maven Central.

## Documentation

The guide sources are in `src/main/docs/guide`; build them with `./gradlew docs` (see [CONTRIBUTING.md](CONTRIBUTING.md)). No documentation site is published yet.

## Modules

The repository is a Gradle build with one project per folder. The table lists each project, its Gradle path and what it is
for. Every folder has a README with its purpose, its responsibility, what is public and what is internal, and how to build
and test it.

| Module | Gradle project | Purpose |
| --- | --- | --- |
| [`graphql-tools`](graphql-tools/README.md) | `:micronaut-graphql-tools` | The runtime library that applications depend on. |
| [`graphql-tools-binding`](graphql-tools-binding/README.md) | `:micronaut-graphql-tools-binding` | The binding rules used by the runtime library. |
| [`graphql-tools-scalars`](graphql-tools-scalars/README.md) | `:micronaut-graphql-tools-scalars` | The standard scalars: `java.time`, JSON and extended scalars, as beans. |
| [`graphql-tools-micrometer`](graphql-tools-micrometer/README.md) | `:micronaut-graphql-tools-micrometer` | Request metrics (Micrometer), optional. |
| [`graphql-tools-opentelemetry`](graphql-tools-opentelemetry/README.md) | `:micronaut-graphql-tools-opentelemetry` | Request and field spans (OpenTelemetry), optional. |
| [`graphql-tools-upload`](graphql-tools-upload/README.md) | `:micronaut-graphql-tools-upload` | File upload: the `Upload` scalar and the multipart request route, optional. |
| [`graphql-tools-security`](graphql-tools-security/README.md) | `:micronaut-graphql-tools-security` | Micronaut Security: the principal in resolvers, `@Secured` on resolver methods and the root fields hook, optional. |
| [`graphql-tools-testing`](graphql-tools-testing/README.md) | `:micronaut-graphql-tools-testing` | The in-process tester for application tests: run a document against the `GraphQL` bean and assert on the response, test scope. |
| [`graphql-tools-processor`](graphql-tools-processor/README.md) | `:micronaut-graphql-tools-processor` | The annotation processor that runs the compile-time checks. |
| [`graphql-tools-bom`](graphql-tools-bom/README.md) | `:micronaut-graphql-tools-bom` | The bill of materials for the published artifacts. |
| [`docs-examples-java`](docs-examples-java/README.md) | `:micronaut-docs-examples-java` | The guide examples in Java, with a test that runs the quickstart. |
| [`docs-examples-kotlin`](docs-examples-kotlin/README.md) | `:micronaut-docs-examples-kotlin` | The guide examples in Kotlin, with a test that runs the quickstart. |
| [`docs-examples-groovy`](docs-examples-groovy/README.md) | `:micronaut-docs-examples-groovy` | The guide examples in Groovy, with a test that runs the quickstart. |
| [`test-suite-tck`](test-suite-tck/README.md) | `:test-suite-tck` | The execution-behaviour scenarios that the example suites run. |
| [`test-suite-benchmarks`](test-suite-benchmarks/README.md) | `:test-suite-benchmarks` | JMH benchmarks and the allocation gate. |
| [`test-suite-java`](test-suite-java/README.md) | `:test-suite-java` | The example application in Java, with its tests. |
| [`test-suite-serde`](test-suite-serde/README.md) | `:test-suite-serde` | The null-field checks on Micronaut Serialization, with their native test. |
| [`test-suite-kotlin`](test-suite-kotlin/README.md) | `:test-suite-kotlin` | The example application in Kotlin, with its tests. |
| [`test-suite-groovy`](test-suite-groovy/README.md) | `:test-suite-groovy` | The example application in Groovy, with its tests and a native smoke test. |
| [`test-suite-build`](test-suite-build/README.md) | `:test-suite-build` | Sample projects that apply the compile-time checks the way a consumer's build does, built by Gradle TestKit. |

The root project (`graphql-tools-parent`, `build.gradle.kts`) aggregates the modules and the shared build settings. The
guide is in `src/main/docs/guide`, and the build convention plugin is in `buildSrc`.

## Quick start

1. Add the repository and the dependency in build.gradle or pom.xml. Milestone previews come from the GitHub Packages registry of this repository, which needs a token even for downloads ([Consuming milestone previews](src/main/docs/guide/quickStart.adoc) has the full setup; no version is on Maven Central):

Gradle:

```groovy
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/donbeave/micronaut-graphql-tools")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
        content { includeGroup("io.micronaut.graphql.tools") }
    }
}

dependencies {
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-M4")
}
```

The library does not bring a JSON codec (BC-26). Add the one your application uses, for example `micronaut-jackson-databind` or `micronaut-serde-jackson`.

Maven (the repository goes in the POM, the credentials in `~/.m2/settings.xml`, see [Consuming milestone previews](src/main/docs/guide/quickStart.adoc)):

```xml
<repositories>
    <repository>
        <id>github-graphql-tools</id>
        <url>https://maven.pkg.github.com/donbeave/micronaut-graphql-tools</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>io.micronaut.graphql.tools</groupId>
        <artifactId>micronaut-graphql-tools</artifactId>
        <version>1.0.0-M4</version>
    </dependency>
</dependencies>
```

2. Define GraphQL Schema

Create GraphQL schema files, `*.graphqls`, to define types, queries, and mutations, in `src/main/resources/graphql`. The library finds them at startup, see [Schema discovery](src/main/docs/guide/schema.adoc); a `schema { }` block is optional.

3. Configuring GraphQL Tools

Nothing to configure for the default location. To load the schema yourself, define a `TypeDefinitionRegistry` bean; it replaces the discovered schema. The example reads `schema.graphqls` from the root of the classpath; a file in the default folder is `classpath:graphql/schema.graphqls`.

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
- `@GraphQLModels(classes, packages)` On any class of the module that compiles the resolvers: generates the bean introspections of model classes that carry no annotation (generated classes, classes of another module or library), see the guide page Model Classes.
- `@GraphQLField` Marks a method of a `@GraphQLType` class as a field resolver, so that methods with arguments (or a `DataFetchingEnvironment` parameter) can back a GraphQL field. `@GraphQLField("name")` on a method or a property binds the field of that name, see [Binding a field to a differently named member](src/main/docs/guide/binding.adoc).
- `@GraphQLIgnore` Marks a method, a property or an enum constant that binds no field, so that strict mode does not report it, see [Escape hatches](src/main/docs/guide/safety/strictMode.adoc).
- `@GraphQLSchemaCoordinate("Type.field")` Names the field a resolver method binds; meant for resolver contracts (interfaces), see [Resolver contracts](src/main/docs/guide/resolverContracts.adoc).

These are all the annotations the library provides (package `io.micronaut.graphql.tools.annotation`). GraphQL enums map to Java enums (with `@Introspected`, see [Enums](src/main/docs/guide/typeSystem.adoc)), and unions and interfaces to Java interfaces, without an annotation of their own; the concrete classes of a union are registered as shown in [Custom types mapping](src/main/docs/guide/typeSystem.adoc).

## Guide

The guide has the details of each topic. Its pages are in `src/main/docs/guide`, listed in `toc.yml`.

| Topic | Guide page |
| --- | --- |
| Where a field gets its value, property or type resolver precedence, renaming, resolver scopes | [binding.adoc](src/main/docs/guide/binding.adoc) |
| Resolver contracts and the marker interfaces | [resolverContracts.adoc](src/main/docs/guide/resolverContracts.adoc) |
| Return types, enums, unions, interfaces and the schema mapping dictionary | [typeSystem.adoc](src/main/docs/guide/typeSystem.adoc) |
| Arguments, input classes and `ArgumentValue` | [inputs.adoc](src/main/docs/guide/inputs.adoc) |
| Standard and custom scalars | [scalars.adoc](src/main/docs/guide/scalars.adoc) |
| Schema directives | [directives.adoc](src/main/docs/guide/directives.adoc) |
| Customizers and replacing the `GraphQL` bean | [customization.adoc](src/main/docs/guide/customization.adoc) |
| Startup errors and the error codes | [safety/startupErrors.adoc](src/main/docs/guide/safety/startupErrors.adoc) |
| Strict mode and the allow lists | [safety/strictMode.adoc](src/main/docs/guide/safety/strictMode.adoc) |
| Binding manifest | [manifest.adoc](src/main/docs/guide/manifest.adoc) |
| Configuration keys | [configuration.adoc](src/main/docs/guide/configuration.adoc) |
| Compile-time checks | [compileTime.adoc](src/main/docs/guide/compileTime.adoc) |
| Observability (Micrometer and OpenTelemetry) | [observability.adoc](src/main/docs/guide/observability.adoc) |
| File upload | [fileUpload.adoc](src/main/docs/guide/fileUpload.adoc) |
| Security | [security.adoc](src/main/docs/guide/security.adoc) |
| Errors | [errors.adoc](src/main/docs/guide/errors.adoc) |
| Testing | [testing.adoc](src/main/docs/guide/testing.adoc) |
| Native image | [nativeImage.adoc](src/main/docs/guide/nativeImage.adoc) |
| Compatibility, graphql-java lines and milestone previews | [compatibility.adoc](src/main/docs/guide/compatibility.adoc) |
| Why this library | [why.adoc](src/main/docs/guide/why.adoc) |

## Compile-time checks

The `micronaut-graphql-tools-processor` module fails the build on the drift that the startup would report. The build setup
is on [Set Up the Build](src/main/docs/guide/compileTime/setup.adoc), and what is checked at compile time and what only at
startup is on [Limits](src/main/docs/guide/compileTime/limits.adoc).

### The strict schema-build test

What the processor cannot decide passes the compilation and fails the startup: a union member or interface
implementation without a `SchemaMappingDictionary` entry, or mapped to a class that does not implement the Java
interface the field declares; a class that is the Java type of two members of one union (the dictionary
decides which class a member type has, so the processor cannot check "one member per class"); a custom scalar without a `GraphQLScalarMapping` bean; directive wirings. So that this
fails the build too, and not the first deployment, the application keeps one test that builds the schema in strict
mode (the default), with the application's real beans:

```java
@MicronautTest(startApplication = false)
class GraphQLSchemaBuildTest {

    @Inject
    BindingManifest bindingManifest; // getting it builds the schema

    @Test
    void theSchemaBuilds() {
        assertFalse(bindingManifest.getLines().isEmpty());
    }

}
```

Values a resolver returns are not checked by either: a `null` for a non-null field and the class of an object that a
union or interface resolves are errors of the request, reported by graphql-java.

## Examples

Three example applications serve the same schema (the same resolvers, types and data) in each language:
[test-suite-java](test-suite-java), [test-suite-kotlin](test-suite-kotlin) (KSP) and
[test-suite-groovy](test-suite-groovy). Their classes have the same fully qualified names, so a documentation snippet can
include a language's source by path. The guide's quickstart code is in the `docs-examples-*` modules, apart from these suites.
[`test-suite-java/src/test/java/example/host`](test-suite-java/src/test/java/example/host) is an application that supplies
its own `TypeDefinitionRegistry` and `GraphQL` bean, and consumes only the library's `GraphQLSchema`.

## Artifacts and versions

| Artifact | Coordinates |
|---|---|
| Library | `io.micronaut.graphql.tools:micronaut-graphql-tools` |
| Binding rules (internal, pulled in by the library) | `io.micronaut.graphql.tools:micronaut-graphql-tools-binding` |
| Standard scalars | `io.micronaut.graphql.tools:micronaut-graphql-tools-scalars` |
| Metrics (Micrometer) | `io.micronaut.graphql.tools:micronaut-graphql-tools-micrometer` |
| Tracing (OpenTelemetry) | `io.micronaut.graphql.tools:micronaut-graphql-tools-opentelemetry` |
| File upload | `io.micronaut.graphql.tools:micronaut-graphql-tools-upload` |
| Security | `io.micronaut.graphql.tools:micronaut-graphql-tools-security` |
| Testing | `io.micronaut.graphql.tools:micronaut-graphql-tools-testing` |
| Annotation processor | `io.micronaut.graphql.tools:micronaut-graphql-tools-processor` |
| BOM | `io.micronaut.graphql.tools:micronaut-graphql-tools-bom` |

How to add the GitHub Packages repository and a token, and how to publish to a local repository, is in the quick start
guide page [quickStart.adoc](src/main/docs/guide/quickStart.adoc). Nothing is published to Maven Central, and the release
history is on [releaseHistory.adoc](src/main/docs/guide/releaseHistory.adoc).
