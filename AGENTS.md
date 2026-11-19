# Agent instructions

Micronaut GraphQL Tools is a generic, schema-first GraphQL library for Micronaut 4, with Java, Kotlin and Groovy as
equal languages. `CONTRIBUTING.md` (setup, tests, docs, benchmarks) and `MAINTAINING.md` (repository rules) are the
human guides. `.github/workflows/gradle.yml` is the source of truth for the checks: if this file and the workflow ever
disagree, the workflow wins, and you should fix this file in the same change.

## Setup

- Tools: `mise install` installs Oracle GraalVM 25 (the default JDK and `native-image`) and `jactionlint`. If mise reports
  that the configuration is not trusted, run `mise trust` first.
  For another JDK line, run `MISE_ENV=jdk17 mise install`, `MISE_ENV=jdk21 mise install` or `MISE_ENV=jdk25 mise install` once (Temurin).
- Run Gradle through mise: `mise exec -- ./gradlew <task>` uses the default JDK. For another JDK line, set the same
  `MISE_ENV` on the command, for example `MISE_ENV=jdk21 mise exec -- ./gradlew <task>`. The JDK of each check is in the table.
- Always use the Gradle wrapper (`./gradlew`). Do not install Gradle.

## Checks

Run the checks that cover what you changed, and before you push run the full build on JDK 21 or 25.

| Check | Command | When |
| --- | --- | --- |
| Full build, default leg | `./gradlew build` | Before every push (JDK 21 or 25) |
| graphql-java floor leg | `./gradlew build -PgraphqlJavaLeg=floor` | Any change that touches graphql-java use (JDK 25) |
| graphql-java ceiling leg | `./gradlew build -PgraphqlJavaLeg=ceiling` | Same (JDK 25) |
| Binary compatibility | `./gradlew build -PgraphqlJavaLeg=ceiling -PgraphqlJavaCompileLeg=default` | Changes to main code (JDK 25) |
| Library on JDK 17 | `./gradlew :micronaut-graphql-tools:check :micronaut-graphql-tools-binding:check` | Changes to the library or binding module (JDK 17) |
| Example suites, JVM | `./gradlew :test-suite-java:test :test-suite-kotlin:test :test-suite-groovy:test` | Runtime and processor changes |
| Native image tests | `./gradlew :test-suite-java:nativeTest :test-suite-java:nativeSerdeTest :test-suite-kotlin:nativeTest --no-parallel` | Runtime features (Oracle GraalVM 25, about 5 GB each) |
| Build wiring of the compile-time checks | `./gradlew :test-suite-build:buildToolsTest` | Changes to the processor's options, registration or documented build snippets (JDK 25 and `mvn`, needs the network; CI job `build tools`) |
| Kotlin cases of the binding corpus | `./gradlew :micronaut-graphql-tools-processor:corpusKotlin` | Changes to the visitor or to a corpus case with a `kotlin/` directory (JDK 25, needs the network; CI job `build tools`; `-Pcorpus.kotlin.processor=kapt` is the nightly variant) |
| Guide | `./gradlew docs` (as the bullet below says) | Docs or `snippet::` changes; then check the log (below) |
| Benchmarks | `./gradlew :benchmarks:check` | Hot path, allocation or startup changes |
| Workflow lint | `mise exec -- jactionlint` | Any change under `.github/` |

- Leg values are roles (`floor`, `default`, `ceiling`, `latest`). The floor and ceiling versions are in
  `gradle/libs.versions.toml`; the default leg takes the graphql-java that micronaut-graphql manages; `latest` is resolved in `build.gradle.kts`.
  `-PgraphqlJavaVersion` is rejected. `latest` is for the canary only.
- Groovy has no native test (Spock cannot run natively): the Groovy suite runs on the JVM, and `./gradlew :test-suite-groovy:nativeSmokeTest` builds the Groovy example's native executable and asserts its GraphQL responses (CI job `native Groovy smoke`, gated).
- Add `-PgraphqlJavaLeg=ceiling` to the native command to run it on that leg.
- Run the guide as `set -o pipefail; ./gradlew docs 2>&1 | tee docs.log`. `./gradlew docs` exits 0 even when a snippet is
  missing. CI fails on the log, so run this after `docs`:
  `grep -E 'NO FILE FOUND|not found in include file|include file not found|unexpected end tag|detected unclosed tag|mismatched end tag|unterminated .* block|Unresolved directive' docs.log`
  It must print nothing (grep exits 1 when there is no match, so read the output, not the status).
