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
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

// The fixtures of the TCK scenario `contributor-loader`: a contributor registers a loader in the registry of the execution
// input, and a resolver reads it from the data fetching environment.

@Requires(property = 'tck.scenario', value = 'contributor-loader')
// tag::contributor-loader[]
@CompileStatic
@Singleton
class LoaderRegisteringContributor implements GraphQLContextContributor {

    @Override
    void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        BatchLoader<String, String> batch = { List<String> keys ->
            CompletableFuture.completedFuture(keys.collect { String key -> "contributed:${key}:${keys.size()}" as String })
        } as BatchLoader<String, String>
        request.executionInput.dataLoaderRegistry.register('contributed', DataLoaderFactory.newDataLoader(batch))
    }
}
// end::contributor-loader[]

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'contributor-loader')
class ContributorLoaderQueries {

    CompletionStage<String> contributorLoaded(DataFetchingEnvironment environment) {
        DataLoader<String, String> loader = environment.getDataLoader('contributed')
        return loader.load('a')
    }
}
