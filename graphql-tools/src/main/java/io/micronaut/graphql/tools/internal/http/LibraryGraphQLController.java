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
package io.micronaut.graphql.tools.internal.http;

import io.micronaut.configuration.graphql.GraphQLController;
import io.micronaut.configuration.graphql.GraphQLExecutionResultHandler;
import io.micronaut.configuration.graphql.GraphQLInvocation;
import io.micronaut.configuration.graphql.GraphQLInvocationData;
import io.micronaut.configuration.graphql.GraphQLJsonSerializer;
import io.micronaut.configuration.graphql.GraphQLRequestBody;
import io.micronaut.configuration.graphql.RequiresGraphQL;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.Map;

/**
 * The library's HTTP endpoint of the GraphQL transport. It replaces the controller of {@code micronaut-graphql} on the same
 * path ({@code graphql.path}) under the same switch ({@code graphql.enabled}), reads the request the same way and runs it through
 * the {@link GraphQLInvocation} bean exactly as that controller did. What it adds is the response: it produces
 * {@code application/graphql-response+json} and {@code application/json}, negotiated by {@code Accept} (see
 * {@link GraphQLHttpResponses}), and under the first a response without {@code data} has status {@code 400}.
 *
 * <p>The {@code POST} route consumes every content type, so the multipart route of the upload module, which consumes
 * {@code multipart/form-data} only, still wins for that content type. The {@code POST} routes of both controllers produce
 * {@code *}{@code /*} and answer {@code 406} themselves for an {@code Accept} that lists neither type: if the router judged
 * {@code Accept}, it would report {@code 415} from the multipart route when the upload module is on the classpath and
 * {@code 406} when it is not.</p>
 */
@Internal
@Controller("${graphql.path:/graphql}")
@RequiresGraphQL
@Replaces(GraphQLController.class)
public final class LibraryGraphQLController {

    private final GraphQLInvocation invocation;
    private final GraphQLExecutionResultHandler resultHandler;
    private final GraphQLJsonSerializer serializer;

    LibraryGraphQLController(GraphQLInvocation invocation, GraphQLExecutionResultHandler resultHandler,
                             GraphQLJsonSerializer serializer) {
        this.invocation = invocation;
        this.resultHandler = resultHandler;
        this.serializer = serializer;
    }

    /**
     * @param query         the document
     * @param operationName the name of the operation to run, or null
     * @param variables     the variables as a JSON object, or null
     * @param request       the request
     * @return the response
     */
    @Get(produces = {GraphQLHttpResponses.GRAPHQL_RESPONSE_JSON, MediaType.APPLICATION_JSON})
    public Publisher<MutableHttpResponse<String>> get(@QueryValue("query") String query,
                                                      @Nullable @QueryValue("operationName") String operationName,
                                                      @Nullable @QueryValue("variables") String variables,
                                                      HttpRequest<?> request) {
        return execute(query, operationName, variablesOf(variables), request);
    }

    /**
     * @param query         the document of a request whose parameters are in the query string, or null
     * @param operationName the name of the operation to run, or null
     * @param variables     the variables as a JSON object, or null
     * @param body          the body of the request, or null
     * @param request       the request
     * @return the response
     */
    @Post(consumes = MediaType.ALL, produces = MediaType.ALL)
    public Publisher<MutableHttpResponse<String>> post(@Nullable @QueryValue("query") String query,
                                                       @Nullable @QueryValue("operationName") String operationName,
                                                       @Nullable @QueryValue("variables") String variables,
                                                       @Nullable @Body String body, HttpRequest<?> request) {
        MediaType contentType = request.getContentType().orElse(null);
        String text = body == null ? "" : body;
        if (MediaType.APPLICATION_JSON_TYPE.equals(contentType)) {
            GraphQLRequestBody parsed = serializer.deserialize(text, GraphQLRequestBody.class);
            return execute(parsed.getQuery() == null ? "" : parsed.getQuery(), parsed.getOperationName(), parsed.getVariables(), request);
        }
        if (query != null) {
            return execute(query, operationName, variablesOf(variables), request);
        }
        if (MediaType.APPLICATION_GRAPHQL_TYPE.equals(contentType)) {
            return execute(text, null, null, request);
        }
        throw new HttpStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Could not process GraphQL request");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> variablesOf(@Nullable String json) {
        return json == null ? Collections.emptyMap() : serializer.deserialize(json, Map.class);
    }

    private Publisher<MutableHttpResponse<String>> execute(String query, @Nullable String operationName,
                                                           @Nullable Map<String, Object> variables, HttpRequest<?> request) {
        MediaType type = GraphQLHttpResponses.negotiate(request);
        if (type == null) {
            return Flux.just(HttpResponse.status(HttpStatus.NOT_ACCEPTABLE));
        }
        MutableHttpResponse<String> response = HttpResponse.status(HttpStatus.OK);
        @SuppressWarnings({"rawtypes", "unchecked"})
        Publisher<MutableHttpResponse<String>> result = Publishers.map(
                resultHandler.handleExecutionResult(invocation.invoke(new GraphQLInvocationData(query, operationName, variables),
                        (HttpRequest) request, response)),
                body -> GraphQLHttpResponses.prepare(response, type, body).body(serializer.serialize(body)));
        return result;
    }

}