- Benchmarks: `:benchmarks:check` runs a JMH smoke test and `allocationTest`, which fails when an operation allocates more
  than its baseline in `benchmarks/allocation-baselines.properties`. A change that allocates more on purpose updates the
  baselines for the three legs on JDK 21 and 25 (`./gradlew :benchmarks:allocationTest -Pallocation.update
  -PgraphqlJavaLeg=<leg>`) and commits the file. Wall-clock numbers come from `./gradlew :benchmarks:jmh`, or from the
  cold-start run that `CONTRIBUTING.md` describes (`:benchmarks:jmhJar`), with the JDK, leg and JMH output quoted. Never claim a speedup without one.

## Rules

- **Performance first** (the library's critical property). Judge every change by the request hot path, then startup and
  binding, then build time. Measure a performance claim; do not assume it.
- **Generic only.** No consumer, company or application names anywhere: code, tests, docs, comments, commit messages,
  branch names, and PR and issue text. On repository branches, CI scans the tree and the PR title and body for forbidden
  names and fails on a match. Fork pull requests get no secrets, so that guard does not run for them. Do not write such names, even to explain a change. Every feature has a generic motivation, a generic
  API, generic tests and docs, and no assumption about one application.
- **Java, Kotlin and Groovy are first class.** A runtime feature has tests in `test-suite-java`, `test-suite-kotlin` and
  `test-suite-groovy`. A processor-only change is tested in `graphql-tools-processor` and `test-suite-java`, the only suite
  that uses the processor. A guide example exists in all three suites with the same class name and tags, and is included
  with `snippet::`.
- **Native.** Each runtime feature has a native test in `test-suite-java` and `test-suite-kotlin`; the Groovy example has the native smoke test.
- **No Jackson in the public API.** Use Micronaut Serialization (`micronaut-serde`). Build input conversion on
  compile-time metadata (`BeanIntrospection`, Serde), not on an `ObjectMapper`. Input conversion is built from the bean
  introspection at startup (#37) and uses no Jackson; add none. The library declares no Jackson dependency (#36).
- **Dependencies.** Dependencies come from the Micronaut version catalog (`mn.*`) first; `gradle/libs.versions.toml` only holds what Micronaut does not manage; overrides need a recorded reason.
- **Micronaut 4 practice.** Follow the current Micronaut 4 line and its conventions. Do not design for Micronaut 5.
- **Publishing.** Nothing goes to Maven Central or GitHub Releases, and there are no snapshots; milestone versions are
  published to this repository's GitHub Packages (Maven) by a tag-triggered workflow only (never publish by hand).
  The workflow runs only in this repository.
- **Docs ship with the feature.** A new configuration key (`micronaut.graphql.tools.*`), public exception or public
  annotation is named in the guide or `README.md` in the same change. `DocsCoverageSpec` enforces this.
- **No references outside the repository.** Do not link to or cite planning notes, or files and repositories outside this
  one. Exceptions: the licence header in `config/HEADER`, and the links the guides already use (DCO, mise, Conventional
  Commits, Checkstyle).
- **Java 17 target.** Library modules compile with `--release 17` (set in `buildSrc`). `graphql-tools-processor` stays on
  Java 21 until #144 (see its `build.gradle.kts`). Do not use newer Java language or API features in library code.

## Pull requests

- Branch from `master`. Commit with `git commit -s` (DCO sign-off on every commit); commits without it cannot be accepted.
- Title: conventional commit (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`). It becomes the squash
  commit message.
- Body: `Closes #<issue>` for the issue the change finishes.
- Open the PR ready for review, not as a draft. Do not merge it yourself.
- Merge: squash only. Merge commits and rebase merges are disabled. Resolve every review conversation first.
- Required status check: `required checks` only. It is green when every job in its `needs:` list in `gradle.yml`
  succeeded. A new gated job is added to that list, never to the ruleset. The `native Groovy smoke` jobs and `build tools` are gated; the other native jobs are not gated yet.
- No admin bypass: a red, cancelled or pending check blocks the merge.

## Model routing

Use the cheapest model that can do the task well. Small or mechanical changes need only a small model. Core-logic
changes of medium size or larger, and the review that gates them, need a stronger one. Design questions that need a
decision are escalated to the strongest model, and its answer is followed. Decision issues are decided by the repository maintainers; an AI agent that needs a decision asks the strongest available model, records the outcome in the issue and follows it; a maintainer can overrule it at any time.

If an issue carries a `model:` label, it names the model the maintainers chose for that issue; follow it.

## Claude Code

`CLAUDE.md` imports this file (`@AGENTS.md`), so Claude Code reads these rules.
