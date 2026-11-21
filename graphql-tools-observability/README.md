# micronaut-graphql-tools-observability

Gradle project `:micronaut-graphql-tools-observability`, folder `graphql-tools-observability/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-observability`.

## Purpose

Request metrics with Micrometer and traces with OpenTelemetry for the GraphQL executions of the library, built on the
conventions of Micronaut Micrometer and Micronaut Tracing. Add the module and the integration you use; a `MeterRegistry` bean
turns the metrics on and an `OpenTelemetry` bean turns the tracing on. Neither library is a dependency of the module: it
compiles against `micronaut-micrometer-core` and `micronaut-tracing-opentelemetry` from their version catalogs, `compileOnly`.

## Responsibility

- One `Instrumentation` bean that implements the request hooks only, so an execution pays nothing for each field. It hosts the
  Micrometer and the OpenTelemetry observers, so the module adds one instrumentation to the chain the library builds.
- The `graphql.request` timer, tagged by operation name (capped, with `other` and `anonymous`) and outcome, and the DataLoader
  counters (`graphql.dataloader.batches`, `.batch.keys`), guarded by `@RequiresMetrics` and
  `micronaut.metrics.binders.graphql.enabled`.
- One span for each request, made with the OpenTelemetry `Instrumenter` API: named after the operation type, with the
  OpenTelemetry GraphQL attributes, and current in the propagated context so that the spans of resolvers are its children.
  Spans for the fields that `micronaut.graphql.tools.tracing.fields` selects, decided once at startup by a
  `FieldFetcherDecorator`. A property read and a subscription field never get a span.
- The configuration: `micronaut.metrics.binders.graphql.enabled` and `.max-operations`, and under
  `micronaut.graphql.tools.tracing` `enabled`, `fields` and `add-operation-name-to-span-name`.
- Per-resolver spans and timers are `@NewSpan`, `@ContinueSpan`, `@SpanTag` and `@Timed` on the resolver methods, which the
  module documents and tests but does not reimplement.

Not in this module: collecting `Instrumentation` beans into the chain, and the DataLoader statistics switch
(`micronaut.graphql.tools.execution.data-loader-statistics`). Those are in `graphql-tools`.

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.observability` | Public API | `GraphQLObservability`: the meter names and tags |
| `...observability.internal` | Internal | The instrumentation, the two observers, the two configuration classes, the field span decorator and the wrapper that traces one |

## Who uses it

Applications that use `micronaut-graphql-tools` and want request metrics, traces or both.

## Public and internal

- `GraphQLObservability` is the only public type. It names things; it creates nothing.
- Every class under `io.micronaut.graphql.tools.observability.internal` is `@Internal`. Do not use it from application code.
- The configuration keys and the span layout are documented in the guide page on observability
  (`src/main/docs/guide/observability.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`). Micronaut Micrometer and Micronaut Tracing (which bring Micrometer and OpenTelemetry) are `compileOnly`.
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
timer and its tags, the cardinality cap (also under concurrent requests), the spans of requests and selected fields, `@NewSpan` and `@Timed` resolvers, the span of the HTTP server as the parent, the beans
the module creates and the architecture rules. `DocsPageSpec` reads `src/main/docs/guide/observability.adoc` and fails when the
page and the constants disagree.
