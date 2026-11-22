# micronaut-graphql-tools-opentelemetry

Gradle project `:micronaut-graphql-tools-opentelemetry`, folder `graphql-tools-opentelemetry/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-opentelemetry`.

## Purpose

Request and field spans with OpenTelemetry for the GraphQL executions of the library, built on the conventions of Micronaut
Tracing. Add the module and an `OpenTelemetry` bean (Micronaut Tracing provides it) and the spans appear. OpenTelemetry is not a
dependency of the module: it compiles against `micronaut-tracing-opentelemetry` from its version catalog, `compileOnly`. The
metrics are in the sibling module `graphql-tools-micrometer`.

## Responsibility

- One `RequestObserver` bean (`@Internal`, from `graphql-tools`) that starts the request span. The library has exactly one
  `Instrumentation` bean for all observers, so the module adds nothing to the chain by itself and nothing per field.
- One span for each request, made with the OpenTelemetry `Instrumenter` API: named after the operation type, with the
  OpenTelemetry GraphQL attributes, and current in the propagated context so that the spans of resolvers are its children.
- The field-wrapping classes, which live only in this module: spans for the fields that `micronaut.graphql.tools.tracing.fields`
  selects, decided once at startup by a `FieldFetcherDecorator`. A property read and a subscription field never get a span.
- The configuration under `micronaut.graphql.tools.tracing`: `enabled`, `fields` and `add-operation-name-to-span-name`.
- Per-resolver spans are `@NewSpan`, `@ContinueSpan` and `@SpanTag` on the resolver methods, which the module documents and
  tests but does not reimplement.

Not in this module: the `Instrumentation` bean and the observer interface. Those are in `graphql-tools`.

## Packages

Each package has a `package-info.java`. See the [API map](../src/main/docs/guide/apiMap.adoc).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.opentelemetry.internal` | Internal | The request span observer, the field span decorator and the wrapper that traces one, the propagated context element and the configuration class |

## Who uses it

Applications that use `micronaut-graphql-tools` and want request and field spans.

## Public and internal

- The module has no public type: every class under `io.micronaut.graphql.tools.opentelemetry.internal` is `@Internal`. Do not
  use it from application code.
- The configuration keys and the span layout are documented in the guide page on observability
  (`src/main/docs/guide/observability.adoc`).

## Relation to other modules

- Depends on `graphql-tools` (`api`). Micronaut Tracing (which brings OpenTelemetry) is `compileOnly`. It does not depend on
  `graphql-tools-micrometer`, which is a test dependency only, to test both modules together.
- The test suites (`test-suite-java`, `-kotlin`, `-groovy`) depend on it and test the spans from the schema to the span
  exporter, on the JVM and, for Java and Kotlin, as a native binary.
- `graphql-tools-bom` manages its version. `benchmarks` measures the per-field cost of a request with the module on.
- It is published to GitHub Packages with the other library modules.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-opentelemetry:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The tests are Spock specs in `src/test/groovy`: the
spans of requests and selected fields, `@NewSpan` resolvers, the span of the HTTP server as the parent, the beans the library
creates with each module alone and with both (exactly one `Instrumentation`) and the architecture rules. `DocsPageSpec` reads
`src/main/docs/guide/observability.adoc` and fails when the page and the keys disagree.
