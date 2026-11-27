/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.client.declarative;

import io.micronaut.graphql.tools.client.annotation.GraphQLClient;
import io.micronaut.graphql.tools.client.annotation.GraphQLOperation;
import io.micronaut.graphql.tools.client.annotation.GraphQLVariable;
import io.micronaut.graphql.tools.client.declarative.Types.Book;
import io.micronaut.graphql.tools.client.declarative.Types.Filter;
import io.micronaut.graphql.tools.client.declarative.Types.Pair;
import io.micronaut.graphql.tools.client.declarative.Types.Sort;
import io.micronaut.graphql.tools.client.GraphQLResponse;
import io.micronaut.graphql.tools.client.internal.GraphQLOperationMetadata;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Header;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.annotation.CookieValue;
import io.micronaut.http.annotation.QueryValue;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * A declarative client whose metadata is written by hand, as the annotation processor writes it, against the echo server.
 */
@GraphQLClient(id = "decl", endpoint = "/echo/graphql")
@Header(name = "X-Static", value = "type")
public interface BookClient {

    String BOOK = "query Book($id: ID!) { book(id: $id) { title pages id } }";
    String ECHO = "query Echo($id: ID, $filter: Filter, $count: Int!, $sorts: [Sort!], $missing: String) { echo }";

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Book book(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    CompletableFuture<Book> bookFuture(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    CompletionStage<Book> bookStage(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Publisher<Book> bookPublisher(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Mono<Book> bookMono(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Flux<Book> bookFlux(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    GraphQLResponse<Book> bookResponse(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    CompletableFuture<GraphQLResponse<Book>> bookResponseFuture(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Mono<GraphQLResponse<Book>> bookResponseMono(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Publisher<GraphQLResponse<Book>> bookResponsePublisher(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    GraphQLResponse<Map<String, Object>> bookDynamicResponse(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    Map<String, Object> bookDynamic(String id);

    @GraphQLOperation(BOOK)
    @GraphQLOperationMetadata(document = BOOK, operationName = "Book", variables = "id", payload = "field")
    GraphQLResponse<?> bookRawResponse(String id);

    @GraphQLOperation("query Missing { book { title } }")
    @GraphQLOperationMetadata(document = "query Missing { book { title } }", operationName = "Missing", payload = "field")
    Book missing();

    @GraphQLOperation("query Missing { book { title } }")
    @GraphQLOperationMetadata(document = "query Missing { book { title } }", operationName = "Missing", payload = "field")
    GraphQLResponse<Book> missingResponse();

    @GraphQLOperation("query Missing { book { title } }")
    @GraphQLOperationMetadata(document = "query Missing { book { title } }", operationName = "Missing", payload = "field")
    Mono<Book> missingMono();

    @GraphQLOperation("query Missing { book { title } }")
    @GraphQLOperationMetadata(document = "query Missing { book { title } }", operationName = "Missing", payload = "field")
    CompletableFuture<Book> missingFuture();

    @GraphQLOperation("mutation Touch { touch }")
    @GraphQLOperationMetadata(document = "mutation Touch { touch }", operationName = "Touch", operationType = "mutation", payload = "field")
    void touch();

    @GraphQLOperation("mutation Touch { touch }")
    @GraphQLOperationMetadata(document = "mutation Touch { touch }", operationName = "Touch", operationType = "mutation", payload = "field")
    CompletableFuture<Void> touchFuture();

    @GraphQLOperation("mutation Touch { touch }")
    @GraphQLOperationMetadata(document = "mutation Touch { touch }", operationName = "Touch", operationType = "mutation", payload = "field")
    Mono<Void> touchMono();

    @GraphQLOperation("mutation Touch { touch }")
    @GraphQLOperationMetadata(document = "mutation Touch { touch }", operationName = "Touch", operationType = "mutation", payload = "field")
    boolean touched();

    @GraphQLOperation("query Pair { a b }")
    @GraphQLOperationMetadata(document = "query Pair { a b }", operationName = "Pair", payload = "data")
    Pair pair();

    @GraphQLOperation("query Pair { a b }")
    @GraphQLOperationMetadata(document = "query Pair { a b }", operationName = "Pair", payload = "data")
    GraphQLResponse<Pair> pairResponse();

    @GraphQLOperation(ECHO)
    @GraphQLOperationMetadata(document = ECHO, operationName = "Echo", variables = {"id", "filter", "count", "sorts", "missing"}, payload = "field")
    Map<String, Object> echo(@GraphQLVariable("id") Long identifier, Filter filter, int count, List<Sort> sorts, @Nullable String missing);

    @GraphQLOperation(ECHO)
    @GraphQLOperationMetadata(document = ECHO, operationName = "Echo", variables = {"id", "filter", "count", "sorts", "missing"}, payload = "field")
    Map<String, Object> echoShuffled(int count, @Nullable String missing, Filter filter, List<Sort> sorts, @GraphQLVariable("id") Long identifier);

    @GraphQLOperation(value = ECHO, method = GraphQLOperation.Method.GET)
    @GraphQLOperationMetadata(document = ECHO, operationName = "Echo", variables = {"id", "count"}, payload = "field")
    Map<String, Object> echoGet(@GraphQLVariable("id") Long identifier, int count);

    @GraphQLOperation(value = ECHO, method = GraphQLOperation.Method.GET)
    @GraphQLOperationMetadata(document = ECHO, operationName = "Echo", variables = {"filter", "sorts", "missing"}, payload = "field")
    Map<String, Object> echoGetObjects(Filter filter, List<Sort> sorts, @Nullable String missing);

    @GraphQLOperation(ECHO)
    @GraphQLOperationMetadata(document = ECHO, operationName = "Echo", variables = {"count"}, payload = "field")
    @Header(name = "X-Method", value = "method")
    Map<String, Object> echoHeaders(int count, @Header("X-Token") String token, @Header String requestId, @CookieValue("session") String session,
                                    @Nullable @Header("X-Absent") String absent, @Header("X-Number") int number);

    String FAIL = "query Fail { book { title pages id } }";

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    Book fail();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    GraphQLResponse<Book> failResponse();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    CompletableFuture<Book> failFuture();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    CompletableFuture<GraphQLResponse<Book>> failResponseFuture();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    Mono<Book> failMono();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    void failVoid();

    @GraphQLOperation(FAIL)
    @GraphQLOperationMetadata(document = FAIL, operationName = "Fail", payload = "field")
    Mono<Void> failVoidMono();

    @Get("/plain")
    String plain();
}
