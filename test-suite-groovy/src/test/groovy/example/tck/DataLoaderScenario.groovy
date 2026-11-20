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
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.runtime.http.scope.RequestScope
import jakarta.inject.Singleton
import org.dataloader.DataLoader
import org.dataloader.DataLoaderFactory
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `data-loader`: a batch loader of volumes, per request, and counters that show how often it
// ran and with how many keys.

@CompileStatic
class DataLoaderNames {
    static final String VOLUMES = 'volumes'
}

@CompileStatic
@GraphQLType
class Author {
    final String name

    Author(String name) {
        this.name = name
    }
}

@CompileStatic
@GraphQLType
class Volume {
    final String title

    Volume(String title) {
        this.title = title
    }
}

@CompileStatic
@GraphQLType
class LoaderStats {
    final int batches
    final int keys

    LoaderStats(int batches, int keys) {
        this.batches = batches
        this.keys = keys
    }
}

@CompileStatic
@Singleton
@Requires(property = 'tck.scenario', value = 'data-loader')
class VolumeLoader implements MappedBatchLoader<String, List<Volume>> {

    final AtomicInteger batches = new AtomicInteger()
    final AtomicInteger keys = new AtomicInteger()

    @Override
    CompletionStage<Map<String, List<Volume>>> load(Set<String> authors) {
        batches.incrementAndGet()
        keys.addAndGet(authors.size())
        Map<String, List<Volume>> volumes = [:]
        authors.each { String author -> volumes.put(author, [new Volume("$author-1".toString()), new Volume("$author-2".toString())] as List<Volume>) }
        return CompletableFuture.completedFuture(volumes)
    }
}

/** One registry for each request: a batch and a cache that live as long as the request. */
@CompileStatic
@Factory
@Requires(property = 'tck.registry', value = 'per-request')
class VolumeRegistry {

    private final VolumeLoader volumeLoader

    VolumeRegistry(VolumeLoader volumeLoader) {
        this.volumeLoader = volumeLoader
    }

    @Primary // over the registry of the example, which a request-scoped proxy cannot replace
    @RequestScope
    DataLoaderRegistry dataLoaderRegistry() {
        return new DataLoaderRegistry().register(DataLoaderNames.VOLUMES, DataLoaderFactory.newMappedDataLoader(volumeLoader))
    }
}

@CompileStatic
@Singleton
@GraphQLRootResolver
@Requires(property = 'tck.scenario', value = 'data-loader')
class DataLoaderQueries {

    private final VolumeLoader volumeLoader

    DataLoaderQueries(VolumeLoader volumeLoader) {
        this.volumeLoader = volumeLoader
    }

    List<Author> authors() {
        return [new Author('ada'), new Author('grace'), new Author('lin')] as List<Author>
    }

    LoaderStats loaderStats() {
        return new LoaderStats(volumeLoader.batches.get(), volumeLoader.keys.get())
    }
}

@CompileStatic
@Singleton
@GraphQLTypeResolver(Author)
@Requires(property = 'tck.scenario', value = 'data-loader')
class AuthorResolver {

    CompletionStage<List<Volume>> volumes(Author author, DataFetchingEnvironment environment) {
        DataLoader<String, List<Volume>> loader = environment.getDataLoader(DataLoaderNames.VOLUMES)
        return loader.load(author.name)
    }
}
