# test-suite-java

Gradle project `:test-suite-java`, folder `test-suite-java/`. Not published.

## Purpose

The example application in Java, and the test suite that runs it. It uses `micronaut-graphql-tools` with the Java
annotation processors, and it has JUnit tests over HTTP, the native image tests . The guide's code snippets come from the three suites (`snippet::` in `src/main/docs/guide`).

## Responsibility

- `src/main/java/example`: the application: the schema (`src/main/resources/schema.graphqls`), resolvers, data classes and
  the `GraphQLFactory` bean.
- `src/serdeTest`: the tests that call the application with the library's GraphQL client (`example.client.ExampleClient`, a declarative
  `@GraphQLClient` with hand-written result records), on the Micronaut Serialization stack: `graphql/query`, `host`, `qualified` and
  `directives.DirectiveWiringTest`. The operation documents are in `src/serdeTest/graphql/client/server`; the processor checks them against the
  schema of the application while the source set compiles (`client.dirs`, the client schema `server`). The client needs Serde, and
  `micronaut-serde-jackson` makes its `JsonMapper` win over Jackson databind, so the two stacks cannot share a classpath: `test` keeps the
  Jackson databind stack. `src/sharedFixtures` holds the beans of the environment `host-graphql` that both source sets start the application with.
- `src/test/java/example`: JUnit tests by feature (`claimed`, `discovery`, `directives`, `eager`, `enums`, `manifest`, `nullfields`,
  `scope`, `tck`, ...). `example.tck.GraphQLToolsTckTest` runs the scenarios of `test-suite-tck`.
- The null-field checks with Micronaut Serialization are in `test-suite-serde`.
- The client examples of the guide are `example.client.*` of `src/serdeTest`: the declarative clients `ExampleClient` (the embedded server) and
  `ServiceClient` (a service id, with `micronaut.http.services.example.urls`), and the tests `LowLevelClientTest`, `DeclarativeClientTest` and
  `ServiceClientTest`. `RequestRecorder` (a server filter) records the headers and the cookie that the clients send and answers 503 on demand,
  for the retry example. `test-suite-kotlin` and `test-suite-groovy` have the same classes and tags in their own `serdeTest` source sets.
  `example.witness.RawHttpWitnessTest` in `src/test` is the independent witness of the server: it posts the same operations with the plain
  Micronaut HTTP client on the Jackson databind stack and shares no code with the library client.
- Build checks: `checkBindingManifest`, `checkSuiteSchemaParity` and `checkClientExampleParity`. The schema check fails when the schema of this
  suite differs from the copies in `test-suite-kotlin` and `test-suite-groovy`; the client check fails when the class names, the tags or the
  operation documents of the client examples of `serdeTest` differ between the three suites.

Not in this module: the library itself (`graphql-tools`) and the shared scenarios (`test-suite-tck`).

## Packages

Example application code, not a library package: no `package-info.java`. Nothing here is API.

| Package | Audience | Holds |
| --- | --- | --- |
| `example` | Example | The application class and the elapsed-time interceptor |
| `example.data` | Example | The data classes and the in-memory repositories |
| `example.graphql` | Example | The `GraphQLFactory` bean, the DataLoaders, and below it the resolvers by operation and type (`query`, `mutation`, `type`, `input`, `directive`) |

## Who uses it

Maintainers and CI. Application developers can read it as an example of a Java application.

## Public and internal

Nothing here is a library API. Do not import from `example.*` in application code.

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`) and `test-suite-tck` (`testImplementation`).
- Puts `micronaut-graphql-tools-processor` on its `annotationProcessor` path, so the compile-time checks run on this
  application. `src/driftFixture` holds a resolver that drifts from the schema on purpose, and `driftFixtureTest` (part of
  `check`) asserts that compiling it fails with `[argument-mismatch] Query.searchProfile`; it runs that compilation in a build
  of its own (`config/drift-fixture.gradle.kts`).
- The Kotlin and Groovy suites are compared with this one by `checkSuiteSchemaParity`; they do not depend on it.

## Build and test

Run from the repository root. JDK 21 or 25 (JDK 25 with Oracle GraalVM for the native tests; see `mise.toml`).

```
./gradlew :test-suite-java:test
./gradlew :test-suite-java:test --tests 'example.tck.*'
./gradlew :test-suite-java:serdeTest
./gradlew :test-suite-java:nativeTest :test-suite-java:nativeSerdeTest --no-parallel
```

- `test` runs the JUnit tests on the JVM (Jackson databind stack); `serdeTest` runs the client tests (Micronaut Serialization stack). Both are part of `check`.
- `nativeTest` and `nativeSerdeTest` build a native image and run the tests of their source set in it. They need Oracle GraalVM 25, about 5 GB of
  memory and 5 to 7 minutes each. Run them one after another (`--no-parallel`).
- Add `-PgraphqlJavaLeg=floor|ceiling` to run on another graphql-java leg.

The full list of the commands CI runs is in `CONTRIBUTING.md` ("Running Tests").
