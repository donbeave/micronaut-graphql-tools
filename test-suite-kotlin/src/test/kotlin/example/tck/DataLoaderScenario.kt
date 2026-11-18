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

import graphql.schema.DataFetchingEnvironment
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `data-loader`: a batch loader of volumes, per request, and counters that show how often it
// ran and with how many keys.

const val VOLUMES = "volumes"

@GraphQLType
class Author(val name: String)

@GraphQLType
class Volume(val title: String)

@GraphQLType
class LoaderStats(val batches: Int, val keys: Int)

@Singleton
@Requires(property = "tck.scenario", value = "data-loader")
class VolumeLoader : MappedBatchLoader<String, List<Volume>> {

    val batches = AtomicInteger()
    val keys = AtomicInteger()

    override fun load(authors: Set<String>): CompletionStage<Map<String, List<Volume>>> {
        batches.incrementAndGet()
        keys.addAndGet(authors.size)
        return CompletableFuture.completedFuture(authors.associateWith { listOf(Volume("$it-1"), Volume("$it-2")) })
    }
}

/** One registry for each request: a batch and a cache that live as long as the request. */
@Factory
@Requires(property = "tck.registry", value = "per-request")
class VolumeRegistry(private val volumeLoader: VolumeLoader) {

    @Primary // over the registry of the example, which a request-scoped proxy cannot replace
    @RequestScope
    fun dataLoaderRegistry(): DataLoaderRegistry =
        DataLoaderRegistry().register(VOLUMES, DataLoaderFactory.newMappedDataLoader(volumeLoader))
}

@GraphQLRootResolver
@Requires(property = "tck.scenario", value = "data-loader")
class DataLoaderQueries(private val volumeLoader: VolumeLoader) {

    fun authors(): List<Author> = listOf(Author("ada"), Author("grace"), Author("lin"))

    fun loaderStats(): LoaderStats = LoaderStats(volumeLoader.batches.get(), volumeLoader.keys.get())
}

@GraphQLTypeResolver(Author::class)
@Requires(property = "tck.scenario", value = "data-loader")
class AuthorResolver {

    fun volumes(author: Author, environment: DataFetchingEnvironment): CompletionStage<List<Volume>> =
        environment.getDataLoader<String, List<Volume>>(VOLUMES)!!.load(author.name)
}
