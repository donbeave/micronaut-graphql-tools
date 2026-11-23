# Maintaining Micronaut GraphQL Tools

This is not an official Micronaut module yet. This file describes how this repository is run today.

## Repository rules

- `master` is protected by a ruleset. Direct pushes, force pushes and deletion are not allowed, and history is linear.
- Every change is a pull request. Only **squash merge** is enabled (merge commits and rebase merges are off).
- The only required status check is `required checks`, the gate job in `.github/workflows/gradle.yml`. It is green only when every job it lists under `needs:` succeeded. A new gated job goes into that `needs:` list, never into the ruleset: renaming a job or adding a graphql-java leg needs no ruleset edit.
- There is no admin bypass. A red, cancelled, pending or missing check blocks the merge. Review conversations must be resolved.
- The native image jobs (`native (GraalVM 25, graphql-java default|ceiling)`) and `native Groovy smoke` are in the gate like every other job of `gradle.yml`.
- Gradle build logic is Kotlin DSL only: `*.gradle.kts` files, Kotlin precompiled script plugins in `buildSrc`, and `*.init.gradle.kts` files. A pull request does not add or edit a Groovy DSL Gradle file (`*.gradle`), except one that migrates it to the Kotlin DSL. The rule covers build scripts only; the Groovy tests (the Spock specs and the Groovy example suite) are not affected.

## One gate: the pull request check is the master check

A merge must never turn `master` red. Three rules make that structural; the workflow is the source of truth, and `.github/scripts/check-gate.py` (run by the `actionlint` job) fails the pull request that breaks one of them.

