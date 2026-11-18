# micronaut-graphql-tools 1.0.0-M3 release notes

Not released yet. This file collects the changes that are on `master` for the 1.0.0-M3 milestone; the tag and the publishing are not done. It follows [1.0.0-M2](RELEASE-NOTES-1.0.0-M2.md). The numbering continues from the M2 notes: `BC-20` is described there, and the changes of this milestone start at `BC-22` (`BC-21` is the non-introspected enum change of #342). See the migration page for the fix of each change.

## What this release changes

Issue numbers refer to this repository.

- Diagnostics: one code registry for the strict checks and the startup failures, `BindingCode` in `graphql-tools-binding` (#237). Every startup message starts with `[code]`, `SchemaBindingException#code()` returns it, the `!` lines of the binding manifest are named by it, and the compile-time diagnostics use the same codes. The error reference lists them.

## Breaking changes (BC-22)

| ID | Issue | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-22 | #237 | Every startup exception extends the new public `SchemaBindingException`, and its message starts with `[code]`. The `!` lines of the binding manifest are named by the code: `!unbound` is `!unbound-resolver-method`, `!unbound-enum-constant` or `!unmapped-input-property`; `!unusedDictionary` is `!unused-dictionary-entry`; `!ambiguous` is `!precedence-ambiguity`; `!unreachable` is `!unreachable-type` or `!unreachable-value-type`; `!unwiredDirective` is `!unwired-directive`. The labels `[invalid-schema-coordinate]` and `[strict]` of `BindingFailuresException` are `[unknown-schema-coordinate]` and `[strict-findings]`. | none | Match on `SchemaBindingException#code()`, not on message text. Read the new words from the manifest. | New in 1.0.0-M3. Not covered by D-33 |

## Known limitations

- The compile-time manifest that the JSR-269 processor writes keeps the old words of the `!` lines until #144 removes the processor.
- Per-check severity (`checks.<code>`, `allow.findings`) is #86; the codes it will use are the ones of the registry.
