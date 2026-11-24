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

import io.micronaut.configuration.graphql.GraphQLJsonSerializer;
import io.micronaut.configuration.graphql.GraphQLResponseBody;
import io.micronaut.configuration.graphql.RequiresGraphQL;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.internal.protection.MutationOverGetException;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Answers a {@code GET} request that selected a mutation with {@code 405} and {@code Allow: POST}, as the GraphQL over HTTP
 * specification recommends. The body is a GraphQL response with one error and no {@code data}, in the media type that
 * {@code Accept} negotiates, so a client that reads either type finds the reason.
 */
@Internal
@Singleton
@Produces
@RequiresGraphQL
public final class MutationOverGetHandler implements ExceptionHandler<MutationOverGetException, HttpResponse<?>> {

    private final GraphQLJsonSerializer serializer;

    MutationOverGetHandler(GraphQLJsonSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public HttpResponse<?> handle(HttpRequest request, MutationOverGetException exception) {
        MediaType type = GraphQLHttpResponses.negotiate(request);
        Map<String, Object> error = Map.of("message", exception.getMessage(),
                "extensions", Collections.singletonMap("code", "METHOD_NOT_ALLOWED"));
        Map<String, Object> specification = Collections.singletonMap("errors", List.of(error));
        MutableHttpResponse<String> response = HttpResponse.<String>status(HttpStatus.METHOD_NOT_ALLOWED)
                .header("Allow", "POST")
                .body(serializer.serialize(new GraphQLResponseBody(specification)));
        return GraphQLHttpResponses.prepare(response, type == null ? MediaType.APPLICATION_JSON_TYPE : type, true);
    }
}
