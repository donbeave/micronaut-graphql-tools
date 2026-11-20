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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import jakarta.inject.Singleton
import org.dataloader.DataLoader
import org.dataloader.MappedBatchLoader

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

// The fixtures of the TCK scenario `data-loader`: a batch loader of volumes, and counters that show how often it
// ran and with how many keys.

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

@Requires(property = 'tck.scenario', value = 'data-loader')
// tag::loader[]
@CompileStatic
@Singleton
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
// end::loader[]

@CompileStatic
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

@Requires(property = 'tck.scenario', value = 'data-loader')
// tag::typed-parameter[]
@CompileStatic
@GraphQLTypeResolver(Author)
class AuthorResolver {

    CompletionStage<List<Volume>> volumes(Author author, DataLoader<String, List<Volume>> loader) {
        return loader.load(author.name)
    }
}
// end::typed-parameter[]
