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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An entry of the {@code errors} list of a GraphQL response.
 *
 * @param message    the message of the error, empty when the response has none
 * @param locations  the places in the document that the error is about, empty when the response has none
 * @param path       the response path of the failed field: field names ({@link String}) and list indexes ({@link Integer}),
 *                   empty when the response has none
 * @param extensions the extensions of the error, empty when the response has none
 * @since 1.0.0
 */
public record GraphQLError(String message, List<Location> locations, List<Object> path, Map<String, Object> extensions) {

    /**
     * Copies the collections and replaces an absent value with an empty one.
     */
    public GraphQLError {
        message = message == null ? "" : message;
        locations = locations == null ? List.of() : List.copyOf(locations);
        path = path == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(path));
        extensions = extensions == null || extensions.isEmpty() ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(extensions));
    }

    /**
     * Creates an error that has a message only.
     *
     * @param message the message
     * @return the error
     */
    public static GraphQLError of(@Nullable String message) {
        return new GraphQLError(message, null, null, null);
    }

    /**
     * A place in a GraphQL document.
     *
     * @param line   the line, starting at 1
     * @param column the column, starting at 1
     * @since 1.0.0
     */
    public record Location(int line, int column) {
    }
}
