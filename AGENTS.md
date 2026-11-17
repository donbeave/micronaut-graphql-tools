# Agent instructions

Micronaut GraphQL Tools is a generic, schema-first GraphQL library for Micronaut 4, with Java, Kotlin and Groovy as
equal languages. `CONTRIBUTING.md` (setup, tests, docs, benchmarks) and `MAINTAINING.md` (repository rules) are the
human guides. `.github/workflows/gradle.yml` is the source of truth for the checks: if this file and the workflow ever
disagree, the workflow wins, and you should fix this file in the same change.

## Setup

- Tools: `mise install` installs Oracle GraalVM 25 (the default JDK and `native-image`) and `actionlint`.
  For another JDK line, `MISE_ENV=jdk17|jdk21|jdk25 mise install` (Temurin).
- Run Gradle through mise so the JDK in `mise.toml` is used: `mise exec -- ./gradlew <task>`.
- Always use the Gradle wrapper (`./gradlew`). Do not install Gradle.

## Checks

Run the checks that cover what you changed, and before you push run the full build on JDK 21 or 25.

| Check | Command | When |
| --- | --- | --- |
| Full build, default leg | `./gradlew build` | Before every push (JDK 21 or 25) |
| graphql-java floor leg | `./gradlew build -PgraphqlJavaLeg=floor` | Any change that touches graphql-java use (JDK 25) |
| graphql-java ceiling leg | `./gradlew build -PgraphqlJavaLeg=ceiling` | Same (JDK 25) |
| Binary compatibility | `./gradlew build -PgraphqlJavaLeg=ceiling -PgraphqlJavaCompileLeg=default` | Changes to main code (JDK 25) |
| Library on JDK 17 | `./gradlew :micronaut-graphql-tools:check` | Changes to the library module (JDK 17) |
| Example suites, JVM | `./gradlew :test-suite-java:test :test-suite-kotlin:test :test-suite-groovy:test` | Runtime and processor changes |
| Native image tests | `./gradlew :test-suite-java:nativeTest :test-suite-kotlin:nativeTest --no-parallel` | Runtime features (Oracle GraalVM 25, about 5 GB each) |
| Guide | `./gradlew docs 2>&1 \| tee docs.log` | Docs or `snippet::` changes; then check the log (below) |
| Benchmarks | `./gradlew :benchmarks:check` | Hot path, allocation or startup changes |
| Workflow lint | `actionlint` | Any change under `.github/` |

- Leg values are roles (`floor`, `default`, `ceiling`, `latest`); the versions live only in `gradle/libs.versions.toml`.
  `-PgraphqlJavaVersion` is rejected. `latest` is for the canary only.
- Groovy has no native test: the Groovy suite runs on the JVM only (`:test-suite-groovy:test`).
- Add `-PgraphqlJavaLeg=ceiling` to the native command to run it on that leg.
- `./gradlew docs` exits 0 even when a snippet is missing. CI fails on the log, so run this after `docs`:
  `grep -E 'NO FILE FOUND|not found in include file|include file not found|unexpected end tag|detected unclosed tag|mismatched end tag|unterminated .* block|Unresolved directive' docs.log`
  It must print nothing.
- Benchmarks: `:benchmarks:check` runs a JMH smoke test and `allocationTest`, which fails when an operation allocates more
  than its baseline in `benchmarks/allocation-baselines.properties`. A change that allocates more on purpose updates the
  baselines for the three legs on JDK 21 and 25 (`./gradlew :benchmarks:allocationTest -Pallocation.update
  -PgraphqlJavaLeg=<leg>`) and commits the file. Wall-clock numbers come only from `./gradlew :benchmarks:jmh`, with the
  JDK, leg and JMH output quoted. Never claim a speedup without one.

## Rules

- **Performance first** (the library's critical property). Judge every change by the request hot path, then startup and
  binding, then build time. Measure a performance claim; do not assume it.
- **Generic only.** No consumer, company or application names anywhere: code, tests, docs, comments, commit messages,
  branch names, and PR and issue text. CI scans the tree and the PR title and body for forbidden names, and fails the
  build on a match. Do not write such names, even to explain a change. Every feature has a generic motivation, a generic
  API, generic tests and docs, and no assumption about one application.
- **Java, Kotlin and Groovy are first class.** A runtime or processor feature has tests in `test-suite-java`,
  `test-suite-kotlin` and `test-suite-groovy`. A guide example exists in all three suites with the same class name and
  tags, and is included with `snippet::`.
- **Native.** Each runtime feature has a native test in `test-suite-java` and `test-suite-kotlin`.
- **No Jackson in the public API.** Use Micronaut Serialization (`micronaut-serde`). Build input conversion on
  compile-time metadata (`BeanIntrospection`, Serde), not on an `ObjectMapper`.
- **Micronaut 4 practice.** Follow the current Micronaut 4 line and its conventions. Do not design for Micronaut 5.
- **No publishing.** Do not add publish tasks, snapshot or release workflows, or Maven Central steps. Releases are cut
  by maintainers by hand.
- **Docs ship with the feature.** A new configuration key (`micronaut.graphql.tools.*`), public exception or public
  annotation is named in the guide or `README.md` in the same change. `DocsCoverageSpec` enforces this.
- **No references outside the repository.** Do not link to or cite files, notes or repositories outside this one.
- **Java 17 target.** Library modules compile with `--release` set in `buildSrc`. Do not use newer Java language or API
  features in library code.

## Pull requests

- Branch from `master`. Commit with `git commit -s` (DCO sign-off on every commit); commits without it cannot be accepted.
- Title: conventional commit (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`). It becomes the squash
  commit message.
- Body: `Closes #<issue>` for the issue the change finishes.
- Open the PR ready for review, not as a draft. Do not merge it yourself.
- Merge: squash only. Merge commits and rebase merges are disabled.
- Required status check: `required checks` only. It is green when every job in its `needs:` list in `gradle.yml`
  succeeded. A new gated job is added to that list, never to the ruleset. The native jobs are not gated yet.
- No admin bypass: a red, cancelled or pending check blocks the merge.

## Model routing

Read the `model:` label of the issue you work on, and use that model. Labels in the repository:

| Label | Model | Use for |
| --- | --- | --- |
| `model: haiku` | Haiku 5.5 (`claude-haiku-5-5`) | Mechanical, fully specified work |
| `model: sonnet` | Sonnet 5.5 (`claude-sonnet-5-5`) | Default: implementation, tests, reviews, docs |
| `model: opus` | Opus 5.5 (`claude-opus-5-5`) | Rare: architecture and cross-cutting design only |

- Haiku to Sonnet: if a `model: haiku` task turns out not to be mechanical, comment on the issue, then continue with Sonnet.
- Sonnet to Opus: only with the maintainer's approval.
- Never use Fable.

## Claude Code

`CLAUDE.md` imports this file (`@AGENTS.md`), so Claude Code reads these rules.
