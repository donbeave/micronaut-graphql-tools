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
package io.micronaut.graphql.tools.client.exceptions;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.graphql.tools.client.GraphQLError;
import io.micronaut.graphql.tools.client.GraphQLResponse;

import java.util.List;

/**
 * The server answered with GraphQL errors. It carries them and the response.
 *
 * @since 1.0.0
 */
public class GraphQLErrorsException extends GraphQLClientException {

    /** The code of this exception. */
    public static final String CODE = "graphql-errors";

    private static final long serialVersionUID = 1L;

    private final transient List<GraphQLError> errors;
    private final transient GraphQLResponse<?> response;

    /**
     * Creates an exception.
     *
     * @param errors   the errors, at least one
     * @param response the response, or {@code null} when there is none
     */
    public GraphQLErrorsException(List<GraphQLError> errors, @Nullable GraphQLResponse<?> response) {
        super(CODE, describe(errors));
        this.errors = List.copyOf(errors);
        this.response = response;
    }

    /**
     * Returns the errors of the response.
     *
     * @return an unmodifiable list
     */
    public List<GraphQLError> getErrors() {
        return errors;
    }

    /**
     * Returns the response, which may hold partial data.
     *
     * @return the response, or {@code null} when there is none
     */
    @Nullable
    public GraphQLResponse<?> getResponse() {
        return response;
    }

    private static String describe(List<GraphQLError> errors) {
        if (errors.isEmpty()) {
            return "The response has errors";
        }
        String first = errors.get(0).message();
        return errors.size() == 1 ? first : first + " (and " + (errors.size() - 1) + " more)";
    }
}
