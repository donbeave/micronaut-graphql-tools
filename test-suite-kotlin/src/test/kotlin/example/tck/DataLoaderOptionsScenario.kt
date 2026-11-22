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

@GraphQLType
class Reader(val name: String)

@GraphQLType
class Page(val title: String)

@GraphQLType
class PageStats(val batches: Int, val keys: Int)

@Singleton
@Requires(property = "tck.scenario", value = "data-loader-options")
class PageLoader : MappedBatchLoader<String, List<Page>> {

    val batches = AtomicInteger()
    val keys = AtomicInteger()

    override fun load(readers: Set<String>): CompletionStage<Map<String, List<Page>>> {
        batches.incrementAndGet()
        keys.addAndGet(readers.size)
        return CompletableFuture.completedFuture(readers.associateWith { listOf(Page("$it-1"), Page("$it-2")) })
    }
}

@Requires(property = "tck.scenario", value = "data-loader-options")
// tag::options[]
@Singleton
class PageLoaderOptions : DataLoaderOptionsCustomizer {

    override fun getLoaderName(): String = "pageLoader"

    // at most two keys in a batch; maxBatchSize() exists in java-dataloader 3.x and 6.x
    override fun customize(options: DataLoaderOptions): DataLoaderOptions = object : DataLoaderOptions() {
        override fun maxBatchSize(): Int = 2
    }
}
// end::options[]

@Singleton
@Requires(property = "tck.scenario", value = "data-loader-options")
class ScopeContributor : GraphQLContextContributor {
    override fun contribute(context: GraphQLContext, request: GraphQLExecutionRequest) {
        context.put("scope", "alpha")
    }
}

@Singleton
@Requires(property = "tck.scenario", value = "data-loader-options")
class ScopeLoader : BatchLoaderWithContext<String, String> {
    override fun load(keys: List<String>, environment: BatchLoaderEnvironment): CompletionStage<List<String>> {
        val context = environment.getContext<GraphQLContext>()
        return CompletableFuture.completedFuture(keys.map { "scope:${context!!.get<String>("scope")}:${keys.size}" })
    }
}

@Singleton
@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "data-loader-options")
class DataLoaderOptionsQueries(private val pageLoader: PageLoader) {

    fun readers(): List<Reader> = listOf(Reader("ada"), Reader("grace"), Reader("lin"))

    fun batchContext(environment: DataFetchingEnvironment): CompletionStage<String> =
        environment.getDataLoader<String, String>("scopeLoader")!!.load("a")

    fun pageStats(): PageStats = PageStats(pageLoader.batches.get(), pageLoader.keys.get())
}

@Singleton
@GraphQLTypeResolver(Reader::class)
@Requires(property = "tck.scenario", value = "data-loader-options")
class ReaderResolver {

    fun pages(reader: Reader, loader: DataLoader<String, List<Page>>): CompletionStage<List<Page>> =
        loader.load(reader.name)
}
