# micronaut-graphql-tools 1.0.0-M2 release notes

Tag: `1.0.0-M2` (not released yet). This milestone follows [1.0.0-M1](RELEASE-NOTES-1.0.0-M1.md). The numbering continues from the M1 notes: `BC-01` to `BC-19` are described there, and the first change of this milestone is `BC-20`. See the migration page for the fix of each change.

## Breaking changes (BC-20)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-20 | #173 (PR #311) | The `graphql-tools` `ObjectMapper` bean name and the `GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER` constant are internal (`@Internal`) and no longer documented. The README sections that described Jackson-defined input behaviour (`@JsonIgnore`, naming strategies, a named mapper) are removed. Input conversion is not configurable through a public bean until #37 replaces it. | none | Do not define an `ObjectMapper` bean named `graphql-tools` to configure input conversion. It is not supported. | New in 1.0.0-M2. Not covered by D-33 |
