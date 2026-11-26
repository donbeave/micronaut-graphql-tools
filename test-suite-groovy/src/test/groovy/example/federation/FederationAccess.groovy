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
package example.federation

import graphql.GraphQLContext
import groovy.transform.CompileStatic
import io.micronaut.graphql.tools.security.GraphQLSecurity
import io.micronaut.security.authentication.Authentication

import java.util.function.Predicate

// tag::access[]
@CompileStatic
final class FederationAccess {

    private FederationAccess() {
    }

    /** Allows a caller that is authenticated and has the role; an anonymous caller has no entry in the context, so it is refused. */
    static Predicate<GraphQLContext> role(String role) {
        return { GraphQLContext context ->
            Authentication authentication = context.get(GraphQLSecurity.AUTHENTICATION_KEY)
            authentication != null && authentication.roles.contains(role)
        } as Predicate<GraphQLContext>
    }

}
// end::access[]
