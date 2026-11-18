# Maintaining Micronaut GraphQL Tools

This is not an official Micronaut module yet. This file describes how this repository is run today.

## Repository rules

- `master` is protected by a ruleset. Direct pushes, force pushes and deletion are not allowed, and history is linear.
- Every change is a pull request. Only **squash merge** is enabled (merge commits and rebase merges are off).
- The only required status check is `required checks`, the gate job in `.github/workflows/gradle.yml`. It is green only when every job it lists under `needs:` succeeded. A new gated job goes into that `needs:` list, never into the ruleset: renaming a job or adding a graphql-java leg needs no ruleset edit.
- There is no admin bypass. A red, cancelled, pending or missing check blocks the merge. Review conversations must be resolved.
- The native image jobs (`native (GraalVM 25, graphql-java default|ceiling)`) are in `.github/workflows/gradle.yml` and run on every pull request, but they are not part of the gate yet (#152).

## Module layout and the binding rules

The modules, and what each may depend on:

- `graphql-tools-binding` (`io.micronaut.graphql.tools.binding`) holds the rule bodies: the schema rules (type extensions, the scalar table, wrapper and coordinate rules), the per-class rules (`ResolverClassRules` for the parameters of a resolver method, `InputTypeRules`, `EnumTypeRules`), the finding model and its one renderer (`FindingRenderer`), and the type-model SPI that the rules read (`TypeModel`, `JClass`, `JMethod`, `JProperty`, `TypeRef`; `BeanModel` for the whole-program planner). It depends on graphql-java and micronaut-core only. It never imports `io.micronaut.inject` and uses no reflection: `BindingArchitectureSpec` fails on either. Every type is `@Internal`; the code registry (#237) will be its one public type. It compiles with `--release 17`, and `:micronaut-graphql-tools-binding:check` runs in the JDK 17 job.
- `graphql-tools` (the runtime) depends on it with `api`. It holds the runtime adapter of the type model (`RuntimeTypeModel` over `BeanDefinition`, `ExecutableMethod`, `Argument` and `BeanIntrospection`; `RuntimeBeanModel`), the bean discovery, the configuration and the wiring. A rule body lives in `binding` or in no module twice; the runtime reads the type through the adapter and calls the rule.
- `graphql-tools-processor` holds the JSR-269 processor (`io.micronaut.graphql.tools.processing`), which is frozen and goes away with #144. Its main classpaths have no dependency on `graphql-tools` (`checkNoRuntimeDependency`, part of `check`). The element-based adapter of the type model and the visitor are #150.

The startup binding (`GraphQLRuntimeWiringGenerator`) is split by phase. Each phase class owns the mutable state it needs and hands the next phase an immutable result:

| Class | Phase |
|---|---|
| `SchemaCoordinateValidator` | explicit `@GraphQLSchemaCoordinate` declarations, before and after the binding |
| `DictionaryValidator` | the `SchemaMappingDictionary` against itself |
| `ArgumentPlanner`, `InputTypeBinder`, `ScalarMappings` | how parameters bind arguments (the rule is `ResolverClassRules.arguments`), and how input types, enums and scalars bind Java types |
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

## Releases

Only GitHub Packages milestone releases are published (D-40): milestone versions `1.0.0-M<n>` and, later, `1.0.0` go to the Maven registry of this repository, `https://maven.pkg.github.com/donbeave/micronaut-graphql-tools`. Maven Central, GitHub Releases and snapshots stay out of scope until the Foundation contribution. The groupId stays `io.micronaut.graphql.tools`; every consumer-facing page says that the artifacts are unaffiliated milestone previews until contributed.

To cut a release:

1. Check that `master` is green (`required checks`) at the commit to release, and that the release notes are ready.
2. Create and push an annotated tag on that commit: `git tag -a 1.0.0-M2 -m "1.0.0-M2" <sha>` and `git push origin 1.0.0-M2`. The tag must match `^[0-9]+\.[0-9]+\.[0-9]+(-M[0-9]+)?$`.
3. The `Release` workflow (`.github/workflows/release.yml`) starts on the tag push. `verify` builds the tagged commit exactly like the default leg of `gradle.yml` and checks that the build version equals the tag; only then does `publish` upload the library, the annotation processor and the BOM. It publishes from the tag's own sources, so no version bump commit is needed: the tag name is the version (`-PprojectVersion`).
4. To publish a tag that already exists and predates the workflow, run `gh workflow run release.yml --ref master -f tag=<tag>`. The workflow definition and the publishing setup (`.github/gradle/publish-github-packages.init.gradle`) come from `master`, and the sources come from the tag.

A published version is not replaced. If a release is wrong, fix it on `master` and release the next milestone; deleting a version needs repository admin rights (GitHub refuses it for a public version with more than 5,000 downloads). The workflow uses the repository's `GITHUB_TOKEN` with `packages: write` and runs only in `donbeave/micronaut-graphql-tools`. See "Consuming milestone previews" in [README.md](README.md) for how users read the packages. To build a version locally, see "Artifacts and versions" there.
