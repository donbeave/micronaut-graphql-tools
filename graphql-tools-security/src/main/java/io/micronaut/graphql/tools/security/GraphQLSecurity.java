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
package io.micronaut.graphql.tools.security;

/**
 * The names that the security module uses.
 */
public final class GraphQLSecurity {

    /** The prefix of the configuration keys of the module. */
    public static final String PREFIX = "micronaut.graphql.tools.security";

    /**
     * The key in the {@code GraphQLContext} of the {@code io.micronaut.security.authentication.Authentication} of the
     * execution. The entry is absent when the request is not authenticated.
     */
    public static final String AUTHENTICATION_KEY = "micronaut.graphql.tools.security.authentication";

    /**
     * The {@code code} extension of the error of a secured field that an anonymous request called.
     */
    public static final String UNAUTHENTICATED_CODE = "UNAUTHENTICATED";

    /**
     * The {@code code} extension of the error of a secured field that an authenticated request called without the required
     * role, or that no request may call.
     */
    public static final String FORBIDDEN_CODE = "FORBIDDEN";

    private GraphQLSecurity() {
    }

}
