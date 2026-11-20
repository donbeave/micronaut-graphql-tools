# micronaut-graphql-tools-observability

Gradle project `:micronaut-graphql-tools-observability`, folder `graphql-tools-observability/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-observability`.

## Purpose

Request metrics with Micrometer and traces with OpenTelemetry for the GraphQL executions of the library. Add the module and
the library you use; a `MeterRegistry` bean turns the metrics on and an `OpenTelemetry` bean turns the tracing on. Neither
library is a dependency of the module.

## Responsibility

- One `Instrumentation` bean that implements the request hooks only, so an execution pays nothing for each field. It hosts the
  Micrometer and the OpenTelemetry observers, so the module adds one instrumentation to the chain the library builds.
- The `graphql.request` timer, tagged by operation name (capped, with `other` and `anonymous`) and outcome, and the DataLoader
  counters (`graphql.dataloader.batches`, `.batch.keys`).
- One span for each request, and spans for the fields that `micronaut.graphql.tools.observability.tracing.fields` selects,
  decided once at startup by a `GraphQLSchemaCustomizer`. A property read and a subscription field never get a span.
- The configuration under `micronaut.graphql.tools.observability`: `enabled`, `metrics.enabled`, `metrics.max-operations`,
  `tracing.enabled` and `tracing.fields`.

Not in this module: collecting `Instrumentation` beans into the chain, and the DataLoader statistics switch
(`micronaut.graphql.tools.execution.data-loader-statistics`). Those are in `graphql-tools`.

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.observability` | Public API | `GraphQLObservability`: the meter names, tags and span attributes |
| `...observability.internal` | Internal | The instrumentation, the two observers, the settings, the selection of traced fields and the wrapper that traces one |

## Who uses it

Applications that use `micronaut-graphql-tools` and want request metrics, traces or both.

## Public and internal

- `GraphQLObservability` is the only public type. It names things; it creates nothing.
- Every class under `io.micronaut.graphql.tools.observability.internal` is `@Internal`. Do not use it from application code.
- The configuration keys and the span layout are documented in the guide page on observability
  (`src/main/docs/guide/observability.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`). Micrometer and OpenTelemetry are `compileOnly`.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and test the timer and the spans from the schema to the
  meter registry and the span exporter, on the JVM and, for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. `benchmarks` measures the per-field cost of a request with the module on.
- It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-observability:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`: the
timer and its tags, the cardinality cap (also under concurrent requests), the spans of requests and selected fields, the beans
the module creates and the architecture rules. `DocsPageSpec` reads `src/main/docs/guide/observability.adoc` and fails when the
page and the constants disagree.
