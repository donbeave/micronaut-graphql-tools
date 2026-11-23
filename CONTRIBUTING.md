# Contributing Code or Documentation to Micronaut GraphQL Tools

This is not an official Micronaut module yet. It lives in `donbeave/micronaut-graphql-tools` and is intended for later contribution to the Micronaut Foundation.

Contributions are accepted under the [Developer Certificate of Origin (DCO)](https://developercertificate.org/). Sign off each commit with `git commit -s`, which adds a `Signed-off-by` line to the commit message. Commits without a `Signed-off-by` line cannot be accepted.

## Finding Issues to Work on

Open issues are listed at [donbeave/micronaut-graphql-tools/issues](https://github.com/donbeave/micronaut-graphql-tools/issues). Look for issues labelled [help wanted](https://github.com/donbeave/micronaut-graphql-tools/issues?q=is%3Aopen+is%3Aissue+label%3A%22help+wanted%22) or [good first issue](https://github.com/donbeave/micronaut-graphql-tools/issues?q=is%3Aopen+is%3Aissue+label%3A%22good+first+issue%22).

## JDK Setup

Micronaut GraphQL Tools builds on JDK 17, 21 or 25; native tests need Oracle GraalVM 25. The library compiles to Java 17 bytecode (`--release 17`), and on JDK 17 only `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check :micronaut-graphql-tools-processor:check :micronaut-graphql-tools-scalars:check :micronaut-graphql-tools-micrometer:check :micronaut-graphql-tools-opentelemetry:check :micronaut-graphql-tools-upload:check :micronaut-graphql-tools-security:check` works: the `test-suite-*` modules need JDK 21 or newer, so run the full `./gradlew build` on JDK 21 or 25.

The tools are pinned in `mise.toml` and installed with [mise](https://mise.jdx.dev): run `mise install` in the project directory. It installs Oracle GraalVM 25 (the build JDK, and `native-image` for the native tests) and `jactionlint` (a fork of actionlint that lints the workflows and composite actions: run `jactionlint` in the project directory; CI runs the same command in the `actionlint` job, with the default profile, which adds the security rules). It is pinned in `mise.toml`; the [jactionlint documentation](https://jactionlint.jdx.dev) lists the other install methods. To build on another JDK line, select an overlay with `MISE_ENV`, for example `export MISE_ENV=jdk21` and then `mise install` (or one command at a time: `MISE_ENV=jdk21 mise exec -- ./gradlew build`; `mise install` alone does not activate the tools, so keep `MISE_ENV` set, or use `mise exec`, when you run Gradle) (`jdk17`, `jdk21`, `jdk25` and `jdk27` install Temurin; JDK 27 is for the canary only). Gradle is not installed by mise: use `./gradlew`.

## IDE Setup

Micronaut GraphQL Tools can be imported into IntelliJ IDEA by opening the project directory (or `settings.gradle.kts`).

## Modules

The module map and the purpose of each Gradle project are in the [Modules](README.md#modules) section of the README. Each module folder has a README with its responsibility, its public and internal parts, and its build and test commands. Read the README of a module before you change it.

## Running Tests

The tests do not need Docker. These are the commands CI runs (all in `.github/workflows/gradle.yml`, workflow `Java CI`):

- `./gradlew build` builds everything and runs every check and test on the default graphql-java leg (JDK 21 or 25).
- `./gradlew build -PgraphqlJavaLeg=floor|ceiling` runs the same build on another graphql-java leg (`default` is the default; `latest` is for the canary). The versions of each role are in `gradle/libs.versions.toml`; the README explains the roles. `-PgraphqlJavaCompileLeg=default` compiles main code on the default leg while the tests run on the leg of `-PgraphqlJavaLeg` (the binary compatibility job).
- `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check :micronaut-graphql-tools-processor:check :micronaut-graphql-tools-scalars:check :micronaut-graphql-tools-micrometer:check :micronaut-graphql-tools-opentelemetry:check :micronaut-graphql-tools-upload:check :micronaut-graphql-tools-security:check` checks the library, its binding module, the processor and the scalars module only; this is the one that works on JDK 17.
- `./gradlew :test-suite-java:nativeTest :test-suite-java:nativeSerdeTest :test-suite-kotlin:nativeTest --no-parallel` runs the native image tests (the build runs tasks in parallel by default, and each native build needs about 5 GB) (Oracle GraalVM 25); add `-PgraphqlJavaLeg=ceiling` for another leg.
- `./gradlew :test-suite-build:buildToolsTest` builds the sample projects of `test-suite-build` with Gradle TestKit and Maven against the library published to `build/repo` (CI job `build tools`, JDK 25 and `mvn`, needs the network; the job also runs `corpusKotlin`, below). `build` and `check` do not run it.

`.github/workflows/gradle.yml` is the source of truth if this list and the workflow ever disagree.

Gradle build logic is written in the Kotlin DSL only: `*.gradle.kts` files, Kotlin precompiled script plugins in `buildSrc`, and `*.init.gradle.kts` files. Do not add or edit a Groovy DSL Gradle file (`*.gradle`), except to migrate it to the Kotlin DSL. The rule covers build scripts only; Groovy remains a test language (the Spock specs and the Groovy example suite).

The binding corpus (#142) is a directory of data cases under `graphql-tools-processor/src/test/resources/corpus/`; its README says how to add and update a case (`./gradlew :micronaut-graphql-tools-processor:corpusUpdate`, and read the diff). It runs as part of `test` on the default graphql-java leg, on JDK 17, 21 and 25 (about 70 s); the floor and ceiling legs skip it with a message, because its verdicts do not depend on the leg. The Kotlin cases are a separate task, `./gradlew :micronaut-graphql-tools-processor:corpusKotlin`, not part of `check` or `build`: it runs one Gradle TestKit build (the Kotlin and KSP2 versions of the root build) that compiles every Kotlin case with the visitor on and off, and downloads the Kotlin and KSP Gradle plugins from the Gradle plugin portal and the processor from Maven Central, so it needs the network (about 1 minute with a warm Gradle home). The CI job `build tools` runs it on every pull request; the canary workflow runs it under kapt every night (`-Pcorpus.kotlin.processor=kapt`).

### Architecture rules

`ArchitectureRulesSpec` (in `graphql-tools`, part of `check`, so it also runs on JDK 17) reads the bytecode of the library and of `graphql-tools-binding` with ArchUnit and fails the build, naming the class and the rule, when:

- a class reflects: a `Class` lookup of a user class, member or annotation (`forName`, `getMethod`, `getDeclaredMethod`, `getDeclaredAnnotation`, `getEnumConstants`, `newInstance` and the like), `setAccessible`, any `java.lang.reflect` type except `Modifier`, or anything of `java.lang.invoke`. Read user classes through the bean introspection or the compile-time metadata instead; reflection needs native-image configuration and is slower.
- a class depends on `com.fasterxml.jackson..` (#36). Use Micronaut Serialization.
- a public or protected member of a public class that is not `@Internal` names an `@Internal` type or a type of an `internal` package.

A class that must still reflect goes in `REFLECTION_ALLOWLIST` of the spec, with the exact accesses it may make, the reason and the issue that removes it; any other reflective access in that class still fails. The list may only shrink: the spec fails when an allowed access no longer happens, so delete the entry in the change that removes the reflection. `GraphQLResolversRegistry` is the one entry today (#34). To check a rule, add a violating class to `graphql-tools/src/main/java` and run `./gradlew :micronaut-graphql-tools:test --tests '*ArchitectureRulesSpec'`; remove it afterwards.

The example applications are three separate Gradle projects with the same schema, resolvers and smoke tests over HTTP:

| Suite | Language | Run the JVM tests | Run the native tests (Oracle GraalVM 25) |
| --- | --- | --- | --- |
| `test-suite-java` | Java (annotation processors) | `./gradlew :test-suite-java:test` | `./gradlew :test-suite-java:nativeTest` |
| `test-suite-kotlin` | Kotlin 2.1.21 with KSP2 (Java and Kotlin target 21) | `./gradlew :test-suite-kotlin:test` | `./gradlew :test-suite-kotlin:nativeTest` |
| `test-suite-groovy` | Groovy (Spock) | `./gradlew :test-suite-groovy:test` | no native test: `./gradlew :test-suite-groovy:nativeSmokeTest` (see below) |

Each native test takes about 5 to 7 minutes and 5 GB of memory: run them one after another.

Two build settings of the suites come from past failures. The suites build their native images with `-H:+SharedArenaSupport`: Netty 4.2 on Java 25 releases direct buffers through a shared arena, and without the flag the event-loop thread that releases a buffer fails and never terminates (`EventLoopShutdownTest` in `test-suite-java` and `test-suite-kotlin` checks it). `gradle.properties` sets `ksp.incremental=false`: an incremental KSP run (a source file added since the last run) dropped the `BeanIntrospectionReference` entries of introspections generated for classes of another module, so `ExternalModelTest` failed until a clean build, and the build cache could restore that output (`IntrospectionEntriesTest` in `test-suite-kotlin` checks it).

#### Native coverage

`nativeTest` runs every JUnit 5 test of the suite as a native binary, so the table below is the list of feature categories that must keep a test in the Java and Kotlin suites (the Groovy example has the native smoke test). A category that runs a TCK scenario names it: the scenario is written once and `example.tck.GraphQLToolsTckTest` runs it in both suites, so the native run covers what the JVM run covers. `checkNativeCoverage` (part of `check`) fails when a row names a test class that is missing from a suite it lists, or a scenario that `scenarios.txt` does not list. Add a row when you add a feature category, and keep the table in step with the native page of the guide.

<!-- native-coverage:begin -->
| Category | Test class | TCK scenario | Suites |
| --- | --- | --- | --- |
| Inputs | `example.tck.GraphQLToolsTckTest` | `arguments`, `input-errors` | Java, Kotlin |
| Enums | `example.enums.EnumValuesTest` | `enums` | Java, Kotlin |
| Interfaces and unions | `example.tck.GraphQLToolsTckTest` | `abstract-types` | Java, Kotlin |
| Scalar mapping | `example.scalars.ScalarsTest` | | Java, Kotlin |
| Subscription | `example.tck.WebSocketTest` | `websocket` | Java, Kotlin |
| Inherited resolver contracts | `example.contracts.InheritedContractTest` | | Java, Kotlin |
| Directives | `example.directives.DirectiveWiringTest` | | Java, Kotlin |
| Error handling | `example.tck.GraphQLToolsTckTest` | `errors`, `error-mapping`, `error-handling`, `error-details` | Java, Kotlin |
| Binding manifest and strict-mode `Object` method detection | `example.manifest.BindingManifestTest` | | Java, Kotlin |
| Schema discovery by index | `example.discovery.SchemaDiscoveryTest` | | Java, Kotlin |
| Execution thread (not an event-loop thread) | `example.tck.EventLoopMarkerTest` | `execution-thread`, `execution-thread-none` | Java, Kotlin |
| Context propagation | `example.tck.GraphQLToolsTckTest` | `per-execution-context`, `async-context` | Java, Kotlin |
| Async returns | `example.tck.GraphQLToolsTckTest` | `return-types` | Java, Kotlin |
| Kotlin `suspend` | `example.suspend.SuspendTest` | | Kotlin |
<!-- native-coverage:end -->

The three schemas (`src/main/resources/schema.graphqls`) are copies; `./gradlew :test-suite-java:checkSuiteSchemaParity` (part of `check`) fails when they differ.

Groovy has no native test, because Spock cannot run natively: its extensions are loaded reflectively and it ships no native metadata. The Groovy application is smoke-tested natively instead. `./gradlew :test-suite-groovy:nativeSmokeTest` (Oracle GraalVM 25, about 5 minutes and 5 GB) builds the native executable with `:test-suite-groovy:nativeCompile`, starts it on a free port, posts the queries of the Java and Kotlin smoke tests to `/graphql` (`src/nativeSmoke/groovy/NativeSmoke.groovy`), asserts the response bodies and stops the process; add `-PgraphqlJavaLeg=ceiling` for another leg. Groovy 4.0.x ships no GraalVM metadata, so the reachability metadata the Groovy runtime needs is in `test-suite-groovy/src/main/resources/META-INF/native-image`: the `invokedynamic` call sites are off, the example code is `@CompileStatic`, SAM arguments are Java lambdas (a closure passed as a SAM type is coerced at run time, which the image cannot do), and each closure class that a Groovy method (`findAll`, `collect`, ...) calls is listed in `reflect-config.json`; add one when you add such a closure, the smoke test fails without it. The Spock suite itself runs on the JVM only.

Responses keep their null fields (#45): `example.nullfields` sends raw HTTP requests and checks that `null` values keep their keys, under each JSON configuration. The default stack (Jackson databind, with and without `jackson.serialization-inclusion=NON_NULL`) runs in `test`, so `nativeTest` runs it too. Micronaut Serialization (`micronaut-serde-jackson`, with the default inclusion, `NON_NULL` and `ALWAYS` set through `micronaut.serde.serialization.inclusion`) runs in its own source set and task, `./gradlew :test-suite-java:serdeTest` (part of `check`), because `micronaut-serde-jackson` makes its `JsonMapper` win and the two stacks cannot share a test classpath. `./gradlew :test-suite-java:nativeSerdeTest` runs the same classes as a native binary (the `native` CI job runs it). The guard test `usesTheExpectedJsonStack` asserts the mapper class in both runs. The native-image Gradle plugin builds the image classpath of every test binary from `testRuntimeClasspath`, which has no `micronaut-serde`; `test-suite-java/build.gradle.kts` therefore adds the serde source set's runtime classpath to `nativeImageSerdeTestClasspath`. Without that the image has no Serde bean definitions and selects `JacksonDatabindMapper`.

### The execution-behaviour TCK

`graphql-tools-tck` holds the execution behaviour the library promises (argument binding, enums, null handling, return types, errors and `data: null`, error mapping through a `DataFetcherExceptionHandler`, abstract types, DataLoaders, bean scopes, introspection, variables, aliases, fragments) as scenarios that are written once and run in all three suites: `example.tck.GraphQLToolsTckTest` in `test-suite-java` and `test-suite-kotlin` (JUnit 5, also under `nativeTest`) and `example.tck.GraphQLToolsTckSpec` in `test-suite-groovy` (Spock, JVM only). `./gradlew :graphql-tools-tck:test` checks the scenario files themselves (listed, complete, well-formed JSON) without a suite; the three suites run the scenarios: `./gradlew :test-suite-java:test --tests 'example.tck.*'` (and the same for Kotlin and Groovy). The whole TCK (9 scenarios) takes about 1.5 to 7 seconds in each suite on a laptop, starting one application per scenario; the first scenario of a JVM carries about 4.7 seconds of warm-up, the others 0.1 to 0.4 seconds each.

A scenario is a directory under `graphql-tools-tck/src/main/resources/tck/`:

| File | Content |
| --- | --- |
| `schema.graphqls` | `extend type Query` (and `extend type Mutation`) with the fields of the scenario, plus its own types. Every suite serves its example schema and merges this file into it, so the type names must not clash with the example's (`Book`, `User`, `Error`, ...). |
| `config.properties` | Optional: Micronaut properties for the application of this scenario (for example `micronaut.graphql.tools.checks.unmapped-input-property=ignore`). |
| `cases.txt` | The cases, one name per line, run in this order against one application: state (a counter, a DataLoader's statistics) carries over from case to case. |
| `<case>.graphql` | The operation document, sent as a raw HTTP POST to `/graphql`. |
| `<case>.variables.json` | Optional: the variables. |
| `<case>.operation` | Optional: the `operationName`. |
| `<case>.expected.json` | The complete response body: `data`, `errors` with `message`, `locations`, `path` and `extensions`. Objects are compared by key, arrays by position, numbers by value. The string `"@any"` matches any string: use it for the message of an error whose wording is graphql-java's and changes between its versions, never for the data. |

To add a scenario:

1. Create the directory with the files above and list its id in `tck/scenarios.txt`. `./gradlew :graphql-tools-tck:test` fails on a missing, unlisted or malformed file.
2. In each suite add the fixtures (resolvers, types, loaders) in `src/test/{java,kotlin,groovy}/example/tck/<Scenario>Scenario.*`. Every fixture bean carries `@Requires(property = "tck.scenario", value = "<id>")`, so that only the scenario that runs has resolvers; classes of unions and interfaces are mapped by a `TckTypeMappings` bean with the same condition. Names stay unique across scenarios, because the fixtures of all scenarios are on one classpath.
3. Run the three suites. A scenario without fixtures in a suite fails there when the application starts. A suite that cannot have the fixtures lists the id and a reason in `src/test/resources/tck-exemptions.txt` (`<id> <reason>`); the list only shrinks: the meta-test `exemptionsAreCurrent` fails on an id that is no scenario, and on one whose scenario passes. No suite has an exemption today.

The shared fixtures register their DataLoaders as `BatchLoader` beans: the library creates the registry of each execution, and a `DataLoaderRegistry` bean fails the startup.

Out of scope here: HTTP transport details and WebSocket scenarios (#50), subscriptions (a `Publisher` return type is the event stream on subscription fields, and is adapted to a `CompletionStage` on other fields), and per-request context propagation.

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

`ErrorReferenceSpec` (same module) compares the two code tables of `src/main/docs/guide/exceptions.adoc` (the parts between `// begin generated` and `// end generated`) with the `BindingCode` enum: the code, its kind, the compile-time flag and `summary()`, in the order of the enum. After you add or change a code, write the tables with `UPDATE_ERROR_REFERENCE=true ./gradlew :micronaut-graphql-tools:test --tests '*ErrorReferenceSpec' --rerun`, and commit the page. Without the variable the spec fails on a difference and changes nothing. Write the text around the tables by hand.

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

## API map

This section tells you which package holds what, which packages you may import from, and which ones are implementation. Every Java package of the library modules has a `package-info.java` that says the same thing in the Javadoc, and the build checks that each of them exists.

### The rules

* A package has one responsibility. A package with more than 15 types is a smell: the build lists the ones that are over the limit, each with a reason, and a package that is no longer over the limit leaves the list.
* The public API is what you import from application code: the annotations, the customizer, mapping and directive interfaces and mapping contexts, `CustomWiring`, the context contributor interface and the request view it receives, the configuration class, `ArgumentValue`, `BindingManifest`, `BindingCode` and the one exception, `SchemaBindingException`. The table below lists where each one is. Everything else is implementation and can change in any release.
* Implementation is marked in two ways. A type is annotated `@Internal` (`io.micronaut.core.annotation.Internal`, the same marker Micronaut uses), and a package of implementation types carries the `internal` segment in its name and an `@Internal` on the package in its `package-info.java`. Micronaut itself follows this convention.
* A public type never lives in an `internal` package, and an internal type belongs in an `internal` package. The code follows both halves; see [Planned layout](#planned-layout) for what is still to be split.
* If a package holds both, the Javadoc of its `package-info.java` says so, and an `@Internal` type is not API even when it is `public`.

### Which package do I import from?

| I want to | Import from |
| --- | --- |
| Mark a resolver class, a method, a parameter, an input class, or list model classes without an annotation | `io.micronaut.graphql.tools.annotation` |
| Declare a resolver contract that fixes the root operation type or the source type | `io.micronaut.graphql.tools.resolver` (`QueryResolver`, `MutationResolver`, `SubscriptionResolver`, `TypeResolver<T>`) |
| Catch a startup failure | `io.micronaut.graphql.tools.exceptions` (`SchemaBindingException`; match on its code) |
| Read an error code | `io.micronaut.graphql.tools.binding` (`BindingCode`) |
| Change a setting in code | `io.micronaut.graphql.tools.config` (`GraphQLToolsConfiguration`) |
| Change the `GraphQL` builder, the schema, the runtime wiring or the type registry, or the final list of errors | `io.micronaut.graphql.tools.customizer` (the `*Customizer` interfaces) |
| Turn an exception of a resolver into an error with a message and extensions | `io.micronaut.graphql.tools.error` (`GraphQLExceptionMapper`) |
| Map a custom scalar, or a union member to a Java type | `io.micronaut.graphql.tools.mapping` (`GraphQLScalarMapping`, `SchemaMappingDictionary`) |
| Use the standard scalars (`java.time`, JSON, extended scalars) | `io.micronaut.graphql.tools.scalars` (`StandardScalar`; the module `graphql-tools-scalars`) |
| Name the request metrics and tags of the Micrometer module | `io.micronaut.graphql.tools.micrometer` (`GraphQLMetrics`; the module `graphql-tools-micrometer`) |
| Name the `Upload` scalar and the error code of an invalid multipart request | `io.micronaut.graphql.tools.upload` (`GraphQLUpload`; the module `graphql-tools-upload`) |
| Name the context key of the authentication, the error codes of a secured field, and see the root fields of an operation | `io.micronaut.graphql.tools.security` (`GraphQLSecurity`, `RootFieldsHook`; the module `graphql-tools-security`) |
| Tell an omitted argument from an explicit `null` | `io.micronaut.graphql.tools.input` (`ArgumentValue`) |
| Accept or reject a `graphql-transport-ws` connection, with attributes | `io.micronaut.graphql.tools.websocket` (`GraphQLConnectionInterceptor`, `GraphQLConnectionRequest`, `GraphQLConnectionResult`) |
| Wire a directive | `io.micronaut.graphql.tools.directive` (`SchemaDirective`) |
| Read what the schema is bound to | `io.micronaut.graphql.tools.manifest` (`BindingManifest`) |
| Add values to the GraphQL context of each operation | `io.micronaut.graphql.tools.execution` (`GraphQLContextContributor`, `GraphQLExecutionRequest`, `GraphQLTransport`, `DataLoaderBatchStatistics`, `DataLoaderOptionsCustomizer`) |
| Wrap the data fetchers of selected fields | `io.micronaut.graphql.tools.execution` (`FieldFetcherDecorator`, `FieldDescriptor`, `FieldExecutionKind`) |

Do not import any type annotated `@Internal`, and nothing from a package named `internal`.

### Current layout

This is the layout of the code now. `graphql-tools` is the library, `graphql-tools-binding` the shared binding rules, `graphql-tools-processor` the compile-time checks, `graphql-tools-scalars` the standard scalars, `graphql-tools-micrometer` the request metrics, `graphql-tools-opentelemetry` the request and field spans, `graphql-tools-upload` the file upload, `graphql-tools-security` the Micronaut Security integration and `graphql-tools-tck` the execution-behaviour TCK of this repository. In the audience column, `Mixed` means the package holds public API and `@Internal` types, and `Internal` means every type is implementation.

| Package | Module | Audience | Holds |
| --- | --- | --- | --- |
| `io.micronaut.graphql.tools` | graphql-tools | Public API | No types: the namespace of the packages below |
| `io.micronaut.graphql.tools.config` | graphql-tools | Public API | The configuration class, `GraphQLToolsConfiguration` |
| `io.micronaut.graphql.tools.customizer` | graphql-tools | Public API | The six `*Customizer` interfaces, `GraphQLErrorsCustomizer` among them |
| `io.micronaut.graphql.tools.error` | graphql-tools | Public API | `GraphQLExceptionMapper` and `GraphQLExceptionContext`: the mapping of the exceptions that resolvers throw to errors |
| `io.micronaut.graphql.tools.mapping` | graphql-tools | Public API | `SchemaMappingDictionary`, `GraphQLScalarMapping` and the mapping contexts |
| `io.micronaut.graphql.tools.directive` | graphql-tools | Public API | `SchemaDirective` and `CustomWiring` |
| `io.micronaut.graphql.tools.input` | graphql-tools | Public API | `ArgumentValue` |
| `io.micronaut.graphql.tools.manifest` | graphql-tools | Public API | `BindingManifest` |
| `io.micronaut.graphql.tools.execution` | graphql-tools | Public API | `GraphQLContextContributor`, the read-only `GraphQLExecutionRequest` that it receives, the `GraphQLTransport` kinds, the `DataLoaderBatchStatistics` of an execution, the `DataLoaderOptionsCustomizer` of one batch loader, and `FieldFetcherDecorator` with its `FieldDescriptor` and `FieldExecutionKind` |
| `io.micronaut.graphql.tools.annotation` | graphql-tools | Public API | The annotations of resolver classes, methods, parameters and input classes |
| `io.micronaut.graphql.tools.resolver` | graphql-tools | Public API | The marker interfaces of resolver contracts: `QueryResolver`, `MutationResolver`, `SubscriptionResolver` and `TypeResolver<T>` |
| `io.micronaut.graphql.tools.websocket` | graphql-tools | Public API | The connection hook of the `graphql-transport-ws` endpoint: `GraphQLConnectionInterceptor`, the request it receives and the result it returns |
| `io.micronaut.graphql.tools.exceptions` | graphql-tools | Public API | The startup failure: `SchemaBindingException`, the one exception to catch |
| `io.micronaut.graphql.tools.internal.exceptions` | graphql-tools | Internal | The base classes of the failures and the aggregate of one binding run; the classes of each kind are in the sub-packages |
| `io.micronaut.graphql.tools.internal.exceptions.mapping` | graphql-tools | Internal | The failures of the mapping dictionary and of class and scalar mappings |
| `io.micronaut.graphql.tools.internal.exceptions.resolver` | graphql-tools | Internal | The failures of binding a field to a resolver method, its arguments and its return type |
| `io.micronaut.graphql.tools.internal.exceptions.implementation` | graphql-tools | Internal | The failures of finding the implementation and the type resolver of an interface, union or abstract type |
| `io.micronaut.graphql.tools.internal.exceptions.input` | graphql-tools | Internal | The failures of input types, their properties and enum values |
| `io.micronaut.graphql.tools.internal.exceptions.wiring` | graphql-tools | Internal | The failures of directive wiring and runtime wiring |
| `io.micronaut.graphql.tools.internal.exceptions.checks` | graphql-tools | Internal | The failures that the startup checks report: strict mode, unreachable types, schema coordinates |
| `io.micronaut.graphql.tools.internal.exceptions.configuration` | graphql-tools | Internal | The failures of the setup: configuration, the schema definition and schema discovery |
| `io.micronaut.graphql.tools.internal` | graphql-tools | Internal | The entry points of the binding: the factories, the eager binding, the bean selection and the shared helpers |
| `io.micronaut.graphql.tools.internal.registry` | graphql-tools | Internal | The runtime method processors that register the resolvers (not the compile-time processor module) |
| `io.micronaut.graphql.tools.internal.discovery` | graphql-tools | Internal | Schema discovery: reads the schema files into the type registry |
| `io.micronaut.graphql.tools.internal.resolver` | graphql-tools | Internal | The data fetchers and the type resolvers, the planning of resolver arguments, and the bean condition behind the resolver annotations |
| `io.micronaut.graphql.tools.internal.wiring` | graphql-tools | Internal | The runtime wiring generator, the wiring factory that picks the data fetcher and the type resolver of each field and type, the interface field binder and the directive wirings |
| `io.micronaut.graphql.tools.binding` | graphql-tools-binding | Public API | `BindingCode`, the registry of the error codes |
| `io.micronaut.graphql.tools.internal.error` | graphql-tools | Internal | The default `DataFetcherExceptionHandler` that hides messages and runs the exception mappers, and the mapper of `ConstraintViolationException` |
| `io.micronaut.graphql.tools.internal.execution` | graphql-tools | Internal | The library's GraphQL invocation: the executor hop, the per-execution state, the lazy DataLoader registry, the wrapper of a subscription stream and the step-aside rules |
| `io.micronaut.graphql.tools.internal.execution.disconnect` | graphql-tools | Internal | The watcher that tells an execution its client disconnected, and its Netty implementation, active only when the Netty server classes are on the classpath |
| `io.micronaut.graphql.tools.internal.http` | graphql-tools | Internal | The HTTP endpoint that replaces the controller of `micronaut-graphql`, and the negotiation of the response media type and status that the multipart route shares |
| `io.micronaut.graphql.tools.internal.ws` | graphql-tools | Internal | The `graphql-transport-ws` handler and the state of its connections |
| `io.micronaut.graphql.tools.internal.observer` | graphql-tools | Internal | The observer SPI of the request hooks (`RequestObserver`), which the Micrometer and OpenTelemetry modules implement, and the one `Instrumentation` bean that calls the observers |
| `io.micronaut.graphql.tools.internal.protection` | graphql-tools | Internal | The cache of parsed documents of the protection settings |
| `io.micronaut.graphql.tools.internal.kotlin` | graphql-tools | Internal | Kotlin `suspend` resolvers and `Flow` subscriptions: the beans that start the coroutine and adapt the flow, active only when the Kotlin coroutine classes (and kotlinx-coroutines-reactive for a `Flow`) are on the classpath |
| `io.micronaut.graphql.tools.internal.input` | graphql-tools | Internal | The conversion of argument and input values, and the input type binder |
| `io.micronaut.graphql.tools.internal.checks` | graphql-tools | Internal | The startup checks and validators: strict mode, unreachable types, schema coordinates, the directive wiring check and the mapping dictionary |
| `io.micronaut.graphql.tools.internal.manifest` | graphql-tools | Internal | Recording and writing the binding manifest at startup |
| `io.micronaut.graphql.tools.internal.model` | graphql-tools | Internal | The runtime model the binding reads: the resolver registry, the introspection registry, the type and bean models |
| `io.micronaut.graphql.tools.binding.internal.finding` | graphql-tools-binding | Internal | Findings and their rendering |
| `io.micronaut.graphql.tools.binding.internal.manifest` | graphql-tools-binding | Internal | The binding manifest renderer and the schema hash |
| `io.micronaut.graphql.tools.binding.internal.membership` | graphql-tools-binding | Internal | The rule that a union or interface member is not mapped to a disjoint class |
| `io.micronaut.graphql.tools.binding.internal.meta` | graphql-tools-binding | Internal | The resolver contracts annotation |
| `io.micronaut.graphql.tools.binding.internal.model` | graphql-tools-binding | Internal | The model of classes and members that the rules read |
| `io.micronaut.graphql.tools.binding.internal.plan` | graphql-tools-binding | Internal | The binding plan and the bean model |
| `io.micronaut.graphql.tools.binding.internal.rules` | graphql-tools-binding | Internal | The per-class rules and their verdicts |
| `io.micronaut.graphql.tools.binding.internal.schema` | graphql-tools-binding | Internal | Reading the schema files and the schema model |
| `io.micronaut.graphql.tools.scalars` | graphql-tools-scalars | Public API | `StandardScalar`, the ids, default names and Java classes of the standard scalars |
| `io.micronaut.graphql.tools.scalars.internal` | graphql-tools-scalars | Internal | The factory that registers a mapping for each scalar, the reader of the configuration and the shared coercion |
| `io.micronaut.graphql.tools.scalars.internal.json` | graphql-tools-scalars | Internal | The JSON scalar |
| `io.micronaut.graphql.tools.scalars.internal.time` | graphql-tools-scalars | Internal | The `java.time` scalars and the builder that picks one from its format |
| `io.micronaut.graphql.tools.micrometer` | graphql-tools-micrometer | Public API | `GraphQLMetrics`, the names of the meters and tags |
| `io.micronaut.graphql.tools.micrometer.internal` | graphql-tools-micrometer | Internal | The Micrometer observer, the cap on operation names and the configuration of the metrics |
| `io.micronaut.graphql.tools.opentelemetry.internal` | graphql-tools-opentelemetry | Internal | The request span observer, the field span decorator and the wrapper that traces a field, the propagated context element and the configuration of the tracing |
| `io.micronaut.graphql.tools.upload` | graphql-tools-upload | Public API | `GraphQLUpload`, the name of the `Upload` scalar, the configuration key and the error code of an invalid multipart request |
| `io.micronaut.graphql.tools.upload.internal` | graphql-tools-upload | Internal | The multipart route, the reader of the `operations` and `map` parts and the `Upload` scalar |
| `io.micronaut.graphql.tools.security` | graphql-tools-security | Public API | `GraphQLSecurity`, the configuration prefix, the context key of the authentication and the error codes of a secured field, and `RootFieldsHook` |
| `io.micronaut.graphql.tools.security.internal` | graphql-tools-security | Internal | The context contributor, the decorator and fetcher of the secured fields and the instrumentation of the hook |
| `io.micronaut.graphql.tools.visitor` | graphql-tools-processor | Compiler plug-in | The compile-time `TypeElementVisitor`, loaded by the compiler and never imported |
| `io.micronaut.graphql.tools.tck` | graphql-tools-tck | Test helper | The TCK runner; not published |

Kotlin and Groovy sources need no `package-info`; the library modules are Java.

### Planned layout

The public API is split into its role packages (`annotation`, `config`, `customizer`, `directive`, `error`, `exceptions`, `execution`, `input`, `manifest`, `mapping`, `resolver` and `websocket`), and the root package `io.micronaut.graphql.tools` holds no types. The implementation side is split by responsibility too: `internal` holds the factories, the eager binding and the shared helpers, and `internal.exceptions` holds the base failure classes with one sub-package for each kind of failure. The packages of `graphql-tools-binding` already follow the convention and stay; whether `BindingCode` stays public is decided with the exception shrink. In `graphql-tools-processor`, only `visitor` remains. Each change that moves a public type gets an entry in [Breaking Changes](src/main/docs/guide/breaks.adoc) and in the [migration page](src/main/docs/guide/migration.adoc).

### How the build keeps this true

`PackageDocumentationSpec` fails when a Java package of the four modules has no `package-info.java`, when a `package-info.java` has no description, when a package has more than 15 types without a listed exception, when the package list of this section differs from the packages of the code, or when `@Internal` on a package and the audience column of this section disagree.

## Benchmarks

The `benchmarks` module measures the request hot path: a property fetch, a root method, a type resolver method
with its source, the conversion of scalar, enum, input object and list-of-inputs arguments, union and interface
resolution from four threads, a directive-wrapped fetcher, and a whole query over 1,000 and 10,000 objects with four
fields each. `BindingBenchmark` measures start-up: it starts a fresh application context and binds a generated schema of
100, 1,600, 3,000, 10,000 or 20,000 fields (the `fields` parameter: 10, 160, 300, 1,000 or 2,000 object types of 10 fields, generated by the
`generateLargeSchema` task), with the binding manifest recorded or not (`micronaut.graphql.tools.manifest.record`), one
invocation per context. The `shape` parameter is `rings` (the schema above) or `interfaces`, a generated interface-heavy
schema at 3,000 and 20,000 fields: object types of 10 fields in groups of ten, every type implementing a shared interface and the
interface of its group (which extends the shared one, so interfaces declare shared fields), a root field per group, and a
type-mapping dictionary with an entry for every object type and for the shared interface, a quarter of the object types mapped to a Java interface. It reaches the interface fixpoint,
the dictionary validation and the registry lookups that the rings barely reach. `RetainedHeapBenchmark` takes the same parameters and reports the heap a started context keeps
as the secondary result `retainedBytes`: the heap in use after a full collection with the context open, minus the heap
in use before the start. JMH's text summary adds the forks up for a secondary result, so the figure per fork is read from
the JSON. Both are measured cold, one fresh JVM per sample, only by `scripts/startup-report.py` (see below): the
`./gradlew :benchmarks:jmh` task excludes them, because its warm-up would start contexts in the same JVM first.

The benchmark jar generates the 1,600-field ring schema only, as before, so the other start-up benchmarks bind the same
schema as before. Each other size is generated into a jar of its own (`-PlargeFields=<fields>`, one size per build; add
`-PlargeShape=interfaces` for the interface-heavy schema, 3,000 fields by default; `-PlargeUnreturnedShared=true` leaves its `shared` root field out, so the interface `Shared` is one that no field returns; the dictionary maps `Shared`, which has no introspection, and the benchmarks fail at start if it does not), so
that the JVM of a size has no other size on its classpath. A jar packages one size and one shape, but JMH crosses every `@Param` value, so a direct run of the startup benchmarks passes `-p fields=<size> -p shape=<shape>` for the ones the jar was built with; `startup-report.py` does. `jmhSmokeTest` generates the 100 and 10,000-field schemas of both shapes in
its own source set and runs those two sizes only. Measured: the 100-field context retained 14.4 MB with all three sizes on the
classpath and 4.7 MB with the 100-field classes alone. The 10,000-field schema is 1,000 types in rings of 100 types, each reached from a root field of its own: graphql-java
builds the executable schema with one recursive call per type of a chain, so one ring of 1,000 types overflows the default
thread stack of a cold JVM, and the benchmark JVMs run on the default thread stack. Binding itself walks a chain with a work
stack (`DeepTypeChainSpec`). `ContextStartBenchmark`, `FirstRequestBenchmark` and
`StartAndFirstRequestBenchmark` separate the binding at startup from the first request: the same schema with
`micronaut.graphql.tools.eager-init` `true` or `false`, timing the context start, the first query after the start, and
both together (`./gradlew :benchmarks:jmhJar`, then run the jar with `-f 10 -wi 0 -i 1 -bm ss` for a cold
JVM per sample; the jar leaves `BindingBenchmark` and `RetainedHeapBenchmark` out unless one is named, because it
generates the 1,600-field schema only). None has an allocation gate: a whole context start is not a path the library owns. A gain is claimed
only with a figure from these benchmarks.

`InvocationBenchmark` measures the executor hop of the execution model on plain graphql-java (`direct`, `hopVirtual`,
`hopPlatform`, `perFieldHop`: no library code) and behind the library's own invocation (`library` and `libraryNone`): one
operation over a root object (`query=root`) or over 1,000 objects (`query=items1000`), each object with ten property
fields and one method field. `mode=direct` runs `executeAsync` on the calling thread (the executor `none`),
`hopVirtual` and `hopPlatform` submit the whole execution to a virtual-thread executor or to a fixed platform pool and wait
(one hop per request), and `perFieldHop` wraps every method fetcher in `supplyAsync` on the virtual-thread executor (one hop per
method field; property fields never hop). `library` runs the same `GraphQL` behind the library's `GraphQLInvocation` with the
default executor (`blocking`), `libraryNone` with `none`; both wait for the result, so they include the `Mono` that the transport
subscribes to. Read `library` against `hopVirtual` and `libraryNone` against `direct`. Read the figure as the difference to `direct` for the same query: the time per
operation includes the calling thread waiting for the executor, and `gc.alloc.rate.norm` counts the allocation of every
thread, because the profiler sees the whole JVM (the allocation gate sees the calling thread only, which is why nothing here is gated).
`AsyncBoundaryBenchmark` prototypes the async boundary: a field returns a `CompletionStage` that completes on the calling thread
(`completion=sameThread`), on a pool thread or on a thread that implements Reactor's `NonBlocking` marker, with the
prototype boundary off or on. With it on, a field that completes on a `NonBlocking` thread completes its future on the request
executor, so its children leave the event loop. Before each trial both benchmarks run the operation once with a
`ThreadChanges` counter and fail when the counted hops differ from the expected ones (none for `direct`, one for the `hop`
modes, one per method field for `perFieldHop`; the `library` modes assert where the fetchers ran), so the number of hops is asserted, not assumed. The counter is not
attached to the measured operations. `jmhSmokeTest` runs every case.

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
  `invocationOverhead` and `invocationSlope` gate the library's invocation with the executor `none` (the gates measure the
  calling thread, and a hop moves the work to another one): the first is the bytes that `invoke` allocates beyond a direct
  `executeAsync` of the same 10-field query (a budget of 1,024 B, and its recorded baseline), the second is that overhead at 1,000
  fields minus the one at 10 fields, which must stay within 0.5 B of its baseline of 0. `loaderOverhead` is the bytes that a
  request allocates in an application with 50 batch loader beans that it does not use, minus those with one (`benchmarks.loaders`
  selects the beans of `Loaders`): a registered but unused loader costs nothing, so the gate allows 0.5 B for each of the 49 and
  its baseline is 0. `injectedParameters` and `injectedStateParameters` gate a field with four and with three injected parameters (context, locale, context value, environment; request, optional response, principal): each allocates the argument array and nothing else (32 B).

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

To compare two builds on a machine that is not idle, build one jar per checkout and case, and run
`python3 benchmarks/scripts/startup-ab.py --fields <fields> --shape <shape> --forks 25 base=<jar> branch=<jar>`. It starts the
builds in turn, one cold JVM each, in a rotating order, and prints the medians, the quartiles, the range, the retained heap
and a 95 percent bootstrap interval of the median difference. A difference whose interval holds zero is inside the noise and
is not reported as a gain. The measured results of the binding changes are in `benchmarks/README.md`.

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

`master` is protected: every change goes through a pull request, and the pull request is squash-merged once the `required checks` status check is green. `required checks` is the only check the ruleset requires: it is the gate job in `gradle.yml`, and a job that must block merging is added to the `needs:` of that job (no ruleset edit). Every job of the workflow is in the gate, the native image jobs (`native (GraalVM 25, ...)`) included, and the branch must be up to date with `master` before it merges (see "One gate" in `MAINTAINING.md`).

- Create a branch, commit with `git commit -s` (DCO sign-off) and push it to your fork or to a branch of the repository.
- Use a [conventional commit](https://www.conventionalcommits.org/) style title for the pull request (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`). It becomes the squashed commit message.
- Run `./gradlew build` before you push, and link the issue the change closes (`Closes #123`).
- Comments and docs give their reason in place. Do not cite decision IDs, design rule IDs or planning files, and do not link to notes kept outside this repository; cite an issue or pull request number instead. `NoPlanningReferencesSpec` fails `check` on the first three; reviewers check the links.
- Fill in the checklist of the [pull request template](.github/PULL_REQUEST_TEMPLATE.md).
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

Only milestone versions (`1.0.0-M<n>`, later `1.0.0`) are published, to the GitHub Packages Maven registry of this repository. There is no Maven Central, GitHub Release or snapshot publishing. A maintainer cuts a release by pushing a version tag on `master`; the `Release` workflow (`.github/workflows/release.yml`) then builds the tagged commit, checks that the build version equals the tag and publishes the library, the annotation processor and the BOM. Contributors never publish. See [MAINTAINING.md](MAINTAINING.md#releases) for the steps and [README.md](README.md#consuming-milestone-previews) for how to consume a release.

CI is one workflow, `.github/workflows/gradle.yml` (`Java CI`: build and tests, the native image tests, the Groovy native smoke test, the guide, the workflow lint (the `actionlint` job, which runs `jactionlint`), plus the `required checks` gate), with the shared tool setup in `.github/actions/setup-build`.
