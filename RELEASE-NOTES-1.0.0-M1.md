# micronaut-graphql-tools 1.0.0-M1 release notes

Tag: `1.0.0-M1` (not created yet). This tag is not a published release. There is no released version before it, so none of the changes below breaks a published API. They matter for applications that run `1.0.0-SNAPSHOT`.

## What this release changes

The stack makes the binding between the SDL schema and the Java code safe: drift between the two fails the build (annotation processor) or the application startup, not a request at runtime.

- Build on Java 21; CI builds and tests graphql-java 21.3 and 25.1 (#2).
- Type system: SDL interfaces, Java interfaces as object types, union and interface member resolution (#3).
- Inputs and scalars: scalar SPI (`GraphQLScalarMapping`), generic argument conversion, `ArgumentValue` to tell an omitted argument from `null` (#4).
- Public seams: registry, wiring and schema customizers, `GraphQLBuilderCustomizer`, `SchemaDirective` wirings, a conditional `GraphQL` bean (#5, integrated by #242).
- Binding safety: explicit precedence, root resolvers per operation type, every binding failure reported at once, binding manifest, strict mode (#6).
- Compile-time verification: new module `micronaut-graphql-tools-processor` checks the SDL against the Java bindings (#7).

## Behaviour changes (BC-01 to BC-18)

"Opt-out" names the setting that restores the old behaviour. Keys are under `micronaut.graphql.tools.`; see [Opt-outs](#opt-outs). "Status" is the state after issue #79, which is still open: the status column shows its recommendation, and no decision is recorded yet.

| ID | PR and commit | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-01 | #2 `11293ef` | `List<CompletionStage<T>>` return type is a startup error (`UnsupportedReturnTypeException`). Before, the client received the future's `toString()`. | none | Return `CompletionStage<List<T>>`. | Pending #79; recommended: approve as is |
| BC-02 | #2 `11293ef`, `9bd50b9` | `Optional` on a non-null type, misplaced or doubled wrappers, `Publisher` outside a subscription field, and a subscription field that does not return `Publisher` are startup errors. | none | Remove `Optional` from non-null types. Return `Publisher` only on subscription fields. Reactive applications that return `Publisher` or `Flux` on other fields will hit this error. | Pending #79; recommended: approve (the `Publisher` part is a stop-gap until #75) |
| BC-03 | #2 `92f361d` | The published POM declares extended-scalars 21.0 (was 20.2). The build requires Java 21. Snapshot publishing: nothing is published automatically; see [Known limitations](#known-limitations). | none | Build on Java 21. If you pin extended-scalars, use 21.0. | Not covered by #79 |
| BC-04 | #3 `feb7dcf` | Resolver stereotypes are `@Inherited`. A `@Singleton` subclass of an annotated class now registers as a resolver, which can create new ambiguities. | none | Remove the stereotype from test fakes and decorators, or mark them `@Requires(env = "test")`. | Pending #79; recommended: approve with a fix on top (#73) |
| BC-05 | #3 `66679ed` | For an interface with its own introspection, properties are read through that introspection. Getters that only an implementation declares stop binding. | none | Declare the getter on the interface. | Pending #79; recommended: approve as is (the mode is logged, #74) |
| BC-06 | #3 `118c40d` | A union member or interface implementation that is mapped outside the field's declared Java interface is a startup error (`IncorrectMemberMappingException`). | none | Map the member so that it matches the field's declared Java interface. | Pending #79; recommended: approve with a narrower check on top (#78) |
| BC-07 | #3 `57d4042`, `c606f03` | Public rename `UnionTypeResolver` to `AbstractTypeResolver`. Dictionary error messages changed. | none | Rename the type in imports and in `extends`. | Not covered by #79 |
| BC-08 | #4 `6344b10`, #7 `be9da1f` | Input class properties that the input type does not define, and input fields that the class cannot set, are startup errors. | `micronaut.graphql.tools.input.unmapped-properties` (`ignore` or `fail`), then `micronaut.graphql.tools.strict: false` | Add the field to the input type, or remove the property. Make the field settable (setter or constructor). | Opt-out exists; not covered by #79 |
| BC-09 | #7 `be9da1f` | A primitive for a nullable built-in scalar (`int` for `Int`) is a startup error in strict mode. | `micronaut.graphql.tools.strict: false` | Use the wrapper class (`Integer`), or make the GraphQL type non-null. | Opt-out exists; not covered by #79 |
| BC-10 | #5 `6ca8658` | The `GraphQL` bean is created only if the application has none. The `GraphQLSchema` bean is exposed. | none | If you define your own `GraphQL` bean, the library does not create one. Use `GraphQLBuilderCustomizer` to change the library's `GraphQL`. | Pending #79; recommended: approve as is |
| BC-11 | #5 `57ba63a` | A bare `SchemaDirectiveWiring` bean (ignored before) is a startup error (`UnwrappedDirectiveWiringException`). | none | Wrap the wiring in a `SchemaDirective` bean: `named(name, wiring)` for one directive, `everyElement(names, wiring)` for several. | Pending #79; recommended: approve, and name the fix in the error |
| BC-12 | #5 `96dd7d0`, w2 `33cb599` | A directive that the schema applies without a wiring is a startup error (`UnwiredDirectivesException`). | `micronaut.graphql.tools.inert-directives` (list of directive names); with `33cb599`, also `micronaut.graphql.tools.strict: false` (then a warning) | Add a wiring, or list the directive in `inert-directives` if it has no runtime effect. | Opt-out exists; not covered by #79 |
| BC-13 | #5 `c038b38` | A `RuntimeWiringCustomizer` that replaces the library's wiring is a startup error (`RuntimeWiringOverrideException`). | none | Add to the library's wiring in the customizer; do not replace it. | Pending #79; recommended: approve with a fix on top (`claimedFields()`, #67) |
| BC-14 | #6 `a11134e` | A field that a property and a type resolver method can both bind is a startup error (`AmbiguousBindingException`). Before, the property won silently. | `micronaut.graphql.tools.precedence: property-first` | Remove one of the two members, or choose the winner with `precedence`. Use `resolver-directives` to choose per field. | Opt-out exists; not covered by #79 |
| BC-15 | #6 `a189200` | Root resolvers are scoped per operation type (`Query`, `Mutation`, `Subscription`). Two root methods for one field are a startup error (`MultipleMethodsFoundException`). Before, the first bean found won. | none | Add `@GraphQLSchemaCoordinate("Query.field")` (or `Mutation`, `Subscription`) to each method. Keep one method per root field. | Pending #79; recommended: approve as is (`@Replaces` resolves override cases) |
| BC-16 | #6 `a18f948` | `strict: true` is the default. Stale resolver methods, missing enum constants, stale dictionary entries, unused enum, input and scalar types, and interface-mapped types without their own introspection are startup errors. | `micronaut.graphql.tools.strict: false` | Fix each finding listed in [Strict mode](README.md#strict-mode), or set `strict: false`. | Opt-out exists; not covered by #79 |
| BC-17 | #7 `d2e5ae7` | Fields of `extend type` are bound and checked. Before, graphql-java's default fetcher served them without any check. | none | Give each extended field a resolver or property. The startup names the coordinate. | Pending #79; recommended: approve as is |
| BC-18 | #2 `3be0c73` | Unreachable types: the startup still fails by default, and now lists all of them. Entries in `allow-unreachable` that are no longer unreachable fail the startup. | `micronaut.graphql.tools.unreachable-types: ignore` | Remove stale entries from `allow-unreachable`. | Opt-out exists; not covered by #79 |

## Opt-outs

| Key | Values (default first) | Restores | Note |
|---|---|---|---|
| `micronaut.graphql.tools.strict` | `true` \| `false` | BC-08, BC-09, BC-12, BC-16 | Fails the startup on the strict-mode findings. `false` binds as before and lists them in the binding manifest. |
| `micronaut.graphql.tools.input.unmapped-properties` | not set \| `fail` \| `ignore` | BC-08 | Not set: fails in strict mode, accepted without it. `ignore` accepts them in strict mode too. |
| `micronaut.graphql.tools.precedence` | `strict` \| `property-first` \| `resolver-first` | BC-14 | `property-first` is the behaviour before the option existed. |
| `micronaut.graphql.tools.inert-directives` | list of directive names (empty) | BC-12 | Directives with no runtime effect that may be applied without a wiring. |
| `micronaut.graphql.tools.unreachable-types` | `fail` \| `ignore` | BC-18 | `ignore` keeps unreachable types and logs them as a warning. |

The annotation processor reads the same settings as build options, with camelCase names, for example `-Amicronaut.graphql.tools.input.unmappedProperties=ignore`. An unknown option under that prefix is an error.

## Known limitations

- **Native image.** No PR in this stack ran native image tests: each PR body says "Not run: native image tests". Earlier analysis recorded two native-only failures of the binding manifest test. This document does not re-verify them.
- **Annotation processor.** `micronaut-graphql-tools-processor` is new. The README and the code do not mark it experimental yet. PR #7 lists these follow-ups:
  - A concrete public method in a `@GraphQLRootResolver` superclass that the bean overrides still fails at startup with "Found multiple methods", while the processor accepts it.
  - Union members and interface implementations come from the runtime dictionary, so "one member class per contract" is checked at startup, not by `javac`.
  - `@Introspected(accessKind = FIELD)` models are skipped by the processor with a note. Startup checks them.
  - kapt is documented in the README but is not exercised by the library's tests.
- **Review findings.** Blocking review findings are fixed on top of this stack in separate changes tracked in issue #172. The stack PRs themselves are not changed.
- **Pending decisions.** The status column reflects issue #79, which is open. Until its decisions are recorded, the status of BC-01, BC-02, BC-04, BC-05, BC-06, BC-10, BC-11, BC-13, BC-15 and BC-17 is provisional.
- **Snapshot publishing (BC-03).** Nothing is published. The README "Snapshots and Releases" section and CONTRIBUTING state that no snapshot or release is available and no workflow publishes one (only `.github/workflows/gradle.yml` builds and tests). To use this stack, build it yourself and publish it to a local or private repository.

## Stack PRs on master

Merged with merge commits, in this order: #2 (`d8d353a`), #3 (`40d12c7`), #6 (`cebdd17`), #7 (`a5cbee3`, brings #4), #242 (`b308c37`, brings #5).
