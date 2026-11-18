# micronaut-graphql-tools 1.0.0-M3 release notes

Not released yet. Tag `1.0.0-M3` will be published to the GitHub Packages Maven registry of this repository by the `Release` workflow (D-40). This milestone follows [1.0.0-M2](RELEASE-NOTES-1.0.0-M2.md). The numbering continues from the M2 notes: `BC-01` to `BC-19` are described in the M1 notes, `BC-20` in the M2 notes, and `BC-21` and `BC-22` are the numbered changes of this milestone so far. See the migration page for the fix of each change. This file grows as the milestone's changes land.

## What this release changes

- Performance (D-02): a GraphQL enum is wired with a values provider built at startup from the enum's bean introspection, so an enum argument reaches the resolver as the Java constant without a conversion call. The allocation baseline of the enum argument case drops from 504 B to 24 B (#33).
- Diagnostics: one code registry for the strict checks and the startup failures, `BindingCode` in `graphql-tools-binding` (#237). Every startup message starts with `[code]`, `SchemaBindingException#code()` returns it, the `!` lines of the binding manifest are named by it, and the compile-time diagnostics use the same codes. The error reference lists them.

## Breaking changes (BC-21, BC-22)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-21 | #33 (PR #342) | A Java enum bound to a GraphQL enum needs a bean introspection. The startup fails with `ClassNotIntrospectedException` for an enum without one. A `RuntimeWiringCustomizer` that registers its own `enumValues` provider for a bound enum fails the startup with `RuntimeWiringOverrideException`. The library no longer reads enum constants by reflection. | none | Annotate the enum with `@Introspected` (`@Serdeable` also introspects it). | Not released yet. Not covered by D-33 |
| BC-22 | #237 | Every startup exception extends the new public `SchemaBindingException`, and its message starts with `[code]`. The `!` lines of the binding manifest are named by the code: `!unbound` is `!unbound-resolver-method`, `!unbound-enum-constant` or `!unmapped-input-property`; `!unusedDictionary` is `!unused-dictionary-entry`; `!ambiguous` is `!precedence-ambiguity`; `!unreachable` is `!unreachable-type` or `!unreachable-value-type`; `!unwiredDirective` is `!unwired-directive`. The labels `[invalid-schema-coordinate]` and `[strict]` of `BindingFailuresException` are `[unknown-schema-coordinate]` and `[strict-findings]`. | none | Match on `SchemaBindingException#code()`, not on message text. Read the new words from the manifest. | Not released yet. Not covered by D-33 |

## Known limitations

- The compile-time manifest that the JSR-269 processor writes keeps the old words of the `!` lines until #144 removes the processor.
- Per-check severity (`checks.<code>`, `allow.findings`) is #86; the codes it will use are the ones of the registry.