1. **Every job of `gradle.yml` is in the `needs:` of `required checks` and runs on every event.** A job with an `if:` or a push-only job is rejected. `gradle.yml` is the only workflow that runs on a push to a branch, and its `push`, `pull_request` and `merge_group` events cover the same branches. Anything that is not a verdict on a commit (the canary, the start-up benchmark, the release) runs from a `schedule`, `workflow_dispatch` or a tag and never from a push to `master`, so a red run there is never mistaken for a broken `master`.
2. **The branch must be up to date with `master` before it merges.** The ruleset `protect-main` has `strict_required_status_checks_policy: true` ("Require branches to be up to date before merging"), so `required checks` is always a run on top of the current `master`. Without it, two pull requests that are each green on a stale base can merge into a red `master` (a semantic merge conflict: #399 added a benchmark that imported a class that #401 had moved, and `master` went red at `dd0258ff`). The cost is one "Update branch" click and one re-run when another pull request merged first. `gradle.yml` also has a `merge_group` trigger, so a merge queue can be switched on without a workflow change (GitHub offers the queue for organization repositories only, so it is not enabled here).
3. **The canary reports, it does not gate.** It runs nightly and on demand against versions Micronaut 4 does not support yet. A failure there is triaged into an issue; it blocks nothing.

One gap remains by design: a fork pull request gets no secrets, so its `consumer-name guard` does not scan (it exits green) while the `push` run on `master` does. Before squash-merging a fork pull request, run the same scan against its tree with the forbidden names yourself; a green `required checks` is not enough for it.

The ruleset is managed in the GitHub settings. The one change made for rule 2, through `gh api -X PUT repos/donbeave/micronaut-graphql-tools/rulesets/27449469`, set `rules[type=required_status_checks].parameters.strict_required_status_checks_policy` from `false` to `true`; nothing else in the ruleset changed. To revert it, send the same call with `false`. If any other tool manages this ruleset, set the same field there too, or its next apply undoes the change.

## Module layout and the binding rules

The modules, and what each may depend on:

- `graphql-tools-binding` (`io.micronaut.graphql.tools.binding`) holds the rule bodies: the schema rules (type extensions, the scalar table, wrapper and coordinate rules), the per-class rules (`ResolverClassRules`: method-to-field matching, the parameters of a resolver method against the arguments of the field, the return type wrappers against the field type, nullable primitives, and `check`, which runs them for one class and returns a `ClassVerdict`; `InputTypeRules`, `EnumTypeRules`), the finding model and its one renderer (`FindingRenderer`), the `BindingPlan` records with their `ManifestRenderer`, and the type-model SPI that the rules read (`TypeModel`, `JClass`, `JMethod`, `JProperty`, `TypeRef`; `BeanModel` for the whole-program planner). It depends on graphql-java and micronaut-core only. It never imports `io.micronaut.inject` and uses no reflection: `BindingArchitectureSpec` fails on either. Every type is `@Internal`; the code registry (#237) will be its one public type. It compiles with `--release 17`, and `:micronaut-graphql-tools-binding:check` runs in the JDK 17 job.
- `graphql-tools` (the runtime) depends on it with `api`. It holds the runtime adapter of the type model (`RuntimeTypeModel` over `BeanDefinition`, `ExecutableMethod`, `Argument` and `BeanIntrospection`; `RuntimeBeanModel`), the bean discovery, the configuration and the wiring. A rule body lives in `binding` or in no module twice; the runtime reads the type through the adapter and calls the rule.
- `graphql-tools-processor` holds the compile-time visitor (`io.micronaut.graphql.tools.visitor`). It compiles with `--release 17`, and `:micronaut-graphql-tools-processor:check` runs in the JDK 17 job. Its main classpaths have no dependency on `graphql-tools` (`checkNoRuntimeDependency`, part of `check`). The element-based adapter of the type model and the visitor are #150.

- `graphql-tools-scalars` (`io.micronaut.graphql.tools.scalars`) holds the standard scalars: the `java.time` scalars, the JSON scalar and a selection of the extended scalars, each registered as a `GraphQLScalarMapping` bean by a factory that reads `micronaut.graphql.tools.scalars.*`. It depends on `graphql-tools` (for `GraphQLScalarMapping`), graphql-java-extended-scalars and the JSON API of Micronaut (`micronaut-json-core`), never on Jackson or on a Serde implementation. Its one public type is the `StandardScalar` enum; everything else is `@Internal`. It compiles with `--release 17`, and `:micronaut-graphql-tools-scalars:check` runs in the JDK 17 job. It is published like the other library modules (the list in `.github/gradle/publish-github-packages.init.gradle`).

- `graphql-tools-micrometer` (`io.micronaut.graphql.tools.micrometer`) holds the request metrics.
- `graphql-tools-opentelemetry` (`io.micronaut.graphql.tools.opentelemetry`) holds the request and field spans; with `graphql-tools-micrometer` (the request metrics) it is a satellite of the library. They are named after their technology, as the modules of Micronaut Micrometer and Micronaut Tracing are, and the modules do not depend on each other. Micrometer and OpenTelemetry are `compileOnly` there: the beans that use one are conditional on its classes and on a `MeterRegistry` or `OpenTelemetry` bean, and no class outside the `Micrometer`/`GraphQLMetrics` named classes may name Micrometer types, nor outside the `Tracing` and `Span` named classes OpenTelemetry types (`MicrometerArchitectureSpec`, `OpenTelemetryArchitectureSpec`). The library has exactly one `Instrumentation` bean for them, `ObserverInstrumentation` in `graphql-tools` (package `internal.observer`), which calls the `@Internal` `RequestObserver` beans that each module contributes: a second `Instrumentation` in the chain costs every field about 550 bytes on graphql-java 21, and the allocation gate (`observedInvocationSlope`) fails on it. It implements request hooks only: a field-level hook would cost every field of every request. The field-wrapping classes (the `FieldFetcherDecorator` that the library asks once for each field at startup, and the fetcher that traces one) live only in the OpenTelemetry module; it returns the offered fetcher for every unselected field and is never offered a trivial one. The only public type of the two is `GraphQLMetrics` (names); everything else is `@Internal`. Both compile with `--release 17`, run in the JDK 17 job and are published like the other library modules; a new satellite follows the same pattern and adds an observer, never an `Instrumentation`.

- `graphql-tools-security` (`io.micronaut.graphql.tools.security`) holds the Micronaut Security integration. Micronaut Security is compile-only: its beans are `@Requires(classes = ...)`, and the core library never depends on it. The principal reaches resolvers through a `GraphQLContextContributor` that copies the request's `Authentication` into the context once per execution; `@Secured` is a `FieldFetcherDecorator` that decides at startup and returns the offered fetcher unchanged for an unsecured field (`SecuritySpec` keeps this); the `RootFieldsHook` runs in one `Instrumentation` that exists only when a hook bean does. It compiles with `--release 17`, runs in the JDK 17 job and is published like the other library modules.
- `graphql-tools-upload` (`io.micronaut.graphql.tools.upload`) holds the `Upload` scalar and the route of the GraphQL multipart request specification. The route is a second `@Post` on the path of the transport with `consumes = multipart/form-data`, which wins over the transport's `*/*` route for that content type (`MultipartUploadSpec` and the `precedence` test of the suites keep this). It carries no `@ExecuteOn`: it calls the `GraphQLInvocation` bean, which does the executor hop (`UploadArchitectureSpec`). It reads JSON with the `JsonMapper` API only, never Jackson. Its one public type is `GraphQLUpload`; everything else is `@Internal`. It compiles with `--release 17`, runs in the JDK 17 job and is published like the other library modules.

Adding a module touches several lists of modules: `settings.gradle.kts` (the source), the publish init script, the summary of `release.yml`, the JDK 17 `check` command and the library-module comment in `gradle.yml`, `AGENTS.md`, `CONTRIBUTING.md`, the BOM README, the module and artifact tables of `README.md`, the list above and `PackageDocumentationSpec`. `.github/scripts/check-modules.py` (run by the `actionlint` job, so part of `required checks`) fails when one of them disagrees with `settings.gradle.kts`. A module that is not published is a `test-suite-*` module (the test suites, the benchmarks and the TCK): the prefix is all the script and the BOM need.

The startup binding (`GraphQLRuntimeWiringGenerator`) is split by phase. Each phase class owns the mutable state it needs and hands the next phase an immutable result:

| Class | Phase |
|---|---|
| `SchemaCoordinateValidator` | explicit `@GraphQLSchemaCoordinate` declarations, before and after the binding |
| `DictionaryValidator` | the `SchemaMappingDictionary` against itself |
| `ArgumentPlanner`, `InputTypeBinder`, `ScalarMappings` | how parameters bind arguments (the rule is `ResolverClassRules.arguments`), and how input types, enums and scalars bind Java types |
| `GraphQLResolversRegistry`, `ReturnTypes` | which method binds a field (`ResolverClassRules.bindsField`) and how the return type is peeled (`ResolverClassRules.returnAdaptation`); both only follow the verdict |
| `BindingManifestRecorder` | collects the `BindingPlan` of the binding; `ManifestRenderer` renders it when the manifest is first read |
| `InterfaceFieldBinder` | fields that an object type shares with its interfaces; returns `InterfaceBindings` |
| `AbstractTypeWiring` | union and interface type resolution and its ambiguity check |
| `UnreachableTypeChecks`, `StrictModeChecks` | reachability and the strict-mode findings |

## Triage incoming issues

Issues and pull requests are tracked on the maintainers' "Roadmap to 1.0" project board. Labels in use:

- Kind: `type: bug`, `type: improvement`, `type: enhancement`, `type: docs`, `type: dependency-upgrade`, `type: breaking`, `type: task`, `type: decision`, `type: epic`.
- Priority: `priority: high`, `priority: medium`, `priority: low`.
- Size: `size: S`, `size: M`, `size: L`.
- Area: `relates-to: <area>` (for example `relates-to: build`, `relates-to: docs`, `relates-to: performance`).
- Workflow: `owner: maintainer`, `status: needs-decision`, `source: review`.
- Community: `help wanted`, `good first issue`, `question`, `duplicate`, `invalid`, `wontfix`.

The `type: *` labels also feed the generated release notes (`.github/release.yml`). New labels are created in the GitHub UI.

## Review pull requests

A pull request is ready to merge when:

- `required checks` is green on the current head commit.
- Every commit is signed off under the [Developer Certificate of Origin](https://developercertificate.org/) (`git commit -s`, a `Signed-off-by` line).
- The title follows the conventional commit style (`feat:`, `fix:`, `docs:`, `build:`, `ci:`, `perf:`, `test:`, `chore:`); it becomes the squash commit message.
- The code is of a minimum quality, uses the Micronaut APIs correctly, contains tests and updates the documentation.
- Linked issues are closed with closing keywords (`Closes #123`).
- No consumer, company or application names appear in code, docs, commit messages or pull request text.

Merge with "Squash and merge" only.

## Dependency upgrades

Renovate is configured in `.github/renovate.json`. Review its pull requests like any other: the same green `required checks` rule applies.

## Dependency downloads

Maven Central and the Gradle plugin portal sometimes answer HTTP 429. Gradle retries that a few times within a fraction of a second, so `gradle.properties` raises the retry count and backoff, and the Gradle steps of `gradle.yml` and the read-only steps of `canary.yml` and the `verify` job of `release.yml` run through `.github/scripts/retry-on-download-failure.sh`. It runs the build again (up to three attempts, none after 15 minutes) only when the output shows a download failure (or the `checkPom` message "declares a non-resolvable dependency", which a runner repository access failure also produces; a failed `checkPom` task does not count as a failed check), keeps the exit code and output of the last attempt, and never retries a run that also failed a test or check, or failed on anything but a download (`test-retry-on-download-failure.sh` tests this in the `actionlint` job). The publish step of `release.yml` is not wrapped: a publish is never retried automatically. The Maven samples retry 429 themselves, with more attempts than Maven's default.

## Releases

Only GitHub Packages milestone releases are published: milestone versions `1.0.0-M<n>` and, later, `1.0.0` go to the Maven registry of this repository, `https://maven.pkg.github.com/donbeave/micronaut-graphql-tools`. Maven Central, GitHub Releases and snapshots stay out of scope until the Foundation contribution. The groupId stays `io.micronaut.graphql.tools`; every consumer-facing page says that the artifacts are unaffiliated milestone previews until contributed.

To cut a release:

1. Check that `master` is green (`required checks`) at the commit to release, and that the `releaseHistory.adoc` section of the guide describes the release.
2. Create and push an annotated tag on that commit: `git tag -a 1.0.0-M2 -m "1.0.0-M2" <sha>` and `git push origin 1.0.0-M2`. The tag must match `^[0-9]+\.[0-9]+\.[0-9]+(-M[0-9]+)?$`.
3. The `Release` workflow (`.github/workflows/release.yml`) starts on the tag push. `verify` builds the tagged commit exactly like the default leg of `gradle.yml` and checks that the build version equals the tag; only then does `publish` upload the library, the annotation processor and the BOM. It publishes from the tag's own sources, so no version bump commit is needed: the tag name is the version (`-PprojectVersion`).
4. To publish a tag that already exists and predates the workflow, run `gh workflow run release.yml --ref master -f tag=<tag>`. The workflow definition and the publishing setup (`.github/gradle/publish-github-packages.init.gradle`) come from `master`, and the sources come from the tag.
5. After the `Release` workflow for a tag (milestone or final release) has succeeded, bump the dependency versions in `README.md` and the Quick Start page (`src/main/docs/guide/quickStart.adoc`). Do not bump them while the workflow is still running or after it failed, because the artifacts are not yet published.

A published version is not replaced. If a release is wrong, fix it on `master` and release the next milestone; deleting a version needs repository admin rights (GitHub refuses it for a public version with more than 5,000 downloads). The workflow uses the repository's `GITHUB_TOKEN` with `packages: write` and runs only in `donbeave/micronaut-graphql-tools`. See "Consuming milestone previews" in [README.md](README.md) for how users read the packages. To build a version locally, see "Artifacts and versions" there.
