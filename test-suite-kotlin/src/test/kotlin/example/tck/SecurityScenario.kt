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
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.async.publisher.Publishers
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.security.GraphQLRootFieldsHook
import io.micronaut.http.HttpRequest
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.Authentication
import io.micronaut.security.filters.AuthenticationFetcher
import io.micronaut.security.rules.SecurityRule
import io.micronaut.security.token.DefaultRolesFinder
import io.micronaut.security.token.RolesFinder
import io.micronaut.security.utils.SecurityService
import jakarta.inject.Singleton
import org.reactivestreams.Publisher
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `security`: an authentication from request headers, secured and unsecured resolvers, a child
// field that runs after an async parent, and a hook that records the root fields.

/** Authenticates a request that has `X-User`, with the comma separated roles of `X-Roles`. */
@Singleton
@Requires(property = "tck.scenario", value = "security")
class HeaderAuthenticationFetcher : AuthenticationFetcher<HttpRequest<*>> {

    override fun fetchAuthentication(request: HttpRequest<*>): Publisher<Authentication> {
        val user = request.headers.get("X-User") ?: return Publishers.empty()
        val roles = request.headers.get("X-Roles")
        return Publishers.just(Authentication.build(user, roles?.split(",") ?: emptyList()))
    }
}

// tag::roles-finder[]
@Singleton
@Replaces(DefaultRolesFinder::class)
@Requires(property = "tck.scenario", value = "security")
@Requires(property = "tck.security.finder", value = "team")
class TeamRolesFinder : RolesFinder {

    override fun resolveRoles(attributes: Map<String, Any>): List<String> = emptyList()

    override fun hasAnyRequiredRoles(requiredRoles: List<String>, grantedRoles: Collection<String>): Boolean {
        val mapped = grantedRoles.filter { it != "ROLE_ADMIN" }.map { if (it == "TEAM_OPS") "ROLE_ADMIN" else it }
        return super.hasAnyRequiredRoles(requiredRoles, mapped)
    }
}
// end::roles-finder[]

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "security")
@Requires(property = "tck.security.expression", notEquals = "true")
class SecurityGatedQueries {

    fun secGated(): String = "gated"
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "security")
@Requires(property = "tck.security.expression", value = "true")
class SecurityExpressionQueries {

    @Secured("#{1 == 1}")
    fun secGated(): String = "gated"
}

// tag::root-fields-hook[]
@Singleton
@Requires(property = "tck.scenario", value = "security")
class RecordingHook : GraphQLRootFieldsHook {

    override fun onRootFields(operation: OperationDefinition.Operation, rootFields: List<String>, context: GraphQLContext) {
        context.put("tck.roots", rootFields.joinToString(","))
    }
}
// end::root-fields-hook[]

@GraphQLType
class SecMe

// tag::secured[]
@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "security")
class SecurityQueries {

    fun secOpen(): String = "open"

    @Secured(SecurityRule.IS_AUTHENTICATED)
    fun secAuthenticated(): String = "authenticated"

    @Secured("ROLE_ADMIN")
    fun secAdmin(): String = "admin"

    @Secured(SecurityRule.DENY_ALL)
    fun secNobody(): String = "nobody"

    fun secMe(): CompletionStage<SecMe> = CompletableFuture.supplyAsync { SecMe() }

    fun secRoots(context: GraphQLContext): String = context.get<String>("tck.roots")!!
}
// end::secured[]

@Singleton
@GraphQLTypeResolver(SecMe::class)
@Requires(property = "tck.scenario", value = "security")
class SecMeFields(private val securityService: SecurityService) {

    fun principal(me: SecMe, authentication: Authentication?): CompletionStage<String?> =
        CompletableFuture.supplyAsync { authentication?.name }

    fun service(me: SecMe): String? = securityService.authentication.map { it.name }.orElse(null)
}
