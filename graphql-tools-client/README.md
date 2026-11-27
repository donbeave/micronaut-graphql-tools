# micronaut-graphql-tools-client

Gradle project `:micronaut-graphql-tools-client`, folder `graphql-tools-client/`, artifact
`io.micronaut.graphql.tools:micronaut-graphql-tools-client`.

## Purpose

The GraphQL client of the library for Micronaut 4: the runtime that an application uses to call a GraphQL server. The module
holds the packages, the dependencies, the check that keeps the server stack out and the annotations of the declarative client
(`@GraphQLClient`, `@GraphQLOperation` and `@GraphQLVariable`, described in the guide page Client). The types of a call (request, response, error, exceptions) and the Serde codec of the wire format are here too. The low-level client, `GraphQLHttpClient`, is here: reactive, `CompletableFuture` and blocking calls over the Micronaut HTTP client, an injection-point factory (`@Inject @GraphQLClient("id") GraphQLHttpClient`), the classification of responses and GET queries. The runtime that
implements a client interface arrives with the client phases.

## Responsibility

- The public packages of the client: `io.micronaut.graphql.tools.client` (the root), `...client.annotation` and
  `...client.exceptions`. The root holds `GraphQLHttpClient`, `GraphQLRequest`, `GraphQLResponse`, `GraphQLError`, `OperationType`, `GraphQLMediaTypes` and
  `GraphQLClientAttributes`; the annotation package holds the three annotations; the exceptions package holds `GraphQLClientException`,
  `GraphQLErrorsException` and `GraphQLTransportException`.
- The internal packages: `...client.internal` (the call path), `...client.internal.serde` (the Micronaut Serialization codec: it
  encodes a request, decodes the response envelope with the presence of `data` and unwraps the single root field of `data`) and `...client.internal.ws` (the WebSocket transport).
- The `checkNoGraphQLJava` task, part of `check`: it fails when graphql-java or the graphql-tools runtime is on a main
  classpath of this module.

Not in this module: the server (`graphql-tools`), the transport of the server (`micronaut-graphql`), and the native tests of
the client (#681).

## Packages

Each package has a `package-info.java`. See the [API map](../CONTRIBUTING.md#api-map).

| Package | Audience | Holds |
| --- | --- | --- |
| `io.micronaut.graphql.tools.client` | Public API | The low-level client, the request, response, error, operation type, media types and request attributes |
| `...client.annotation` | Public API | `GraphQLClient`, `GraphQLOperation` and `GraphQLVariable` |
| `...client.exceptions` | Public API | The exceptions of a client call |
| `...client.internal` | Internal | The call path (the implementation of `GraphQLHttpClient`, the response classifier and the factory of the injection points), the startup check for Micronaut Serialization and `GraphQLOperationMetadata`, the annotation that the compile-time visitor writes on an operation method |
| `...client.internal.serde` | Internal | The Serde codec of the request and the response envelope |
| `...client.internal.ws` | Internal | The WebSocket transport, present with the Micronaut WebSocket client |

## Who uses it

Applications that call a GraphQL server from Micronaut, and do not serve GraphQL themselves.

## Public and internal

- The public packages hold the API. Every class under `io.micronaut.graphql.tools.client.internal` is `@Internal`.
- The API names no graphql-java, graphql-tools, Jackson or Reactor type.

## Relation to other modules

- Depends on `micronaut-http-client-core` (`api`) and on the Micronaut Serialization API (`api`, `micronaut-serde-api`).
- Depends on the Micronaut WebSocket client and the retry module at compile time only (`compileOnly`); an application adds them
  to use the subscriptions and the retries.
- It does not depend on `graphql-tools`, `graphql-tools-binding` or `graphql-tools-processor`. `checkNoGraphQLJava` keeps it so.
- It is published with the other library modules and managed by `graphql-tools-bom`.

## Build and test

Run from the repository root:

```
./gradlew :micronaut-graphql-tools-client:check
```

The module compiles with `--release 17`, so the check also runs on JDK 17. The Spock specs run the codec on Micronaut Serialization, and `GraphQLHttpClientSpec` runs the client against a stub server on an embedded Netty server (the matrix of statuses by body kind, the POST and GET requests, the filters, the discovery by service id, the timeouts and the blocking call on an event loop). `ClientAnnotationsSpec` checks that the aliases of `@GraphQLClient` resolve to `@Client`. `checkNoGraphQLJava` runs as
part of `check`.
