# test-suite-serde

Gradle project `:test-suite-serde`, folder `test-suite-serde/`. Not published.

## Purpose

The null-field checks (#45) and the checks of the GraphQL client (#685) on the Micronaut Serialization stack (`micronaut-serde-jackson`). The client needs Serde, so it runs here and not in `test-suite-java`. That stack makes its
`JsonMapper` win over Jackson databind, so the two cannot share a classpath: `test-suite-java` runs the Jackson databind
stack, this module the Serde stack.

## Responsibility

- `src/main/java/example`: a small application (schema in `src/main/resources/schema.graphqls`) with the resolvers the
  checks query.
- `src/test/java/example/nullfields`: raw HTTP checks that `null` values keep their keys, with the default inclusion,
  `NON_NULL` and `ALWAYS` (`micronaut.serde.serialization.inclusion`).
- `src/test/java/example/client`: `GraphQLHttpClient` against the GraphQL endpoint of this application: typed and dynamic
  queries, GET, a mutation with a union, the 405 of a mutation over GET, request and field errors, `@ClientFilter(serviceId)`
  and the blocking call on an event loop and on a worker. They run in the native test too.

## Packages

Example application code, not a library package: no `package-info.java`. Nothing here is API.

## Who uses it

Maintainers and CI.

## Public and internal

Nothing here is a library API. Do not import from `example.*` in application code.

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`), on `micronaut-graphql-tools-client` (`testImplementation`) and puts `micronaut-graphql-tools-processor` on its
  `annotationProcessor` path.
- `test-suite-java` runs the same checks on the Jackson databind stack; the two modules share no code.

## Build and test

Run from the repository root. JDK 21 or 25 (Oracle GraalVM 25 for the native test).

```
./gradlew :test-suite-serde:test
./gradlew :test-suite-serde:nativeTest --no-parallel
```

Add `-PgraphqlJavaLeg=floor|ceiling` to run on another graphql-java leg.
