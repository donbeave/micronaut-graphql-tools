# micronaut-graphql-tools 1.0.0-M2 release notes

Tag: `1.0.0-M2`, published to the GitHub Packages Maven registry of this repository by the `Release` workflow (D-40). It is not on Maven Central and there is no GitHub Release. This milestone follows [1.0.0-M1](RELEASE-NOTES-1.0.0-M1.md). The numbering continues from the M1 notes: `BC-01` to `BC-19` are described there, and the only numbered change of this milestone is `BC-20`. See the migration page for the fix of each change.

## What this release changes

The binding stack of M1 becomes faster to start and easier to diagnose, and the build and CI move to the version policy (D-34, D-35, D-41). Issue numbers refer to this repository.

- Startup and diagnostics:
  - The schema is bound when the application context starts, so a broken binding fails the startup, not the first request (#51). `micronaut.graphql.tools.eager-init: false` restores lazy binding.
  - Unreachable types, invalid schema coordinates and strict findings are reported in one aggregated startup error (#84).
  - An abstract-type member mapping that is ambiguous fails the startup (#77), and abstract types resolve to the most specific member (#78).
  - A directive wired by both a `named` and an `everyElement` `SchemaDirective` fails the startup with `DuplicateDirectiveWiringException` (#164).
  - A customizer that shadows a library-bound field is reported with the coordinate, the customizer and the fix (#166). Several startup errors name their fix (#247).
  - Allowing or ignoring an unreachable type also covers the enums, inputs and scalars it uses (#83, runtime half; the compile-time half is open).
  - Escape hatches: `@GraphQLIgnore`, and lifecycle methods (`@EventListener`, `@Scheduled`, `@PreDestroy`) no longer count as unbound methods in strict mode (#106; the compile-time processor applies the same rules). `RuntimeWiringCustomizer#claimedFields()` is available (#67). A bare `GraphQLScalarType` bean that is named like a schema scalar is named in the missing-scalar error with the `GraphQLScalarMapping.of` fix (#176).
  - The conditional `GraphQL` and `GraphQLSchema` beans step aside only for an unqualified or `@Primary` application bean, and log the decision (#165).
  - The binding manifest is recorded only when `micronaut.graphql.tools.manifest.record` or `manifest.enabled` is `true`, and rendered without reflection (#91, #64).
  - A class that backs two input types keeps `ArgumentValue` for both (#160).
- Performance (D-02): per-field fetchers and argument plans are precomputed (#287), abstract types resolve through a `ClassValue` cache (#69), the object-method check uses a fixed signature set (#92). A JMH `benchmarks` module with allocation gates measures the request hot path, and a benchmark measures binding time and retained heap at 100, 1,600 and 10,000 fields (#43, #39 in part).
- graphql-java: legs are named by role: floor 21.5, default 22.4 (managed by `micronaut-graphql` 4.8.0) and ceiling 26.2 (#11, #260). The removed `DirectiveInfo` is replaced. A canary workflow tests JDK 27 and the newest graphql-java (#114).
- Build tooling: Micronaut core 4.10.37, platform 4.10.24, micronaut-test 4.10.3 (#115); dependencies the Micronaut BOMs and catalogs manage come from them, and `gradle/libs.versions.toml` holds only the rest (#307); Apollo Kotlin 5.2.0 and floor java-dataloader 3.2.2 (#295). The library compiles with `--release 17`, and `-Werror` applies to its main compilation only (#169).
- Test suites: the example application is split into `test-suite-java`, `test-suite-kotlin` and `test-suite-groovy` (#55). Native image tests run in CI on Oracle GraalVM 25 (#118). Tests cover null fields in responses under each JSON configuration (#45) and root methods of one name per operation type (#157).
- CI and repository: one `required checks` gate job, squash-only merges, a consumer-name guard job, Renovate rules for the version policy, Gradle and tool caching with mise, and `jactionlint` for the workflow lint (#9, #153, #258, #261, #262, #280). Milestone versions are published to GitHub Packages from a tag-triggered workflow (#305).
- Documentation: the guide skeleton with snippets included from the test suites and a docs CI job (#57), a migration page and a breaking-changes page, README corrections (#59), thread-selection notes for blocking resolvers (#14), `AGENTS.md` (#168), and DCO sign-off wording in `MAINTAINING.md` (#241).

## Breaking changes (BC-20)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-20 | #173 (PR #311) | The `graphql-tools` `ObjectMapper` bean name and the `GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER` constant are internal (`@Internal`) and no longer documented. The README sections that described Jackson-defined input behaviour (`@JsonIgnore`, naming strategies, a named mapper) are removed. Input conversion is not configurable through a public bean until #37 replaces it. | none | Do not define an `ObjectMapper` bean named `graphql-tools` to configure input conversion. It is not supported. | New in 1.0.0-M2. Not covered by D-33 |

## Other behaviour changes since 1.0.0-M1

These changes have no BC number. Each can fail an application that started on 1.0.0-M1; the error names the fix.

- Eager binding (#51): a broken binding now fails the startup. Opt-out: `micronaut.graphql.tools.eager-init: false`.
- Ambiguous abstract-type mapping (#77) and a directive wired by both a `named` and an `everyElement` bean (#164) fail the startup. No opt-out.
- The binding manifest is no longer recorded by default (#91). Opt-in: `micronaut.graphql.tools.manifest.record: true` (or `manifest.enabled`). A `BindingManifest` bean without it fails with a message that names the key.
- A qualified second `GraphQL` or `GraphQLSchema` bean no longer disables the library's bean (#165).

## Known limitations

- **Native image.** Native tests run on Oracle GraalVM 25 in CI. Issue #285 (the native image selecting `JacksonDatabindMapper` over the Serde mapper) is open for 1.0.0-M3.
- **Annotation processor.** The limitations listed in the [1.0.0-M1 notes](RELEASE-NOTES-1.0.0-M1.md#known-limitations) still apply. The processor and the startup check do not agree on every case of unreachable types (#83) and of a superclass root-resolver method (#226); both are open for 1.0.0-M3.
- **Input conversion.** The input conversion still goes through a Jackson `ObjectMapper` internally. It is not configurable through a public bean until #37 replaces it.
- **Publishing.** Only GitHub Packages milestone versions are published, and downloading needs a token with `read:packages` even though the packages are public. See [Consuming milestone previews](README.md#consuming-milestone-previews).
