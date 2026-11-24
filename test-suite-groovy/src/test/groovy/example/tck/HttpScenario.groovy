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

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

// The fixtures of the TCK scenario `http`: a field that echoes its argument, a context contributor that puts the header
// X-Tenant of the request in the GraphQL context, and a batch loader whose values come from that tenant.

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'http')
class HttpTenantContributor implements GraphQLContextContributor {

    @Override
    void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        request.httpRequest?.headers?.get('X-Tenant', String)?.ifPresent { String tenant -> context.put('tck.tenant', tenant) }
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'http')
class TenantLoader implements BatchLoader<String, String> {

    @Override
    CompletionStage<List<String>> load(List<String> tenants) {
        Thread.sleep(2)
        return CompletableFuture.completedFuture(tenants.collect { String tenant -> 'loaded-' + tenant } as List<String>)
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'http')
class HttpQueries {

    private final Map<Object, Long> loaderIds = new IdentityHashMap<Object, Long>()
    private final AtomicLong nextLoaderId = new AtomicLong()

    String httpEcho(String value) {
        return value
    }

    String tenant(DataFetchingEnvironment environment) {
        return environment.graphQlContext.get('tck.tenant')
    }

    CompletionStage<String> tenantLoaded(DataLoader<String, String> loader, DataFetchingEnvironment environment) {
        return loader.load((String) environment.graphQlContext.get('tck.tenant'))
    }

    String loaderId(DataLoader<String, String> loader) {
        synchronized (loaderIds) {
            Long id = loaderIds.get(loader)
            if (id == null) {
                id = nextLoaderId.incrementAndGet()
                loaderIds.put(loader, id)
            }
            return id.toString()
        }
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'http')
class HttpMutations {

    private final AtomicInteger count = new AtomicInteger()

    String httpMutate(String value) {
        count.incrementAndGet()
        return value
    }

    int httpMutationCount() {
        return count.get()
    }
}
