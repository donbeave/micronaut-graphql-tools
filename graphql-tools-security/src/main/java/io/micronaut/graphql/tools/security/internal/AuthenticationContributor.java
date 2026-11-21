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
package io.micronaut.graphql.tools.security.internal;

import graphql.GraphQLContext;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.http.HttpRequest;
import io.micronaut.security.authentication.Authentication;
import jakarta.inject.Singleton;

/**
 * Puts the {@code Authentication} that Micronaut Security set on the request into the context, once for each execution. The
 * request is the one the execution captured: the HTTP request, or the upgrade request of a WebSocket connection. Nothing reads
 * a thread-local, so the entry is the same in a field that runs after an async hop.
 */
@Internal
@Singleton
@Requires(classes = Authentication.class)
@Requires(property = GraphQLSecurity.PREFIX + ".enabled", value = "true", defaultValue = "true")
final class AuthenticationContributor implements GraphQLContextContributor {

    @Override
    public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        HttpRequest<?> http = request.httpRequest();
        if (http == null) {
            return;
        }
        http.getUserPrincipal(Authentication.class).ifPresent(authentication ->
                context.put(GraphQLSecurity.AUTHENTICATION_KEY, authentication));
    }

}
