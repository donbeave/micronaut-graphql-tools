# micronaut-graphql-tools-scalars

Gradle project `:micronaut-graphql-tools-scalars`, folder `graphql-tools-scalars/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-scalars`.

## Purpose

The standard scalars that most services need, ready to use: the `java.time` types, JSON and a selection of the extended
scalars. Add the module and declare the scalar in the schema (`scalar LocalDate`); the mapping is already a bean.
A scalar has a configurable name and, for `java.time`, a configurable wire format.

## Responsibility

- One `GraphQLScalarMapping` bean for each scalar of `StandardScalar`: `Instant`, `LocalDate`, `LocalTime`,
  `LocalDateTime`, `OffsetTime`, `OffsetDateTime`, `ZonedDateTime`, `YearMonth`, `Year`, `Duration`, `Period`, `JSON`,
  `Url`, `UUID`, `Locale`, `Currency` and the signed `Int` and `Float` scalars.
- The coercions of the `java.time` scalars (ISO-8601 by default, `epoch-seconds`, `epoch-millis`, `seconds`, `millis` or a
  `DateTimeFormatter` pattern) and of the JSON scalar.
- The configuration under `micronaut.graphql.tools.scalars`: `enabled`, and `<id>.enabled`, `<id>.name` and `<id>.format`
  for each scalar. A value that a scalar cannot use fails the startup with a message that names the key.
- The JSON scalar converts a value that is not JSON yet with the application's `JsonMapper` (Micronaut Serialization in an
  application that uses it). The module uses no Jackson and brings no JSON implementation.
  It rejects a number that is not finite (NaN, infinity) at any depth of a map or a list, in output and in variables, and a
  float that is not zero but underflows to zero as a `double` (for example `1e-10000`). It writes an integral `Number`
  (`AtomicLong`, `LongAdder` and the like) as an exact integer literal; an unknown `Number` goes through its decimal text.

Not in this module: the scalar SPI (`GraphQLScalarMapping`) and the binding of scalars to Java types. Those are in
`graphql-tools`.

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.scalars` | Public API | `StandardScalar`: the ids, default names and Java classes |
| `...scalars.internal` | Internal | The factory of the mapping beans, the reader of the configuration, the shared coercion |
| `...scalars.internal.json` | Internal | The JSON scalar |
| `...scalars.internal.time` | Internal | The `java.time` scalars and the builder that picks one from its format |

## Who uses it

Applications that use `micronaut-graphql-tools` and have `java.time`, JSON or extended scalars in their schema.

## Public and internal

- `StandardScalar` is the only public type. It describes the scalars; it does not create them.
- Every class under `io.micronaut.graphql.tools.scalars.internal` is `@Internal`. Do not use it from application code.
- The configuration keys and the formats are documented in the guide page on scalars (`src/main/docs/guide/scalars.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`) for `GraphQLScalarMapping`, on graphql-java-extended-scalars (`api`, the version
  that `micronaut-graphql` manages) and on `micronaut-json-core` (`api`) for the `JsonMapper`.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and test the scalars from the schema to the JSON
  of the response, on the JVM and, for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. `benchmarks` measures the allocation of its coercions.
- It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-scalars:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`:
a table of values for each `java.time` scalar, the binding of a schema, the JSON scalar on Micronaut Serialization, the
configuration errors and the architecture rules. `DocsPageSpec` reads `src/main/docs/guide/scalars.adoc` and fails when
the table of the page and `StandardScalar` disagree.
