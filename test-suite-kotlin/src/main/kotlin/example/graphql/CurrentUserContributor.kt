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
package example.graphql

import example.data.AuthenticationService
import graphql.GraphQLContext
import io.micronaut.graphql.tools.execution.GraphQLContextContributor
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest
import jakarta.inject.Singleton

/**
 * Puts the user of each request in the GraphQL context of its execution, from the header [HEADER], which stands in for a
 * real credential such as a token. The state belongs to the execution, so concurrent requests never see each other's user,
 * and the resolvers read it from the `DataFetchingEnvironment`.
 */
@Singleton
class CurrentUserContributor(private val authenticationService: AuthenticationService) : GraphQLContextContributor {

    override fun contribute(context: GraphQLContext, request: GraphQLExecutionRequest) {
        val username = request.httpRequest()?.headers?.get(HEADER, String::class.java)?.orElse(null) ?: return
        authenticationService.find(username)?.let { context.put(AuthenticationService.CURRENT_USER, it) }
    }

    companion object {
        const val HEADER = "X-User"
    }
}
