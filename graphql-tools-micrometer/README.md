# micronaut-graphql-tools-micrometer

Gradle project `:micronaut-graphql-tools-micrometer`, folder `graphql-tools-micrometer/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-micrometer`.

## Purpose

Request metrics with Micrometer for the GraphQL executions of the library, built on the conventions of Micronaut Micrometer.
Add the module and a `MeterRegistry` bean (Micronaut Micrometer provides it) and the metrics appear. Micrometer is not a
dependency of the module: it compiles against `micronaut-micrometer-core` from its version catalog, `compileOnly`. The spans are
in the sibling module `graphql-tools-opentelemetry`.

## Responsibility

- One `RequestObserver` bean (`@Internal`, from `graphql-tools`) that records the metrics. The library has exactly one
  `Instrumentation` bean for all observers, so the module adds nothing to the chain by itself and nothing per field.
- The `graphql.request` timer, tagged by operation name (capped, with `other` and `anonymous`) and outcome, and the DataLoader
  counters (`graphql.dataloader.batches`, `.batch.keys`), guarded by `@RequiresMetrics` and
  `micronaut.metrics.binders.graphql.enabled`.
- The configuration: `micronaut.metrics.binders.graphql.enabled` and `.max-operations`.
- A timer for one resolver is `@Timed` on the method, which the module documents and tests but does not reimplement.

Not in this module: the `Instrumentation` bean and the observer interface, and the DataLoader statistics switch
(`micronaut.graphql.tools.execution.data-loader-statistics`). Those are in `graphql-tools`. No class here wraps a data fetcher.

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.micrometer` | Public API | `GraphQLMetrics`: the meter names and tags |
| `...micrometer.internal` | Internal | The observer, the cap on operation names and the configuration class |

## Who uses it

Applications that use `micronaut-graphql-tools` and want request metrics.

## Public and internal

- `GraphQLMetrics` is the only public type. It names things; it creates nothing.
- Every class under `io.micronaut.graphql.tools.micrometer.internal` is `@Internal`. Do not use it from application code.
- The configuration keys are documented in the guide page on observability (`src/main/docs/guide/observability.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`). Micronaut Micrometer (which brings Micrometer) is `compileOnly`. It does not depend on
  `graphql-tools-opentelemetry`.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and test the timer from the schema to the meter
  registry, on the JVM and, for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. `benchmarks` measures the per-field cost of a request with the module on.
- It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-micrometer:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`: the
timer and its tags, the cardinality cap (also under concurrent requests), `@Timed` resolvers, the DataLoader counters, the
beans the module creates and the architecture rules. `DocsPageSpec` reads `src/main/docs/guide/observability.adoc` and fails
when the page and the constants disagree.
