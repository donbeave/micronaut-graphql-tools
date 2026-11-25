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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * Decides whether a {@code graphql-transport-ws} connection is accepted, when its {@code connection_init} message arrives.
 * Browser clients cannot set headers on a WebSocket, so they send the token in the payload of that message.
 *
 * <p>Every bean of this type is called once for each connection, in the order of
 * {@link io.micronaut.core.annotation.Order @Order} on the bean class or of {@link io.micronaut.core.order.Ordered#getOrder()},
 * lowest first, until one rejects. The attributes of the accepting interceptors are merged (a later one overrides an earlier
 * one) and are available to every operation of the connection: a {@code GraphQLContextContributor}
 * reads them as {@code getConnectionAttributes()} and puts what resolvers need in the GraphQL context.</p>
 *
 * <p>The call is on the execution executor ({@code micronaut.graphql.tools.execution.executor}), so an interceptor may
 * block, for example to validate a token. An exception that it throws rejects the connection with the close code
 * {@code 4403} and a generic reason, and is logged at WARN. Without a bean of this type every connection is accepted.</p>
 */
@Experimental
@FunctionalInterface
public interface GraphQLConnectionInterceptor {

    /**
     * @param request the payload of {@code connection_init} and the upgrade request
     * @return the decision
     */
    @NonNull
    GraphQLConnectionResult intercept(@NonNull GraphQLConnectionRequest request);

}
