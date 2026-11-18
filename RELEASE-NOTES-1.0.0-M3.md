# micronaut-graphql-tools 1.0.0-M3 release notes

Tag: `1.0.0-M3`. This release is not released yet: these notes list what has landed on `master` so far. It follows [1.0.0-M2](RELEASE-NOTES-1.0.0-M2.md). The numbering continues from the M2 notes: `BC-20` is described there and `BC-21` is reserved, so the first numbered change here is `BC-22`. See the migration page for the fix of each change.

## What this release changes

Issue numbers refer to this repository.

- Schema discovery (#17): without a `TypeDefinitionRegistry` bean the library reads `micronaut.graphql.tools.schema.locations` (default `classpath:graphql/**/*.graphqls`; `classpath*:` for dependency jars, `file:` for the file system) and merges the files, each parsed with its own name. A schema without a `schema { }` block uses `Query`, `Mutation` and `Subscription` as root types. A file that holds only operations is skipped. A native image reads the schema index `META-INF/micronaut-graphql-tools/schema-index.txt`. The compile-time processor reads its schema directory with the same code.

## Breaking changes (BC-22)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-22 | #17 | The compile-time processor reads `*.graphqls` files only; a `*.graphql` file in its schema directory is no longer read. | none | Rename schema files to `.graphqls`. Operation files may stay `.graphql`. | New in 1.0.0-M3. Not covered by D-33 |

## Other behaviour changes since 1.0.0-M2

- An application with no `TypeDefinitionRegistry` bean now reads the default schema location instead of failing with a missing bean. When no file is found the startup fails with `SchemaDiscoveryException`.
- A schema without a `schema { }` block binds. Before, `SchemaDefinitionNotProvidedException` failed it; it now fails only a schema that has none of the root types.
