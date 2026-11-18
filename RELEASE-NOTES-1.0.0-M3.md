# micronaut-graphql-tools 1.0.0-M3 release notes

Not released yet. Tag `1.0.0-M3` will be published to the GitHub Packages Maven registry of this repository by the `Release` workflow (D-40). This milestone follows [1.0.0-M2](RELEASE-NOTES-1.0.0-M2.md). The numbering continues from the M2 notes: `BC-01` to `BC-19` are described in the M1 notes, `BC-20` in the M2 notes, and `BC-21` and `BC-22` are the numbered changes of this milestone. See the migration page for the fix of each change. This file grows as the milestone's changes land.

## What this release changes

- Performance (D-02): a GraphQL enum is wired with a values provider built at startup from the enum's bean introspection, so an enum argument reaches the resolver as the Java constant without a conversion call. The allocation baseline of the enum argument case drops from 504 B to 24 B (#33).
- Schema discovery (#17): without a `TypeDefinitionRegistry` bean the library reads `micronaut.graphql.tools.schema.locations` (default `classpath:graphql/**/*.graphqls`; `classpath*:` for dependency jars, `file:` for the file system) and merges the files, each parsed with its own name. A schema without a `schema { }` block uses `Query`, `Mutation` and `Subscription` as root types. A file that holds only operations is skipped. A native image reads the schema index `META-INF/micronaut-graphql-tools/schema-index.txt`. The compile-time processor reads its schema directory with the same code.

## Breaking changes (BC-21, BC-22)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-21 | #33 (PR #342) | A Java enum bound to a GraphQL enum needs a bean introspection. The startup fails with `ClassNotIntrospectedException` for an enum without one. A `RuntimeWiringCustomizer` that registers its own `enumValues` provider for a bound enum fails the startup with `RuntimeWiringOverrideException`. The library no longer reads enum constants by reflection. | none | Annotate the enum with `@Introspected` (`@Serdeable` also introspects it). | Not released yet. Not covered by D-33 |
| BC-22 | #17 | The compile-time processor reads `*.graphqls` files only; a `*.graphql` file in its schema directory is no longer read. | none | Rename schema files to `.graphqls`. Operation files may stay `.graphql`. | Not released yet. Not covered by D-33 |

## Other behaviour changes

- An application with no `TypeDefinitionRegistry` bean now reads the default schema location instead of failing with a missing bean. When no file is found the startup fails with `SchemaDiscoveryException`.
- A schema without a `schema { }` block binds. Before, `SchemaDefinitionNotProvidedException` failed it; it now fails only a schema that has none of the root types.
