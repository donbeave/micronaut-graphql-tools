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
package example.tck;

import graphql.GraphQLContext;
import graphql.schema.DataFetchingEnvironment;
import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import jakarta.inject.Singleton;
import org.dataloader.DataLoaderFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code contributor-loader}: a contributor registers a loader in the registry of the
 * execution input, and a resolver reads it from the data fetching environment.
 */
public final class ContributorLoaderScenario {

    private ContributorLoaderScenario() {
    }

    @Requires(property = "tck.scenario", value = "contributor-loader")
    // tag::contributor-loader[]
    @Singleton
    public static class LoaderRegisteringContributor implements GraphQLContextContributor {

        @Override
        public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            request.executionInput().getDataLoaderRegistry().register("contributed",
                    DataLoaderFactory.<String, String>newDataLoader(keys -> CompletableFuture.completedFuture(
                            keys.stream().map(key -> "contributed:" + key + ":" + keys.size()).toList())));
        }
    }
    // end::contributor-loader[]

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "contributor-loader")
    public static class ContributorLoaderQueries {

        public CompletionStage<String> contributorLoaded(DataFetchingEnvironment environment) {
            return environment.<String, String>getDataLoader("contributed").load("a");
        }
    }

}
