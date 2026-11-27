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
package io.micronaut.graphql.tools.client;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException;
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException;
import io.micronaut.http.HttpResponse;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A GraphQL response.
 *
 * <p>{@code hasData} tells a response with no {@code data} entry (a request that failed before execution) from one with
 * {@code "data": null}: both have a {@code null} {@link #data()}.</p>
 *
 * @param data         the decoded {@code data}, or {@code null} when it is absent or {@code null}
 * @param hasData      whether the response has a {@code data} entry
 * @param errors       the {@code errors} of the response, empty when it has none
 * @param extensions   the {@code extensions} of the response, empty when it has none
 * @param httpResponse the HTTP response that carried it, or {@code null} when it was not decoded from one
 * @param <D>          the type of the {@code data}
 * @since 1.0.0
 */
public record GraphQLResponse<D>(
        @Nullable D data,
        boolean hasData,
        List<GraphQLError> errors,
        Map<String, Object> extensions,
        @Nullable HttpResponse<?> httpResponse) {

    /**
     * Copies the collections and replaces an absent value with an empty one.
     */
    public GraphQLResponse {
        errors = errors == null || errors.isEmpty() ? List.of() : List.copyOf(errors);
        extensions = extensions == null || extensions.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }

    /**
     * Returns the response with an HTTP response attached.
     *
     * @param httpResponse the HTTP response
     * @return a copy of this response
     */
    public GraphQLResponse<D> withHttpResponse(@Nullable HttpResponse<?> httpResponse) {
        return new GraphQLResponse<>(data, hasData, errors, extensions, httpResponse);
    }

    /**
     * Returns the {@code data}, or throws when the response has errors or no {@code data} entry. The result is {@code null}
     * only when the server sent {@code "data": null} without errors, or a {@code null} root field that was unwrapped.
     *
     * @return the {@code data}
     * @throws GraphQLErrorsException when the response has errors, even when it has partial data
     * @throws GraphQLClientException when the response has no errors and no {@code data} entry
     */
    @Nullable
    public D dataOrThrow() {
        if (!errors.isEmpty()) {
            throw new GraphQLErrorsException(errors, this);
        }
        if (!hasData) {
            throw new GraphQLClientException(GraphQLClientException.NO_DATA, "The response has neither data nor errors");
        }
        return data;
    }
}
