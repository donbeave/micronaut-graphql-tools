# Contributing Code or Documentation to Micronaut GraphQL Tools

This is not an official Micronaut module yet. It lives in `donbeave/micronaut-graphql-tools` and is intended for later contribution to the Micronaut Foundation.

Contributions are accepted under the [Developer Certificate of Origin (DCO)](https://developercertificate.org/). Sign off each commit with `git commit -s`, which adds a `Signed-off-by` line to the commit message. Commits without a `Signed-off-by` line cannot be accepted.

## Finding Issues to Work on

Open issues are listed at [donbeave/micronaut-graphql-tools/issues](https://github.com/donbeave/micronaut-graphql-tools/issues). Look for issues labelled [help wanted](https://github.com/donbeave/micronaut-graphql-tools/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22) or [good first issue](https://github.com/donbeave/micronaut-graphql-tools/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22).

## JDK Setup

Micronaut GraphQL Tools builds on JDK 17, 21 or 25; native tests need Oracle GraalVM 25. The library compiles to Java 17 bytecode (`--release 17`), and on JDK 17 only `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check` works: the processor module and the `test-suite-*` modules need JDK 21 or newer, so run the full `./gradlew build` on JDK 21 or 25.

The tools are pinned in `mise.toml` and installed with [mise](https://mise.jdx.dev): run `mise install` in the project directory. It installs Oracle GraalVM 25 (the build JDK, and `native-image` for the native tests) and `jactionlint` (a fork of actionlint that lints the workflows and composite actions: run `jactionlint` in the project directory; CI runs the same command in the `actionlint` job, with the default profile, which adds the security rules). It is pinned in `mise.toml`; the [jactionlint documentation](https://jactionlint.jdx.dev) lists the other install methods. To build on another JDK line, select an overlay with `MISE_ENV`, for example `export MISE_ENV=jdk21` and then `mise install` (or one command at a time: `MISE_ENV=jdk21 mise exec -- ./gradlew build`; `mise install` alone does not activate the tools, so keep `MISE_ENV` set, or use `mise exec`, when you run Gradle) (`jdk17`, `jdk21`, `jdk25` and `jdk27` install Temurin; JDK 27 is for the canary only). Gradle is not installed by mise: use `./gradlew`.

## IDE Setup

Micronaut GraphQL Tools can be imported into IntelliJ IDEA by opening the project directory (or `settings.gradle.kts`).

## Running Tests

The tests do not need Docker. These are the commands CI runs (all in `.github/workflows/gradle.yml`, workflow `Java CI`):

- `./gradlew build` builds everything and runs every check and test on the default graphql-java leg (JDK 21 or 25).
- `./gradlew build -PgraphqlJavaLeg=floor|ceiling` runs the same build on another graphql-java leg (`default` is the default; `latest` is for the canary). The versions of each role are in `gradle/libs.versions.toml`; the README explains the roles. `-PgraphqlJavaCompileLeg=default` compiles main code on the default leg while the tests run on the leg of `-PgraphqlJavaLeg` (the binary compatibility job).
- `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check` checks the library and its binding module only; this is the one that works on JDK 17.
- `./gradlew :test-suite-java:nativeTest :test-suite-java:nativeSerdeTest :test-suite-kotlin:nativeTest --no-parallel` runs the native image tests (the build runs tasks in parallel by default, and each native build needs about 5 GB) (Oracle GraalVM 25); add `-PgraphqlJavaLeg=ceiling` for another leg.

`.github/workflows/gradle.yml` is the source of truth if this list and the workflow ever disagree.

The binding corpus (#142) is a directory of data cases under `graphql-tools-processor/src/test/resources/corpus/`; its README says how to add and update a case (`./gradlew :micronaut-graphql-tools-processor:corpusUpdate`, and read the diff). It runs as part of `test` on the default graphql-java leg, on JDK 21 and 25 (about 70 s); the floor and ceiling legs skip it with a message, because its verdicts do not depend on the leg. It never runs on JDK 17 (the processor module needs JDK 21). The Kotlin cases are a separate task, `./gradlew :micronaut-graphql-tools-processor:corpusKotlin`, not part of `check` or CI: it runs one Gradle TestKit build (Kotlin 2.1.21, KSP2 2.1.21-2.0.2) that downloads the Kotlin and KSP Gradle plugins from the Gradle plugin portal and the processor from Maven Central, so it needs the network (about 2 to 3 minutes with a warm Gradle home).

The example applications are three separate Gradle projects with the same schema, resolvers and smoke tests over HTTP:

| Suite | Language | Run the JVM tests | Run the native tests (Oracle GraalVM 25) |
| --- | --- | --- | --- |
| `test-suite-java` | Java (annotation processors) | `./gradlew :test-suite-java:test` | `./gradlew :test-suite-java:nativeTest` |
| `test-suite-kotlin` | Kotlin 2.1.21 with KSP2 (Java and Kotlin target 21) | `./gradlew :test-suite-kotlin:test` | `./gradlew :test-suite-kotlin:nativeTest` |
| `test-suite-groovy` | Groovy (Spock) | `./gradlew :test-suite-groovy:test` | no native test: `./gradlew :test-suite-groovy:nativeSmokeTest` (see below) |

Each native test takes about 5 to 7 minutes and 5 GB of memory: run them one after another.

The three schemas (`src/main/resources/schema.graphqls`) are copies; `./gradlew :test-suite-java:checkSuiteSchemaParity` (part of `check`) fails when they differ.

Groovy has no native test, because Spock cannot run natively: its extensions are loaded reflectively and it ships no native metadata (D-38). The Groovy application is smoke-tested natively instead. `./gradlew :test-suite-groovy:nativeSmokeTest` (Oracle GraalVM 25, about 5 minutes and 5 GB) builds the native executable with `:test-suite-groovy:nativeCompile`, starts it on a free port, posts the queries of the Java and Kotlin smoke tests to `/graphql` (`src/nativeSmoke/groovy/NativeSmoke.groovy`), asserts the response bodies and stops the process; add `-PgraphqlJavaLeg=ceiling` for another leg. Groovy 4.0.x ships no GraalVM metadata, so the reachability metadata the Groovy runtime needs is in `test-suite-groovy/src/main/resources/META-INF/native-image`: the `invokedynamic` call sites are off, the example code is `@CompileStatic`, SAM arguments are Java lambdas (a closure passed as a SAM type is coerced at run time, which the image cannot do), and each closure class that a Groovy method (`findAll`, `collect`, ...) calls is listed in `reflect-config.json`; add one when you add such a closure, the smoke test fails without it. The Spock suite itself runs on the JVM only.

Responses keep their null fields (#45): `example.nullfields` sends raw HTTP requests and checks that `null` values keep their keys, under each JSON configuration. The default stack (Jackson databind, with and without `jackson.serialization-inclusion=NON_NULL`) runs in `test`, so `nativeTest` runs it too. Micronaut Serialization (`micronaut-serde-jackson`, with the default inclusion, `NON_NULL` and `ALWAYS` set through `micronaut.serde.serialization.inclusion`) runs in its own source set and task, `./gradlew :test-suite-java:serdeTest` (part of `check`), because `micronaut-serde-jackson` makes its `JsonMapper` win and the two stacks cannot share a test classpath. `./gradlew :test-suite-java:nativeSerdeTest` runs the same classes as a native binary (the `native` CI job runs it). The guard test `usesTheExpectedJsonStack` asserts the mapper class in both runs. The native-image Gradle plugin builds the image classpath of every test binary from `testRuntimeClasspath`, which has no `micronaut-serde`; `test-suite-java/build.gradle.kts` therefore adds the serde source set's runtime classpath to `nativeImageSerdeTestClasspath`. Without that the image has no Serde bean definitions and selects `JacksonDatabindMapper`.

### The execution-behaviour TCK

`graphql-tools-tck` holds the execution behaviour the library promises (argument binding, enums, null handling, return types, errors and `data: null`, error mapping through a `DataFetcherExceptionHandler`, abstract types, DataLoaders, bean scopes, introspection, variables, aliases, fragments) as scenarios that are written once and run in all three suites: `example.tck.GraphQLToolsTckTest` in `test-suite-java` and `test-suite-kotlin` (JUnit 5, also under `nativeTest`) and `example.tck.GraphQLToolsTckSpec` in `test-suite-groovy` (Spock, JVM only). `./gradlew :graphql-tools-tck:test` checks the scenario files themselves (listed, complete, well-formed JSON) without a suite; the three suites run the scenarios: `./gradlew :test-suite-java:test --tests 'example.tck.*'` (and the same for Kotlin and Groovy). The whole TCK (8 scenarios) takes about 1.5 to 7 seconds in each suite on a laptop, starting one application per scenario; the first scenario of a JVM carries about 4.7 seconds of warm-up, the others 0.1 to 0.4 seconds each.

A scenario is a directory under `graphql-tools-tck/src/main/resources/tck/`:

| File | Content |
| --- | --- |
| `schema.graphqls` | `extend type Query` (and `extend type Mutation`) with the fields of the scenario, plus its own types. Every suite serves its example schema and merges this file into it, so the type names must not clash with the example's (`Book`, `User`, `Error`, ...). |
| `config.properties` | Optional: Micronaut properties for the application of this scenario (for example `micronaut.graphql.tools.input.detect-omission=true`). |
| `cases.txt` | The cases, one name per line, run in this order against one application: state (a counter, a DataLoader's statistics) carries over from case to case. |
| `<case>.graphql` | The operation document, sent as a raw HTTP POST to `/graphql`. |
| `<case>.variables.json` | Optional: the variables. |
| `<case>.operation` | Optional: the `operationName`. |
| `<case>.expected.json` | The complete response body: `data`, `errors` with `message`, `locations`, `path` and `extensions`. Objects are compared by key, arrays by position, numbers by value. The string `"@any"` matches any string: use it for the message of an error whose wording is graphql-java's and changes between its versions, never for the data. |

To add a scenario:

1. Create the directory with the files above and list its id in `tck/scenarios.txt`. `./gradlew :graphql-tools-tck:test` fails on a missing, unlisted or malformed file.
2. In each suite add the fixtures (resolvers, types, loaders) in `src/test/{java,kotlin,groovy}/example/tck/<Scenario>Scenario.*`. Every fixture bean carries `@Requires(property = "tck.scenario", value = "<id>")`, so that only the scenario that runs has resolvers; classes of unions and interfaces are mapped by a `TckTypeMappings` bean with the same condition. Names stay unique across scenarios, because the fixtures of all scenarios are on one classpath.
3. Run the three suites. A scenario without fixtures in a suite fails there when the application starts. A suite that cannot have the fixtures lists the id and a reason in `src/test/resources/tck-exemptions.txt` (`<id> <reason>`); the list only shrinks: the meta-test `exemptionsAreCurrent` fails on an id that is no scenario, and on one whose scenario passes. No suite has an exemption today.

The fixtures serve a plain `DataLoaderRegistry` in every scenario except the ones that set `tck.registry=per-request` (`data-loader`). The example's registry is request scoped, and a request-scoped bean has no request on the thread that completes a `CompletionStage`: the call fails with `No request present` (the per-execution context and lazy DataLoader registry of #44 and #20 are the fix, and this scenario setting goes away with them).

Out of scope here: HTTP transport details and WebSocket scenarios (#50), subscriptions (a `Publisher` return type is allowed on subscription fields only), and per-request context propagation.

## Dependencies

Dependencies come from the Micronaut version catalog (`mn.*`) first; `gradle/libs.versions.toml` only holds what Micronaut does not manage; overrides need a recorded reason.

- A dependency that Micronaut's BOMs manage is declared without a version, or through the generated `mn`, `mnGraphql` and `mnTest` catalogs. `logback` and JUnit come from the Micronaut platform BOM (`libs.micronaut.platform`) in the tests and the benchmarks.
- The `micronaut`, `micronaut-platform` and `micronaut-test` entries of `gradle/libs.versions.toml` are inputs of micronaut-build and the Micronaut Gradle plugin, not dependency pins.
- An override of a managed version (today only Groovy 4.0.33, because the BOM's 4.0.28 cannot read JDK 27 class files) needs a comment with its reason next to it and an exception in `.github/scripts/versions-audit.sh`.

## Building Documentation

The documentation sources are located at `src/main/docs/guide`.

To build the guide and the Javadocs, run `./gradlew docs`, then open `build/docs/index.html`. To build only the guide, run `./gradlew publishGuide` (or `./gradlew pG`); the result is in `build/docs/guide/index.html`. Nothing is published. The `docs (Java 25)` CI job runs `./gradlew docs` on every pull request and uploads `build/docs` as the `guide` workflow artifact.

### Adding a code example

Every code example in the guide is compiled code in all three languages. A page includes it with the `snippet::` macro:

```
snippet::example.graphql.query.AllBooksQuery[project-base="test-suite", source="main", tags="resolver", indent="0"]
```

The macro looks for the class in `test-suite-java`, `test-suite-kotlin` and `test-suite-groovy` (`project-base` names them), under `src/main` (`source="main"`, use the default `test` for specs), and renders one tab per language. To add an example:

1. Add the class, with the same fully qualified name, to all three suites.
2. Wrap the lines to show in `// tag::name[]` and `// end::name[]` comment lines, with the same tag name in all three files.
3. Include it with `snippet::<fully qualified class name>[project-base="test-suite", source="main", tags="name", indent="0"]`.

A missing file or tag does not fail `./gradlew docs`: the macro prints a warning (`NO FILE FOUND`, `tag ... not found in include file`) and renders the tab without it; an unclosed tag logs `detected unclosed tag` or `mismatched end tag`, an unclosed block logs `unterminated ... block`. The CI job fails when the log contains one of these warnings, so check the log when you run `./gradlew docs` locally.

### Documentation coverage checks

`DocsCoverageSpec` (in the `graphql-tools` module, so `./gradlew :micronaut-graphql-tools:test` and `check` run it) fails when the documentation has a gap:

- Every configuration key under `micronaut.graphql.tools` (read from the generated configuration metadata) is named in the guide or in `README.md`, as the full dotted key, for example `micronaut.graphql.tools.eager-init`.
- Every public exception in `io.micronaut.graphql.tools.exceptions` and every public annotation in `io.micronaut.graphql.tools.annotation` is named in the guide or in `README.md`. The failure message names the item and the page to edit.
- Every `snippet::` in the guide (except those with an explicit `project=`) has its `.java`, `.kt` and `.groovy` file in the three suites, with every tag named by the macro.

`ProcessorOptionsDocsSpec` (in the `graphql-tools-processor` module) fails when a compile-time processor option is not named in `src/main/docs/guide/compileTime/options.adoc`. Its allowlist is `graphql-tools-processor/src/test/resources/docs-coverage-allowlist.txt`, with the same rules.

A deliberate gap goes into `graphql-tools/src/test/resources/docs-coverage-allowlist.txt` with a reason and a tracker issue number. The list may only shrink: the spec fails on an entry whose item is documented now or no longer exists. A new key, exception or annotation needs documentation in the same pull request.

## Working on the code base

To get a local development version of Micronaut GraphQL Tools working, first run the `publishToMavenLocal` task. This publishes to your local Maven repository only; nothing is published anywhere else.

```
./gradlew pTML
```

You can then reference the version specified with `projectVersion` in `gradle.properties` in a test project's build file or `pom.xml`. If you use Gradle, add the `mavenLocal` repository (Maven automatically does this):

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
fields each. `BindingBenchmark` measures start-up: it starts a fresh application context and binds a generated schema of
100, 1,600, 3,000, 10,000 or 20,000 fields (the `fields` parameter: 10, 160, 300, 1,000 or 2,000 object types of 10 fields, generated by the
`generateLargeSchema` task), with the binding manifest recorded or not (`micronaut.graphql.tools.manifest.record`), one
invocation per context. `RetainedHeapBenchmark` takes the same parameters and reports the heap a started context keeps
as the secondary result `retainedBytes`: the heap in use after a full collection with the context open, minus the heap
in use before the start. JMH's text summary adds the forks up for a secondary result, so the figure per fork is read from
the JSON. Both are measured cold, one fresh JVM per sample, only by `scripts/startup-report.py` (see below): the
`./gradlew :benchmarks:jmh` task excludes them, because its warm-up would start contexts in the same JVM first.

The benchmark jar generates the 1,600-field schema only, as before, so the other start-up benchmarks bind the same
schema as before. Each other size is generated into a jar of its own (`-PlargeFields=<fields>`, one size per build), so
that the JVM of a size has no other size on its classpath. `jmhSmokeTest` generates the 100 and 10,000-field schemas in
its own source set and runs those two sizes only. Measured: the 100-field context retained 14.4 MB with all three sizes on the
classpath and 4.7 MB with the 100-field classes alone. The 10,000-field schema is a chain of 1,000 types, and binding
recurses once per type of the chain: the default thread stack overflows, so the benchmark JVMs run with `-Xss16m`. This
is a workaround for #297, which removes the recursion; remove the flag when #297 lands (`jvmArgs` in
`benchmarks/build.gradle.kts`, and `-jvmArgs -Xss16m` for the jar). `ContextStartBenchmark`, `FirstRequestBenchmark` and
`StartAndFirstRequestBenchmark` separate the binding at startup from the first request: the same schema with
`micronaut.graphql.tools.eager-init` `true` or `false`, timing the context start, the first query after the start, and
both together (`./gradlew :benchmarks:jmhJar`, then run the jar with `-f 10 -wi 0 -i 1 -bm ss -jvmArgs -Xss16m` for a cold
JVM per sample; the jar leaves `BindingBenchmark` and `RetainedHeapBenchmark` out unless one is named, because it
generates the 1,600-field schema only). None has an allocation gate: a whole context start is not a path the library owns. A gain is claimed
only with a figure from these benchmarks.

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
it with `-Pjmh.includes=<regex>`, and pick the graphql-java leg with `-PgraphqlJavaLeg=ceiling` (`floor`, `default` or `ceiling`). The results are in
`benchmarks/build/results/jmh/`. Run on an idle machine with a JDK the CI uses (Temurin 21 or 25), and quote the figures with
the JDK, the graphql-java leg and the error that JMH prints.

The start-up figures have one command. `python3 benchmarks/scripts/startup-report.py` builds one jar per schema size
and runs `BindingBenchmark` and `RetainedHeapBenchmark` on each, with ten cold JVMs per case (`--forks` changes that). It
prints, for each case, the median binding time with the minimum and maximum, and the median `retainedBytes`, all taken
over the per-fork values in the JMH JSON. `--json <file>` keeps the JSON, `--previous <file>` prints the change against
an earlier JSON, and `--from <file>` reports a JSON without running anything. The report prints the JDK, so quote it with
the figures. The last jar it builds is a one-size jar: run `./gradlew :benchmarks:jmhJar` again before the other benchmarks.

The `Startup benchmark` workflow (`.github/workflows/startup-benchmark.yml`) runs the command every night and on demand
(Actions, "Run workflow"). It archives the JSON as the `startup-benchmark` artifact (90 days), prints the medians and the
change against the last successful run in the job summary, and is never part of the pull request gate: the figures are
reported, not gated. Hosted runners are shared, so read the retained heap first and treat a binding-time change inside
the spread of the forks as noise. To compare a change with master, run the command on each checkout with `--json` and
pass the master JSON to `--previous`.

When an allocation changes on purpose, record the new baselines of the three legs (`floor`, `default`, `ceiling`) and commit the file; the diff is the
review of the change:

```
./gradlew :benchmarks:allocationTest -Pallocation.update
./gradlew :benchmarks:allocationTest -Pallocation.update -PgraphqlJavaLeg=floor
./gradlew :benchmarks:allocationTest -Pallocation.update -PgraphqlJavaLeg=ceiling
```

Baselines are keyed by operation, graphql-java leg and JDK feature version, and are recorded for JDK 21 and JDK 25 (the two
full CI legs); record the ones for the JDK you run with, and for both when a change is intended. There are none for JDK 17,
where the gate is skipped (the module is not built there). Run the update on 21 and on 25 for the three legs.

## Creating a pull request

`master` is protected: every change goes through a pull request, and the pull request is squash-merged once the `required checks` status check is green. `required checks` is the only check the ruleset requires: it is the gate job in `gradle.yml`, and a job that must block merging is added to the `needs:` of that job (no ruleset edit). The native image jobs (`native (GraalVM 25, ...)`) are in the same workflow but not part of the gate yet; the `native Groovy smoke (...)` jobs are in the gate.

- Create a branch, commit with `git commit -s` (DCO sign-off) and push it to your fork or to a branch of the repository.
- Use a [conventional commit](https://www.conventionalcommits.org/) style title for the pull request (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`). It becomes the squashed commit message.
- Run `./gradlew build` before you push, and link the issue the change closes (`Closes #123`).
- Send us a [pull request](https://help.github.com/articles/creating-a-pull-request).

## Checkstyle

We want to keep the code clean, following good practices about organization, Javadoc, and style as much as possible.

Micronaut GraphQL Tools uses [Checkstyle](https://checkstyle.sourceforge.io/) to make sure that the code follows those standards. The configuration is defined in `config/checkstyle/checkstyle.xml`. To execute Checkstyle, run:

```
./gradlew <module-name>:checkstyleMain
```

Before starting to contribute new code we recommended that you install the IntelliJ [CheckStyle-IDEA](https://plugins.jetbrains.com/plugin/1065-checkstyle-idea) plugin and configure it to use `config/checkstyle/checkstyle.xml`.

IntelliJ will mark in red the issues Checkstyle finds. Typical fixes are:

- Add one empty line before `package` in line 16
- Add the Javadoc for the constructor in line 27
- Add an space after `if` in line 34

The plugin also adds a new tab in the bottom of the IDE to run Checkstyle and show errors and warnings. We recommend that you run the report and fix all issues before submitting a pull request.

## Releases

Only milestone versions (`1.0.0-M<n>`, later `1.0.0`) are published, to the GitHub Packages Maven registry of this repository (D-40). There is no Maven Central, GitHub Release or snapshot publishing. A maintainer cuts a release by pushing a version tag on `master`; the `Release` workflow (`.github/workflows/release.yml`) then builds the tagged commit, checks that the build version equals the tag and publishes the library, the annotation processor and the BOM. Contributors never publish. See [MAINTAINING.md](MAINTAINING.md#releases) for the steps and [README.md](README.md#consuming-milestone-previews) for how to consume a release.

CI is one workflow, `.github/workflows/gradle.yml` (`Java CI`: build and tests, the native image tests, the Groovy native smoke test, the guide, the workflow lint (the `actionlint` job, which runs `jactionlint`), plus the `required checks` gate), with the shared tool setup in `.github/actions/setup-build`.
