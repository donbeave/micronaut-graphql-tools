# docs-examples-groovy

Gradle project `:micronaut-docs-examples-groovy`, folder `docs-examples-groovy/`. Not published.

## Purpose

The quickstart of the user guide in Groovy: a schema, a `TypeDefinitionRegistry` bean, a root resolver and a test that runs the
first query.

## Responsibility

- `src/main`: the quickstart classes and `schema.graphqls`, which the guide includes with the `snippet::` macro
  (`project-base="docs-examples"`).
- `src/test`: `QuickStartTest`, which starts an application context and executes the first query.

Not in this module: the behavioural tests, which stay in the `test-suite-*` modules.

## Who uses it

The guide build and maintainers. Application developers read the snippets in the guide.

## Public and internal

Example code, not a library package. Nothing here is API.

## Relation to other modules

It depends on `micronaut-graphql-tools`. The `test-suite-*` modules hold the behavioural tests of the same features.

## Build and test

```
./gradlew :micronaut-docs-examples-groovy:test
```
