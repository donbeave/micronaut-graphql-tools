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

import io.micronaut.context.annotation.Requires;
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver;
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import jakarta.inject.Singleton;
import org.dataloader.DataLoader;
import org.dataloader.MappedBatchLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The fixtures of the TCK scenario {@code data-loader}: a batch loader of volumes, per request, and counters that show how
 * often it ran and with how many keys.
 */
public final class DataLoaderScenario {

    private DataLoaderScenario() {
    }

    @GraphQLType
    public static class Author {
        private final String name;

        public Author(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @GraphQLType
    public static class Volume {
        private final String title;

        public Volume(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    @GraphQLType
    public static class LoaderStats {
        private final int batches;
        private final int keys;

        public LoaderStats(int batches, int keys) {
            this.batches = batches;
            this.keys = keys;
        }

        public int getBatches() {
            return batches;
        }

        public int getKeys() {
            return keys;
        }
    }

    @Requires(property = "tck.scenario", value = "data-loader")
    // tag::loader[]
    @Singleton
    public static class VolumeLoader implements MappedBatchLoader<String, List<Volume>> {

        final AtomicInteger batches = new AtomicInteger();
        final AtomicInteger keys = new AtomicInteger();

        @Override
        public CompletionStage<Map<String, List<Volume>>> load(Set<String> authors) {
            batches.incrementAndGet();
            keys.addAndGet(authors.size());
            return CompletableFuture.completedFuture(authors.stream().collect(
                    Collectors.toMap(author -> author, author -> List.of(new Volume(author + "-1"), new Volume(author + "-2")))));
        }
    }
    // end::loader[]

    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "data-loader")
    public static class Queries {

        private final VolumeLoader volumeLoader;

        public Queries(VolumeLoader volumeLoader) {
            this.volumeLoader = volumeLoader;
        }

        public List<Author> authors() {
            return List.of(new Author("ada"), new Author("grace"), new Author("lin"));
        }

        public LoaderStats loaderStats() {
            return new LoaderStats(volumeLoader.batches.get(), volumeLoader.keys.get());
        }
    }

    @Requires(property = "tck.scenario", value = "data-loader")
    // tag::typed-parameter[]
    @GraphQLTypeResolver(Author.class)
    public static class AuthorResolver {

        public CompletionStage<List<Volume>> volumes(Author author, DataLoader<String, List<Volume>> loader) {
            return loader.load(author.getName());
        }
    }
    // end::typed-parameter[]

}
