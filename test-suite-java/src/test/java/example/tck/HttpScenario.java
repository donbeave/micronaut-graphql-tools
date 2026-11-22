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
import org.dataloader.BatchLoader;
import org.dataloader.DataLoader;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * The fixtures of the TCK scenario {@code http}: a field that echoes its argument, a context contributor that puts the
 * header {@code X-Tenant} of the request in the GraphQL context, and a batch loader whose values come from that tenant.
 */
public final class HttpScenario {

    static final String SCENARIO = "http";
    static final String TENANT = "tck.tenant";

    private HttpScenario() {
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TenantContributor implements GraphQLContextContributor {

        @Override
        public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            if (request.getHttpRequest() != null) {
                request.getHttpRequest().getHeaders().get("X-Tenant", String.class).ifPresent(tenant -> context.put(TENANT, tenant));
            }
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class TenantLoader implements BatchLoader<String, String> {

        @Override
        public CompletionStage<List<String>> load(List<String> tenants) {
            sleepBriefly();
            return CompletableFuture.completedFuture(tenants.stream().map(tenant -> "loaded-" + tenant).toList());
        }

        private static void sleepBriefly() {
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = SCENARIO)
    public static class Queries {

        public String httpEcho(String value) {
            return value;
        }

        public String tenant(DataFetchingEnvironment environment) {
            return environment.getGraphQlContext().get(TENANT);
        }

        public CompletionStage<String> tenantLoaded(DataLoader<String, String> loader, DataFetchingEnvironment environment) {
            return loader.load(environment.getGraphQlContext().get(TENANT));
        }

        public String loaderId(DataLoader<String, String> loader) {
            return Integer.toHexString(System.identityHashCode(loader));
        }
    }
}
