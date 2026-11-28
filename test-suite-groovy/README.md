# test-suite-groovy

Gradle project `:test-suite-groovy`, folder `test-suite-groovy/`. Not published.

## Purpose

The example application in Groovy, and the test suite that runs it. It uses `micronaut-graphql-tools` and has Spock
specs over HTTP. Spock cannot run in a native image, so this suite has no native test: its application is smoke-tested
as a native executable instead.

## Responsibility

- `src/main/groovy/example`: the application: resolvers, data classes and the `GraphQLFactory` bean. Its schema is
  `src/main/resources/schema.graphqls`, a copy of the Java suite's schema (checked by `checkSuiteSchemaParity` in
  `test-suite-java`).
- `src/main/resources/META-INF/native-image`: the native-image metadata that the Groovy runtime needs (reflection and
  closure classes). Add an entry there when you add a closure that a Groovy method calls.
- `src/test/groovy/example`: Spock specs: `GraphQLSmokeSpec`, `discovery`, and `tck`, which holds the fixtures of the
  execution-behaviour scenarios and `GraphQLToolsTckSpec`.
- `src/serdeTest/groovy/example/client`: the client examples of the guide, the Groovy version of `example.client` in `test-suite-java`
  (`ExampleClient` and `ServiceClient` with `@CompileStatic` result classes, `LowLevelClientTest`, `DeclarativeClientTest` and
  `ServiceClientTest` as Spock specs), on the Micronaut Serialization stack: the client needs Serde, and `micronaut-serde-jackson` makes its
  `JsonMapper` win over Jackson databind, so the two stacks cannot share a classpath and `test` keeps the Jackson databind stack. The Groovy
  compiler checks the operation documents of `src/serdeTest/graphql/client/server` against the schema of the application (`client.dirs`, the
  client schema `server`). `checkClientExampleParity` in `test-suite-java` compares the class names, the tags and the documents with the Java suite.
- `src/nativeSmoke/groovy/NativeSmoke.groovy`: the native smoke test. It starts the native executable, posts the queries of
  the Java and Kotlin smoke tests to `/graphql`, asserts the bodies and stops the process.

Not in this module: the library (`graphql-tools`), the shared scenarios (`test-suite-tck`) and the compile-time checks
(`graphql-tools-processor`, which this suite does not use).

## Packages

Example application code, not a library package: no `package-info.java`. Nothing here is API.

| Package | Audience | Holds |
| --- | --- | --- |
| `example` | Example | The application class and the elapsed-time interceptor |
| `example.data` | Example | The data classes and the in-memory repositories |
| `example.graphql` | Example | The `GraphQLFactory` bean, the DataLoaders, and below it the resolvers by operation and type (`query`, `mutation`, `type`, `input`, `directive`) |

## Who uses it

Maintainers and CI. Application developers can read it as an example of a Groovy application.

## Public and internal

Nothing here is a library API. Do not import from `example.*` in application code.

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`) and `test-suite-tck` (`testImplementation`).
- Does not depend on `test-suite-java` or `test-suite-kotlin`; their schemas are compared with this one by
  `checkSuiteSchemaParity`.

## Build and test

Run from the repository root. JDK 21 or 25.

```
./gradlew :test-suite-groovy:test
./gradlew :test-suite-groovy:test --tests 'example.tck.*'
./gradlew :test-suite-groovy:serdeTest
./gradlew :test-suite-groovy:nativeSmokeTest --no-parallel
```

- `test` runs the Spock specs on the JVM (Jackson databind stack); `serdeTest` runs the client examples (Micronaut Serialization stack). Both are part of `check`.
- `nativeSmokeTest` builds the native executable with `:test-suite-groovy:nativeCompile` and smoke-tests it. It needs
  Oracle GraalVM 25, about 5 minutes and 5 GB of memory. It is the native check for this suite in CI.
- Add `-PgraphqlJavaLeg=floor|ceiling` to run on another graphql-java leg.

The full list of the commands CI runs is in `CONTRIBUTING.md` ("Running Tests").
