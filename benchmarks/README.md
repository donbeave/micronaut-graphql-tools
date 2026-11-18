# benchmarks

Gradle project `:benchmarks`, folder `benchmarks/`. Not published.

## Purpose

Measurements of the request hot path and of start-up, plus the allocation gate that protects the hot path.
Performance is a critical property of the library. Maintainers use this module when they change code that a request
runs, or when they claim a performance gain.

## Responsibility

- `src/jmh/java` (package `io.micronaut.graphql.tools.benchmarks`): the JMH benchmarks. They cover property, root and
  type resolver methods, argument conversion, union and interface resolution, a directive-wrapped fetcher, whole queries,
  and start-up (`BindingBenchmark`, `RetainedHeapBenchmark`).
- `src/allocationTest/java`: the allocation gate. It fails when an operation allocates more bytes than its baseline in
  `allocation-baselines.properties`.
- `src/jmhSmokeTest/java`: runs every JMH benchmark once, so that the benchmarks compile and start. Its numbers mean nothing.
- `src/main`: generated sources. The `generateLargeSchema` task generates the large schema that the start-up benchmarks bind.
- `scripts/startup-report.py`: builds one jar per schema size and runs the start-up benchmarks cold, one JVM per sample.

## Who uses it

Maintainers and CI. The CI `check` runs `jmhSmokeTest` and `allocationTest`. No application depends on this module.

## Public and internal

Nothing here is a library API. The module is not in the BOM (`graphql-tools-bom`).

## Relation to other modules

- Depends on `micronaut-graphql-tools` (`implementation`), the library under measurement, and on `micronaut-inject`
  (annotation processing for the benchmark beans).
- JMH is the benchmark framework (`jmh` and `jmhAnnotationProcessor` configurations).
- Nothing depends on this module.

## Build and test

Run from the repository root. JDK 21 or 25.

```
./gradlew :benchmarks:jmh
./gradlew :benchmarks:jmh -Pjmh.profile=quick
./gradlew :benchmarks:jmhSmokeTest
./gradlew :benchmarks:allocationTest
```

- `:benchmarks:jmh` runs two forks with the `gc` profiler. The allocation figure is `gc.alloc.rate.norm`. The start-up
  benchmarks are left out of it: run them with `scripts/startup-report.py`.
- `:benchmarks:allocationTest -Pallocation.update` rewrites the baselines. Review the diff: a higher number is an
  allocation the change added. Use `-PgraphqlJavaLeg=floor` or `ceiling` for the other legs, which have their own
  baselines.
- `:benchmarks:jmhJar` builds the benchmark jar.
- `-PlargeFields=<fields>` generates the start-up schema of another size, one size per build.

The full description, including the cold start procedure, is the "Benchmarks" section of `CONTRIBUTING.md`.
