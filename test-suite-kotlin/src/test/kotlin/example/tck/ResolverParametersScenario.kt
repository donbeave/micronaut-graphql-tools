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
import graphql.schema.DataFetchingEnvironment
import graphql.schema.DataFetchingFieldSelectionSet
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLContextValue
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.execution.GraphQLContextContributor
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
import jakarta.inject.Singleton
import java.security.Principal
import java.util.Locale
import java.util.Optional

// The fixtures of the TCK scenario `resolver-parameters`: a contributor that puts a value in the context, a filter that
// authenticates the request, and a resolver with one parameter of each injectable kind.

@GraphQLType
class ParamBook(val title: String, val author: String)

@Singleton
@Requires(property = "tck.scenario", value = "resolver-parameters")
class TenantContributor : GraphQLContextContributor {

    override fun contribute(context: GraphQLContext, request: GraphQLExecutionRequest) {
        context.put("tck.tenant", "acme")
    }
}

@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Requires(property = "tck.scenario", value = "resolver-parameters")
class AuthenticatingFilter {

    @RequestFilter
    fun authenticate(request: MutableHttpRequest<*>) {
        request.setUserPrincipal(Principal { "alice" })
    }
}

// tag::parameters[]
@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "resolver-parameters")
class ParameterQueries {

    fun paramContext(context: GraphQLContext): String = context.get<String>("tck.tenant")!!

    fun paramContextValue(@GraphQLContextValue("tck.tenant") tenant: String): String = tenant

    fun paramMissingValue(@GraphQLContextValue("tck.absent") absent: String?): String? = absent

    fun paramEnvironment(environment: DataFetchingEnvironment): String = "Query." + environment.fieldDefinition.name

    fun paramSelection(selection: DataFetchingFieldSelectionSet): ParamBook =
        ParamBook(selection.fields.joinToString(",") { it.name }, "a")

    fun paramLocale(locale: Locale): Boolean = true

    fun paramRequest(request: HttpRequest<*>): String = request.path

    fun paramResponse(response: MutableHttpResponse<*>): Boolean = true

    fun paramOptionalResponse(response: Optional<MutableHttpResponse<*>>): Boolean = response.isPresent

    fun paramPrincipal(principal: Principal): String = principal.name

    fun paramMixed(
        locale: Locale,
        name: String,
        @GraphQLContextValue("tck.tenant") tenant: String,
        environment: DataFetchingEnvironment,
        count: Int,
    ): String = "$name:$count:$tenant:Query." + environment.fieldDefinition.name
}
// end::parameters[]
