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

import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.graphql.tools.security.GraphQLSecurity;
import io.micronaut.security.authentication.Authentication;
import io.micronaut.security.token.RolesFinder;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rejects the call of a secured field that the caller may not make, with an error of the field and no call to the resolver;
 * lets every other call through to the wrapped fetcher. A call that passes reads one context entry and allocates nothing.
 */
@Internal
final class SecuredDataFetcher implements DataFetcher<Object> {

    private final DataFetcher<Object> delegate;
    private final String coordinates;
    private final boolean authenticated;
    private final Set<String> roles;
    private final List<String> requiredRoles;
    private final RolesFinder rolesFinder;

    /**
     * @param delegate      the fetcher of the field
     * @param coordinates   the coordinates of the field, for the message
     * @param authenticated whether any authenticated caller may call the field
     * @param roles         the roles of which a caller needs one; empty with {@code authenticated} for no role requirement and
     *                      empty without it for a field that nobody may call
     * @param rolesFinder   the {@code RolesFinder} that decides whether the roles of a caller satisfy the required ones, or
     *                      null for the default one, which is a plain membership test
     */
    SecuredDataFetcher(DataFetcher<Object> delegate, String coordinates, boolean authenticated, Set<String> roles,
                       RolesFinder rolesFinder) {
        this.delegate = delegate;
        this.coordinates = coordinates;
        this.authenticated = authenticated;
        this.roles = roles;
        this.requiredRoles = List.copyOf(roles);
        this.rolesFinder = rolesFinder;
    }

    @Override
    public Object get(DataFetchingEnvironment environment) throws Exception {
        Authentication authentication = environment.getGraphQlContext().get(GraphQLSecurity.AUTHENTICATION_KEY);
        if (authentication != null && allows(authentication)) {
            return delegate.get(environment);
        }
        boolean anonymous = authentication == null;
        String code = anonymous ? GraphQLSecurity.UNAUTHENTICATED_CODE : GraphQLSecurity.FORBIDDEN_CODE;
        String message = anonymous ? "Authentication is required to call " + coordinates
                : "Access to " + coordinates + " is forbidden";
        return DataFetcherResult.newResult()
                .error(GraphqlErrorBuilder.newError(environment).message(message).extensions(Map.of("code", code)).build())
                .build();
    }

    private boolean allows(Authentication authentication) {
        if (authenticated) {
            return true;
        }
        if (rolesFinder != null) {
            return !requiredRoles.isEmpty() && rolesFinder.hasAnyRequiredRoles(requiredRoles, authentication.getRoles());
        }
        for (String role : authentication.getRoles()) {
            if (roles.contains(role)) {
                return true;
            }
        }
        return false;
    }

}
