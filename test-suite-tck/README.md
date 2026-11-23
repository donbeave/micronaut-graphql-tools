# test-suite-tck

Gradle project `:test-suite-tck`, folder `test-suite-tck/`. Not published.

## Purpose

The execution-behaviour TCK. It describes how a running application must answer GraphQL requests: argument binding, null
handling, return types, errors and `data: null`, abstract types, DataLoaders, introspection, variables, aliases and
fragments. Each scenario is data: a schema, the operations, and the exact JSON response. The three example applications
run every scenario against their own resolvers.

## Responsibility

- The scenarios: one directory each under `src/main/resources/tck/`, listed in `tck/scenarios.txt`.
- The runner in `io.micronaut.graphql.tools.tck` (`Tck`, `Scenarios`, `Scenario`, `JsonDiff`). It reads the scenario
  files from the classpath and compares responses. `Tck.run` starts an application context and its embedded server for each
  scenario; the suites supply the application classes and the fixtures.
- The checks of the scenario files in `src/test` (`ScenarioFilesTest`, `ExemptionsTest`, `JsonDiffTest`): every listed
  scenario is complete and well-formed.

Not in this module: the resolvers and the fixtures of a scenario. Each suite has them in `src/test/{java,kotlin,groovy}`
under `example/tck`.

## Packages

The package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.tck` | Test helper | The TCK runner (`Tck`, `Scenarios`, `Scenario`, `JsonDiff`) |

## Who uses it

Maintainers. The test suites use it: `test-suite-java`, `test-suite-kotlin` and `test-suite-groovy` add it as a test
dependency. Application developers do not depend on it.

## Public and internal

Nothing here is a library API. The package `io.micronaut.graphql.tools.tck` is a helper for this repository's tests.

## Relation to other modules

- Depends on `micronaut-runtime` and `micronaut-json-core` (`api`): the suite that runs a scenario provides the server and
  the JSON stack.
- Its tests use JUnit 5 and `micronaut-jackson-databind` (`testRuntimeOnly`).
- Used by `test-suite-java`, `test-suite-kotlin` and `test-suite-groovy` (`testImplementation`). It does not use
  `micronaut-graphql-tools` itself.

## Build and test

Check the scenario files (no application is started):

```
./gradlew :test-suite-tck:test
```

Run the scenarios in a suite. The scenarios are in the `example.tck` package of each suite:

```
./gradlew :test-suite-java:test --tests 'example.tck.*'
./gradlew :test-suite-kotlin:test --tests 'example.tck.*'
./gradlew :test-suite-groovy:test --tests 'example.tck.*'
```

`./gradlew :test-suite-java:nativeTest :test-suite-kotlin:nativeTest --no-parallel` runs the Java and Kotlin TCK as native images too. The whole TCK takes about 7 to 9 seconds
in each suite.

To add a scenario, follow "The execution-behaviour TCK" in `CONTRIBUTING.md`: create the directory, list it in
`scenarios.txt`, and add the fixtures to each suite.
