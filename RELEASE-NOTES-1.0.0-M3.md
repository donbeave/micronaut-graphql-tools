# micronaut-graphql-tools 1.0.0-M3 release notes

Not released yet. Tag `1.0.0-M3` will be published to the GitHub Packages Maven registry of this repository by the `Release` workflow (D-40). This milestone follows [1.0.0-M2](RELEASE-NOTES-1.0.0-M2.md). The numbering continues from the M2 notes: `BC-01` to `BC-19` are described in the M1 notes, `BC-20` in the M2 notes, and `BC-21` is the first numbered change of this milestone, followed by `BC-22`. See the migration page for the fix of each change. This file grows as the milestone's changes land.

## What this release changes

- Performance (D-02): a GraphQL enum is wired with a values provider built at startup from the enum's bean introspection, so an enum argument reaches the resolver as the Java constant without a conversion call. The allocation baseline of the enum argument case drops from 504 B to 24 B (#33).
- Performance (D-02): arguments and input objects are converted by converters that startup builds from the declared type and the bean introspection, so a request runs no Jackson `convertValue` and no reflection. The allocation baselines drop, per operation: a scalar argument 504 B to 24 B, an input object 728 B to 48 B, a list of four input objects 1,648 B to 176 B (#37).

## Breaking changes (BC-21, BC-22)

| ID | PR | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-21 | #33 (PR #342) | A Java enum bound to a GraphQL enum needs a bean introspection. The startup fails with `ClassNotIntrospectedException` for an enum without one. A `RuntimeWiringCustomizer` that registers its own `enumValues` provider for a bound enum fails the startup with `RuntimeWiringOverrideException`. The library no longer reads enum constants by reflection. | none | Annotate the enum with `@Introspected` (`@Serdeable` also introspects it). | Not released yet. Not covered by D-33 |
| BC-22 | #37 | Arguments and input objects are converted from `BeanIntrospection`. Jackson annotations on input classes, naming strategies, `FAIL_ON_UNKNOWN_PROPERTIES` and an `ObjectMapper` bean stop being the input contract, and the `GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER` constant is removed. A list is converted into `List`, `Collection`, `Iterable`, `Set`, `SortedSet` or `Deque` only. | none | Name the properties and constructor parameters of an input class like the input fields. Use a `TypeConverter` bean for a conversion of your own, and the scalar mapping for custom scalars. | Not released yet. Not covered by D-33 |
