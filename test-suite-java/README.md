# test-suite-java

Gradle project `:test-suite-java`, folder `test-suite-java/`. Not published.

## Purpose

The example application in Java, and the test suite that runs it. It uses `micronaut-graphql-tools` with the Java
annotation processors, and it has JUnit tests over HTTP, the native image tests and the Micronaut Serialization variant
of the JSON tests. The guide's code snippets come from the three suites (`snippet::` in `src/main/docs/guide`).

## Responsibility

- `src/main/java/example`: the application: the schema (`src/main/resources/schema.graphqls`), resolvers, data classes and
  the `GraphQLFactory` bean. The `.graphql` operations used by the tests are in `src/main/graphql`.
- `src/test/java/example`: JUnit tests by feature (`claimed`, `discovery`, `directives`, `eager`, `enums`, `graphql/query`, `manifest`, `nullfields`,
  `scope`, `tck`, `host`, ...). `example.tck.GraphQLToolsTckTest` runs the scenarios of `test-suite-tck`.
- `src/serdeTest/java/example/nullfields`: the same null-field checks with Micronaut Serialization, in their own source
  set and task.
- Build checks: `checkBindingManifest` and `checkSuiteSchemaParity`. The parity check fails when the schema of this suite
  differs from the copies in `test-suite-kotlin` and `test-suite-groovy`.

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
./gradlew :test-suite-java:serdeTest
./gradlew :test-suite-java:test --tests 'example.tck.*'
./gradlew :test-suite-java:nativeTest :test-suite-java:nativeSerdeTest --no-parallel
```

- `test` runs the JUnit tests on the JVM. `serdeTest` runs the null-field tests with Micronaut Serialization. Both are part of
  `check`.
- `nativeTest` and `nativeSerdeTest` build native images and run the tests in them. They need Oracle GraalVM 25, about 5 GB of
  memory and 5 to 7 minutes each. Run them one after another (`--no-parallel`).
- Add `-PgraphqlJavaLeg=floor|ceiling` to run on another graphql-java leg.

The full list of the commands CI runs is in `CONTRIBUTING.md` ("Running Tests").
