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
import groovy.transform.CompileStatic
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

// The fixtures of the TCK scenario `resolver-parameters`: a contributor that puts a value in the context, a filter that
// authenticates the request, and a resolver with one parameter of each injectable kind.

@CompileStatic
@GraphQLType
class ParamBook {

    final String title
    final String author

    ParamBook(String title, String author) {
        this.title = title
        this.author = author
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'resolver-parameters')
class TenantContributor implements GraphQLContextContributor {

    @Override
    void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        context.put('tck.tenant', 'acme')
    }
}

@CompileStatic
@ServerFilter(Filter.MATCH_ALL_PATTERN)
@Requires(property = 'tck.scenario', value = 'resolver-parameters')
class AuthenticatingFilter {

    @RequestFilter
    void authenticate(MutableHttpRequest<?> request) {
        request.setUserPrincipal({ 'alice' } as Principal)
    }
}

// tag::parameters[]
@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'resolver-parameters')
class ParameterQueries {

    String paramContext(GraphQLContext context) {
        context.get('tck.tenant')
    }

    String paramContextValue(@GraphQLContextValue('tck.tenant') String tenant) {
        tenant
    }

    String paramMissingValue(@GraphQLContextValue('tck.absent') String absent) {
        absent
    }

    String paramEnvironment(DataFetchingEnvironment environment) {
        'Query.' + environment.fieldDefinition.name
    }

    ParamBook paramSelection(DataFetchingFieldSelectionSet selection) {
        new ParamBook(selection.fields*.name.join(','), 'a')
    }

    boolean paramLocale(Locale locale) {
        locale != null
    }

    String paramRequest(HttpRequest<?> request) {
        request.path
    }

    boolean paramResponse(MutableHttpResponse<?> response) {
        response != null
    }

    boolean paramOptionalResponse(Optional<MutableHttpResponse<?>> response) {
        response.isPresent()
    }

    String paramPrincipal(Principal principal) {
        principal.name
    }

    String paramMixed(Locale locale, String name, @GraphQLContextValue('tck.tenant') String tenant,
                      DataFetchingEnvironment environment, int count) {
        name + ':' + count + ':' + tenant + ':Query.' + environment.fieldDefinition.name
    }
}
// end::parameters[]
