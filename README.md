# Micronaut GraphQL Tools

[![Build Status](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml/badge.svg)](https://github.com/donbeave/micronaut-graphql-tools/actions/workflows/gradle.yml)

Schema-first approach for building GraphQL applications with Micronaut 4, in Java, Kotlin and Groovy.

This is not an official Micronaut module yet: it is developed here and is intended for later contribution to the Micronaut Foundation. Milestone previews (`1.0.0-M<n>`) are published to the GitHub Packages Maven registry of this repository (see [Release History](src/main/docs/guide/releaseHistory.adoc)); nothing is published to Maven Central.

## Documentation

The guide sources are in `src/main/docs/guide`, listed in [`toc.yml`](src/main/docs/guide/toc.yml); build them with `./gradlew docs` (see [CONTRIBUTING.md](CONTRIBUTING.md)). No documentation site is published yet. Start with these pages:

- [Why This Library](src/main/docs/guide/why.adoc) says what the library is for and compares it with other tools.
- [Quick Start](src/main/docs/guide/quickStart.adoc) has the full setup, including the repository declarations and the credentials of GitHub Packages.
- [Configuration](src/main/docs/guide/configuration.adoc) lists every `micronaut.graphql.tools.*` key.
- [Exceptions](src/main/docs/guide/exceptions.adoc) lists the startup and compile-time error codes.

## Quick start

1. Add the dependency (the repository declaration is on the Quick Start page):

```groovy
dependencies {
    implementation("io.micronaut.graphql.tools:micronaut-graphql-tools:1.0.0-M4")
}
```

2. Define the schema in `*.graphqls` files in `src/main/resources/graphql`; the library finds them at startup.
3. Add a model class, annotated with `@Introspected`, for each object type of the schema.
4. Add a root resolver, a `@Singleton` class annotated with `@GraphQLRootResolver`, whose method names are the field names.
5. Run a query against the `GraphQL` bean, or post it to `/graphql`.

The classes of steps 3 to 5 are the ones that `QuickStartTest` runs in each language, and the Quick Start page includes them from the same source.

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

Nothing is published to Maven Central, and the release
history is on [releaseHistory.adoc](src/main/docs/guide/releaseHistory.adoc).
