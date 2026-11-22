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
package io.micronaut.graphql.tools.websocket;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.HttpRequest;

import java.util.Map;
import java.util.Objects;

/**
 * What a {@link GraphQLConnectionInterceptor} sees of a connection: the payload of its {@code connection_init} message and
 * the HTTP request that upgraded the connection (its headers, cookies and query parameters).
 *
 * @param payload     the payload of the {@code connection_init} message, where browser clients put their token; never null,
 *                    empty when the client sent none
 * @param httpRequest the HTTP request that upgraded the connection; never null
 */
public record GraphQLConnectionRequest(@NonNull Map<String, Object> payload, @NonNull HttpRequest<?> httpRequest) {

    /**
     * @param payload     the {@code payload} of the {@code connection_init} message, empty when the client sent none
     * @param httpRequest the upgrade request
     */
    public GraphQLConnectionRequest {
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(httpRequest, "httpRequest");
    }

}
