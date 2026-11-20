# micronaut-graphql-tools 1.0.0-M4 release notes

Not released yet. This milestone follows [1.0.0-M3](RELEASE-NOTES-1.0.0-M3.md). The numbering of the breaking changes continues from the M3 notes.

## What this release changes

- Resolver marker interfaces (#73, BC-34): `QueryResolver`, `MutationResolver`, `SubscriptionResolver` and `TypeResolver<T>` in the package `io.micronaut.graphql.tools.resolver`. A marker fixes the root operation type (the root type that the schema names for the operation), `TypeResolver<T>` gives the source type as a type argument with no class literal, and only the methods that implement a method of a contract interface bind, so a public helper of an implementation binds nothing and does not trip strict mode. The visitor of `graphql-tools-processor` records the operations, the source type and the contract methods in the annotation metadata of the implementation, and the startup reads them from the bean definition. The reflective read of a class annotation in the registry is removed. The compile-time checks and the startup use the same rules for the markers.
- Kotlin `suspend` resolvers (#185): a `suspend` function binds as a query, mutation or type resolver method, its continuation is not a parameter, and the field is an async field. The support is a bean of the library that exists only when the Kotlin and kotlinx-coroutines classes are on the classpath, so a Java-only application and its native image link no Kotlin type. The propagated context is visible inside the coroutine, an exception becomes an error of the field, and cancelling the field cancels the coroutine. Binding a `suspend` function without kotlinx-coroutines-core fails the startup with `[suspend-without-coroutines]`.

## Breaking changes (BC-34)

| ID | Issue | What changes | Opt-out | Migration hint | Status |
|---|---|---|---|---|---|
| BC-34 | #73 | `@GraphQLRootResolver` and `@GraphQLTypeResolver` no longer carry the `@Bean` and `@DefaultScope(Singleton)` stereotype, which BC-04 introduced. A class that carries one, extends an annotated class or implements an annotated interface is a bean only when it has a scope annotation of its own, so a resolver class needs `@Singleton`. A type resolver bean that has a supertype besides `Object` and was compiled without the processor fails the startup with `[type-resolver-contracts-not-found]`. | none | Add `@Singleton` to each resolver class and `@Replaces(<original>.class)` to a fake or a subclass of a resolver bean. | Breaking by design |
