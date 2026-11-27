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

import io.micronaut.http.MediaType;

/**
 * The media types of the GraphQL over HTTP exchange.
 *
 * @since 1.0.0
 */
public final class GraphQLMediaTypes {

    /** The media type of a GraphQL response: {@code application/graphql-response+json}. */
    public static final String GRAPHQL_RESPONSE_JSON = "application/graphql-response+json";

    /** {@link #GRAPHQL_RESPONSE_JSON} as a {@link MediaType}. */
    public static final MediaType GRAPHQL_RESPONSE_JSON_TYPE = MediaType.of(GRAPHQL_RESPONSE_JSON);

    /** The value of the {@code Accept} header of a request: the GraphQL response type, then plain JSON. */
    public static final String ACCEPT = GRAPHQL_RESPONSE_JSON + ", " + MediaType.APPLICATION_JSON + ";q=0.9";

    private GraphQLMediaTypes() {
    }
}
