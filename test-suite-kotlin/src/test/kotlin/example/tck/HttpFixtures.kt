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
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.execution.GraphQLContextContributor
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `http`: a field that echoes its argument, a context contributor that puts the header
// X-Tenant of the request in the GraphQL context, and a batch loader whose values come from that tenant.

private const val TENANT = "tck.tenant"

@Singleton
@Requires(property = "tck.scenario", value = "http")
class HttpTenantContributor : GraphQLContextContributor {

    override fun contribute(context: GraphQLContext, request: GraphQLExecutionRequest) {
        request.httpRequest?.headers?.get("X-Tenant", String::class.java)?.ifPresent { tenant -> context.put(TENANT, tenant) }
    }
}

@Singleton
@Requires(property = "tck.scenario", value = "http")
class TenantLoader : BatchLoader<String, String> {

    override fun load(tenants: List<String>): CompletionStage<List<String>> {
        Thread.sleep(2)
        return CompletableFuture.completedFuture(tenants.map { tenant -> "loaded-$tenant" })
    }
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "http")
class HttpQueries {

    fun httpEcho(value: String): String = value

    fun tenant(environment: DataFetchingEnvironment): String? = environment.graphQlContext.get<String>(TENANT)

    fun tenantLoaded(loader: DataLoader<String, String>, environment: DataFetchingEnvironment): CompletionStage<String> =
        loader.load(requireNotNull(environment.graphQlContext.get<String>(TENANT)))

    fun loaderId(loader: DataLoader<String, String>): String = Integer.toHexString(System.identityHashCode(loader))
}
