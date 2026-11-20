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
import io.micronaut.http.context.ServerRequestContext
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `per-execution-context`: a contributor that numbers the executions and puts a value in the
// context, and a loader whose batch function says whether the request is in the propagated context.

@Requires(property = "tck.scenario", value = "per-execution-context")
// tag::contributor[]
@Singleton
class NumberingContributor : GraphQLContextContributor {

    private val executions = AtomicInteger()

    override fun contribute(context: GraphQLContext, request: GraphQLExecutionRequest) {
        context.put("tck.execution", executions.incrementAndGet())
        context.put("tck.value", "from-contributor")
    }
}
// end::contributor[]

@Singleton
@Requires(property = "tck.scenario", value = "per-execution-context")
class RequestProbeLoader : BatchLoader<String, Boolean> {

    override fun load(keys: List<String>): CompletionStage<List<Boolean>> {
        val request = ServerRequestContext.currentRequest<Any>().isPresent
        return CompletableFuture.completedFuture(keys.map { request })
    }
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "per-execution-context")
class ContextQueries {

    fun contributed(environment: DataFetchingEnvironment): String = environment.graphQlContext.get<String>("tck.value")!!

    fun executionNumber(environment: DataFetchingEnvironment): Int = environment.graphQlContext.get<Int>("tck.execution")!!

    fun loadersCreated(environment: DataFetchingEnvironment): List<String> = environment.dataLoaderRegistry.keys.sorted()

    fun batchSawRequest(loader: DataLoader<String, Boolean>): CompletionStage<Boolean> = loader.load("probe")
}
