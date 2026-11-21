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
package example.tck

import graphql.GraphQLContext
import graphql.language.OperationDefinition
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.security.RootFieldsHook
import io.micronaut.http.HttpRequest
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import org.reactivestreams.Publisher

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `security`: an authentication from request headers, secured and unsecured resolvers, a child
// field that runs after an async parent, and a hook that records the root fields.

/** Authenticates a request that has `X-User`, with the comma separated roles of `X-Roles`. */
@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'security')
class HeaderAuthenticationFetcher implements AuthenticationFetcher<HttpRequest<?>> {

    @Override
    Publisher<Authentication> fetchAuthentication(HttpRequest<?> request) {
        String user = request.headers.get('X-User')
        if (user == null) {
            return Publishers.<Authentication> empty()
        }
        String roles = request.headers.get('X-Roles')
        List<String> granted = roles == null ? new ArrayList<String>() : roles.split(',').toList()
        Publishers.just(Authentication.build(user, granted))
    }
}

// tag::root-fields-hook[]
@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'security')
class RecordingHook implements RootFieldsHook {

    @Override
    void onRootFields(OperationDefinition.Operation operation, List<String> rootFields, GraphQLContext context) {
        context.put('tck.roots', rootFields.join(','))
    }
}
// end::root-fields-hook[]

@CompileStatic
@GraphQLType
class SecMe {
}

// tag::secured[]
@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'security')
class SecurityQueries {

    String secOpen() {
        'open'
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    String secAuthenticated() {
        'authenticated'
    }

    @Secured('ROLE_ADMIN')
    String secAdmin() {
        'admin'
    }

    @Secured(SecurityRule.DENY_ALL)
    String secNobody() {
        'nobody'
    }

    CompletionStage<SecMe> secMe() {
        CompletableFuture.supplyAsync({ new SecMe() })
    }

    String secRoots(GraphQLContext context) {
        context.get('tck.roots')
    }
}
// end::secured[]

@CompileStatic
@Singleton
@GraphQLTypeResolver(SecMe)
@Requires(property = 'tck.scenario', value = 'security')
class SecMeFields {

    private final SecurityService securityService

    SecMeFields(SecurityService securityService) {
        this.securityService = securityService
    }

    CompletionStage<String> principal(SecMe me, Authentication authentication) {
        CompletableFuture.supplyAsync({ authentication == null ? null : authentication.name })
    }

    String service(SecMe me) {
        securityService.authentication.map({ Authentication a -> a.name }).orElse(null)
    }
}
