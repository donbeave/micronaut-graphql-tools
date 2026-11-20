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
/**
 * The connection hook of the {@code graphql-transport-ws} WebSocket protocol: a
 * {@link io.micronaut.graphql.tools.websocket.GraphQLConnectionInterceptor} sees the payload of {@code connection_init}
 * and accepts the connection, with attributes that resolvers read, or rejects it.
 *
 * <p><b>Audience.</b> Public API. Application code implements the interceptor and reads the request and the result types.
 * The handler that calls it is in the {@code internal.ws} package.</p>
 */
package io.micronaut.graphql.tools.websocket;
