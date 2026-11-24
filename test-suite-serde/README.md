# test-suite-serde

Gradle project `:test-suite-serde`, folder `test-suite-serde/`. Not published.

## Purpose

The null-field checks (#45) on the Micronaut Serialization stack (`micronaut-serde-jackson`). That stack makes its
`JsonMapper` win over Jackson databind, so the two cannot share a classpath: `test-suite-java` runs the Jackson databind
stack, this module the Serde stack.

## Responsibility

- `src/main/java/example`: a small application (schema in `src/main/resources/schema.graphqls`) with the resolvers the
  checks query.
- `src/test/java/example/nullfields`: raw HTTP checks that `null` values keep their keys, with the default inclusion,
  `NON_NULL` and `ALWAYS` (`micronaut.serde.serialization.inclusion`).

## Packages

Example application code, not a library package: no `package-info.java`. Nothing here is API.

## Who uses it

Maintainers and CI.

## Public and internal

Nothing here is a library API. Do not import from `example.*` in application code.

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`) and puts `micronaut-graphql-tools-processor` on its
  `annotationProcessor` path.
- `test-suite-java` runs the same checks on the Jackson databind stack; the two modules share no code.

## Build and test

Run from the repository root. JDK 21 or 25 (Oracle GraalVM 25 for the native test).

```
./gradlew :test-suite-serde:test
./gradlew :test-suite-serde:nativeTest --no-parallel
```

Add `-PgraphqlJavaLeg=floor|ceiling` to run on another graphql-java leg.
