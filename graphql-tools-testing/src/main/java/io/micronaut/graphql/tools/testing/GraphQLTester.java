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
package io.micronaut.graphql.tools.testing;

import graphql.ExecutionResult;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MutableHttpRequest;
import jakarta.inject.Singleton;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Runs GraphQL documents against the application's own schema, in the process of the test and without a server or an HTTP client.
 * The bean is available when the application has the {@code GraphQLInvocation} bean, that is, in every application of this library,
 * and an application test injects it:
 *
 * <pre>{@code
 * response = tester.request("query($id: ID!) { book(id: $id) { title } }").variable("id", "b1").execute();
 * response.assertNoErrors().assertPath("book.title", "Dune");
 * }</pre>
 *
 * <p>The document goes through the same invocation as an HTTP request: the executor hop, the
 * {@link io.micronaut.graphql.tools.execution.GraphQLContextContributor} beans, the customizers, the instrumentations and the
 * error handling all run. The invocation gets a synthetic HTTP request ({@code POST /graphql}, with the headers of the call and the
 * transport {@code HTTP}), so a contributor that reads a header and a request-scoped bean work as they do for a client.
 * The call blocks the calling thread until the operation completes or the timeout passes.</p>
 */
@Singleton
public final class GraphQLTester {

    /** The attribute of the synthetic request that carries the context values of the call. */
    public static final String CONTEXT_ATTRIBUTE = "io.micronaut.graphql.tools.testing.context";

    /** How long a call waits for the operation to complete, unless the request sets another timeout. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /** The path of the synthetic request. */
    static final String PATH = "/graphql";

    private final GraphQLInvocation invocation;

    /**
     * @param invocation the invocation that serves the application's GraphQL endpoint
     */
    public GraphQLTester(@NonNull GraphQLInvocation invocation) {
        this.invocation = Objects.requireNonNull(invocation, "invocation");
    }

    /**
     * Starts a request for a document.
     *
     * @param document the query, mutation or subscription document
     * @return the request, which a caller completes with variables, context values and headers
     */
    @NonNull
    public GraphQLTestRequest request(@NonNull String document) {
        return new GraphQLTestRequest(this, Objects.requireNonNull(document, "document"));
    }

    /**
     * Runs a document without variables or context.
     *
     * @param document the document
     * @return the response
     */
    @NonNull
    public GraphQLTestResponse execute(@NonNull String document) {
        return request(document).execute();
    }

    GraphQLTestResponse run(GraphQLTestRequest call) {
        MutableHttpRequest<String> http = HttpRequest.POST(PATH, "");
        call.headers().forEach(http::header);
        if (!call.context().isEmpty()) {
            http.setAttribute(CONTEXT_ATTRIBUTE, Map.copyOf(call.context()));
        }
        Publisher<ExecutionResult> publisher = invocation.invoke(
                new GraphQLInvocationData(call.document(), call.operationName(), call.variables()), http, HttpResponse.ok(""));
        ExecutionResult result = Mono.from(publisher).block(call.timeout());
        if (result == null) {
            throw new AssertionError("The operation completed without a result");
        }
        return new GraphQLTestResponse(result);
    }

}
