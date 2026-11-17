# Contributing Code or Documentation to Micronaut

Contributions are accepted under the [Developer Certificate of Origin (DCO)](https://developercertificate.org/). Sign off each commit with `git commit -s`, which adds a `Signed-off-by` line to the commit message. Commits without a `Signed-off-by` line cannot be accepted.

## Finding Issues to Work on

If you are interested in contributing to Micronaut and are looking for issues to work on, take a look at the issues tagged with [help wanted](https://github.com/micronaut-projects/micronaut-graphql-tools/issues?q=is%3Aopen+is%3Aissue+label%3A%22status%3A+help+wanted%22).

## JDK Setup

Micronaut GraphQL Tools builds on JDK 17, 21 or 25; native tests need GraalVM for JDK 21. The library compiles to Java 17 bytecode (`--release 17`), and on JDK 17 only `./gradlew :micronaut-graphql-tools:check` works: the processor module and `test-suite` need JDK 21 or newer, so run the full `./gradlew build` on JDK 21 or 25.

## IDE Setup

Micronaut GraphQL Tools can be imported into IntelliJ IDEA by opening the `build.gradle` file.

## Docker Setup

Micronaut GraphQL Tools tests currently require Docker to be installed.

## Running Tests

To run the tests, use `./gradlew check`.

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the documentation, run `./gradlew publishGuide` (or `./gradlew pG`), then open `build/docs/index.html`

To also build the Javadocs, run `./gradlew docs`.

## Working on the code base

If you use IntelliJ IDEA, you can import the project using the Intellij Gradle Tooling ("File / Import Project" and selecting the "settings.gradle" file).

To get a local development version of Micronaut GraphQL Tools working, first run the `publishToMavenLocal` task.

```
./gradlew pTML
```

You can then reference the version specified with `projectVersion` in `gradle.properties` in a test project's `build.gradle` or `pom.xml`. If you use Gradle, add the `mavenLocal` repository (Maven automatically does this):

```
repositories {
    mavenLocal()
    mavenCentral()
}
```

## Benchmarks

The `benchmarks` module measures the request hot path (D-02): a property fetch, a root method, a type resolver method
with its source, the conversion of scalar, enum, input object and list-of-inputs arguments, union and interface
resolution from four threads, a directive-wrapped fetcher, and a whole query over 1,000 and 10,000 objects with four
fields each. A gain is claimed only with a figure from these benchmarks.

Two checks run on every `./gradlew check`:

- `:benchmarks:jmhSmokeTest` runs every JMH benchmark once in the test JVM (no fork, single shot), so the benchmarks
  compile and start. Its numbers mean nothing.
- `:benchmarks:allocationTest` fails when an operation allocates more than its baseline in
  `benchmarks/allocation-baselines.properties`. It measures the bytes the calling thread allocates per operation
  (`getCurrentThreadAllocatedBytes`), discards the first round and takes the minimum of the others. Each operation runs
  in its own JVM, compiled by C1 only: the figure is every allocation the code performs, before the escape analysis of
  C2 removes some of them, so it does not change from one run, machine or JDK patch level to the next. (JMH's
  `gc.alloc.rate.norm` is the C2 steady state, so it is lower for some operations.) A path the library owns may allocate its baseline plus 0.5 bytes, so a path with a baseline of 0
  stays at 0. A whole query may allocate 5 percent more than its baseline, and only on the default graphql-java leg.
  Wall-clock time is never gated.

To measure, run `./gradlew :benchmarks:jmh` (two forks, the `gc` profiler; the allocation figure is
`gc.alloc.rate.norm` in bytes per operation) or `./gradlew :benchmarks:jmh -Pjmh.profile=quick` for a short run. Narrow
it with `-Pjmh.includes=<regex>`, and pick the graphql-java leg with `-PgraphqlJavaVersion=25.1`. The results are in
`benchmarks/build/results/jmh/`. Run on an idle machine with a JDK the CI uses (Temurin 21 or 25), and quote the figures with
the JDK, the graphql-java leg and the error that JMH prints.

When an allocation changes on purpose, record the new baselines of both legs and commit the file; the diff is the
review of the change:

```
./gradlew :benchmarks:allocationTest -Pallocation.update
./gradlew :benchmarks:allocationTest -Pallocation.update -PgraphqlJavaVersion=25.1
```

Baselines are keyed by operation, graphql-java leg and JDK feature version, and are recorded for JDK 21 and JDK 25 (the two
full CI legs); record the ones for the JDK you run with, and for both when a change is intended. There are none for JDK 17,
where the gate is skipped (the module is not built there). Run the update on 21 and on 25 for both legs.

## Creating a pull request

Once you are satisfied with your changes:

- Commit your changes in your local branch
- Push your changes to your remote branch on GitHub
- Send us a [pull request](https://help.github.com/articles/creating-a-pull-request)

## Checkstyle

We want to keep the code clean, following good practices about organization, Javadoc, and style as much as possible.

Micronaut GraphQL Tools uses [Checkstyle](https://checkstyle.sourceforge.io/) to make sure that the code follows those standards. The configuration is defined in `config/checkstyle/checkstyle.xml`. To execute Checkstyle, run:

```
./gradlew <module-name>:checkstyleMain
```

Before starting to contribute new code we recommended that you install the IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use Micronaut's checkstyle configuration file.

IntelliJ will mark in red the issues Checkstyle finds. For example:

![](https://github.com/micronaut-projects/micronaut-core/raw/master/src/main/docs/resources/img/checkstyle-issue.png)

In this case, to fix the issues, we need to:

- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add an space after `if` in line 34

The plugin also adds a new tab in the bottom of the IDE to run Checkstyle and show errors and warnings. We recommend that you run the report and fix all issues before submitting a pull request.

## Releases

Nothing is published automatically. There are no release, snapshot or Maven Central workflows; the only CI workflow is `.github/workflows/gradle.yml`, which builds and tests. Releases are cut by the maintainers by hand.
