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
import groovy.transform.CompileStatic
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

@Requires(property = 'tck.scenario', value = 'per-execution-context')
// tag::contributor[]
@CompileStatic
@Singleton
class NumberingContributor implements GraphQLContextContributor {

    private final AtomicInteger executions = new AtomicInteger()

    @Override
    void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        context.put('tck.execution', executions.incrementAndGet())
        context.put('tck.value', 'from-contributor')
    }
}
// end::contributor[]

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'per-execution-context')
class RequestProbeLoader implements BatchLoader<String, Boolean> {

    @Override
    CompletionStage<List<Boolean>> load(List<String> keys) {
        boolean request = ServerRequestContext.currentRequest().isPresent()
        List<Boolean> result = []
        keys.each { String key -> result.add(request) }
        return CompletableFuture.completedFuture(result)
    }
}

@CompileStatic
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'per-execution-context')
class ContextQueries {

    String contributed(DataFetchingEnvironment environment) {
        return environment.graphQlContext.get('tck.value')
    }

    int executionNumber(DataFetchingEnvironment environment) {
        return (int) environment.graphQlContext.get('tck.execution')
    }

    List<String> loadersCreated(DataFetchingEnvironment environment) {
        return new ArrayList<String>(environment.dataLoaderRegistry.keys).sort()
    }

    CompletionStage<Boolean> batchSawRequest(DataLoader<String, Boolean> loader) {
        return loader.load('probe')
    }
}
