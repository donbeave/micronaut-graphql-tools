# micronaut-graphql-tools-testing

Gradle project `:micronaut-graphql-tools-testing`, folder `graphql-tools-testing/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-testing`.

## Purpose

An in-process GraphQL tester for application test suites: run a document with variables, context values and headers
against the application's own schema, and assert on the data, the errors and the extensions. No server and no HTTP
client are involved. Add it with `testImplementation`.

## Responsibility

- `GraphQLTester`, a bean that runs a document through the `GraphQLInvocation` bean of the application, so the executor
  hop, the context contributors, the customizers, the instrumentations and the error handling run as for a request.
- `GraphQLTestRequest`, the builder of one call: operation name, variables, context values, headers and timeout.
- `GraphQLTestResponse`, the response with `getData()`, `getErrors()` and `getExtensions()` and fluent assertions on
  paths and errors. A failed assertion is an `AssertionError`; the module has no test framework dependency.
- Context values and headers of a call travel on a synthetic `POST /graphql` request; one `GraphQLContextContributor`
  copies the values into the `GraphQLContext`.

Not in this module: an HTTP client (a test of the transport uses Micronaut's HTTP client), a mock of resolvers, or a
test framework integration.

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.testing` | Public API | `GraphQLTester`, `GraphQLTestRequest`, `GraphQLTestResponse` |
| `...testing.internal` | Internal | The context contributor |

## Who uses it

Applications that use `micronaut-graphql-tools` and test their queries, mutations and resolvers in `@MicronautTest`
classes in Java, Kotlin or Groovy.

## Public and internal

- The three types of `io.micronaut.graphql.tools.testing` are public; everything under `internal` is `@Internal`.
- The usage is in the guide page on testing (`src/main/docs/guide/testing.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`). Nothing else at compile time.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) use it. It is test-time code, so it has no native test.
- `graphql-tools-bom` manages its version. It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-testing:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`:
`GraphQLTesterSpec` (the request, the paths and the assertions, against a stub invocation), the architecture rules and
`DocsPageSpec`. The behaviour against a real schema is tested in the three example suites.
