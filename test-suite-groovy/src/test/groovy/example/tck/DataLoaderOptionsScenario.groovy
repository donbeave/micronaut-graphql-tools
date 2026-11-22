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
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.execution.DataLoaderOptionsCustomizer
import io.micronaut.graphql.tools.execution.GraphQLContextContributor
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest
import jakarta.inject.Singleton
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoader

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `data-loader-options`: a customizer that limits the batch size of one loader to two
// keys, and counters that show how often the loader ran and with how many keys. A second loader reads the context of its
// execution through `BatchLoaderEnvironment#getContext()`.

@CompileStatic
@GraphQLType
class Reader {
    final String name

    Reader(String name) {
        this.name = name
    }
}

@CompileStatic
@GraphQLType
class Page {
    final String title

    Page(String title) {
        this.title = title
    }
}

@CompileStatic
@GraphQLType
class PageStats {
    final int batches
    final int keys

    PageStats(int batches, int keys) {
        this.batches = batches
        this.keys = keys
    }
}

@Requires(property = 'tck.scenario', value = 'data-loader-options')
@CompileStatic
@Singleton
class PageLoader implements MappedBatchLoader<String, List<Page>> {

    final AtomicInteger batches = new AtomicInteger()
    final AtomicInteger keys = new AtomicInteger()

    @Override
    CompletionStage<Map<String, List<Page>>> load(Set<String> readers) {
        batches.incrementAndGet()
        keys.addAndGet(readers.size())
        Map<String, List<Page>> pages = [:]
        readers.each { String reader -> pages.put(reader, [new Page("$reader-1".toString()), new Page("$reader-2".toString())] as List<Page>) }
        return CompletableFuture.completedFuture(pages)
    }
}

@Requires(property = 'tck.scenario', value = 'data-loader-options')
// tag::options[]
@CompileStatic
@Singleton
class PageLoaderOptions implements DataLoaderOptionsCustomizer {

    @Override
    String getLoaderName() {
        return 'pageLoader'
    }

    @Override
    DataLoaderOptions customize(DataLoaderOptions options) {
        // at most two keys in a batch; maxBatchSize() exists in java-dataloader 3.x and 6.x
        return new DataLoaderOptions() {
            @Override
            int maxBatchSize() {
                return 2
            }
        }
    }
}
// end::options[]

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'data-loader-options')
class ScopeContributor implements GraphQLContextContributor {

    @Override
    void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
        context.put('scope', 'alpha')
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'data-loader-options')
class ScopeLoader implements BatchLoaderWithContext<String, String> {

    @Override
    CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
        GraphQLContext context = environment.getContext() as GraphQLContext
        return CompletableFuture.completedFuture(keys.collect { String key -> "scope:${context.get('scope')}:${keys.size()}" as String })
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'data-loader-options')
class DataLoaderOptionsQueries {

    private final PageLoader pageLoader

    DataLoaderOptionsQueries(PageLoader pageLoader) {
        this.pageLoader = pageLoader
    }

    List<Reader> readers() {
        return [new Reader('ada'), new Reader('grace'), new Reader('lin')] as List<Reader>
    }

    CompletionStage<String> batchContext(DataFetchingEnvironment environment) {
        DataLoader<String, String> loader = environment.getDataLoader('scopeLoader')
        return loader.load('a')
    }

    PageStats pageStats() {
        return new PageStats(pageLoader.batches.get(), pageLoader.keys.get())
    }
}

@CompileStatic
@Singleton
@GraphQLTypeResolver(Reader)
@Requires(property = 'tck.scenario', value = 'data-loader-options')
class ReaderResolver {

    CompletionStage<List<Page>> pages(Reader reader, DataLoader<String, List<Page>> loader) {
        return loader.load(reader.name)
    }
}
