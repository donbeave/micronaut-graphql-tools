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
import io.micronaut.graphql.tools.annotation.GraphQLType;
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver;
import io.micronaut.graphql.tools.execution.DataLoaderOptionsCustomizer;
import io.micronaut.graphql.tools.execution.GraphQLContextContributor;
import io.micronaut.graphql.tools.execution.GraphQLExecutionRequest;
import jakarta.inject.Singleton;
import org.dataloader.BatchLoaderEnvironment;
import org.dataloader.BatchLoaderWithContext;
import org.dataloader.DataLoader;
import org.dataloader.DataLoaderOptions;
import org.dataloader.MappedBatchLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * The fixtures of the TCK scenario {@code data-loader-options}: a customizer that limits the batch size of one loader to
 * two keys, and counters that show how often the loader ran and with how many keys. A second loader reads the context of its
 * execution through {@code BatchLoaderEnvironment#getContext()}.
 */
public final class DataLoaderOptionsScenario {

    private DataLoaderOptionsScenario() {
    }

    @GraphQLType
    public static class Reader {
        private final String name;

        public Reader(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @GraphQLType
    public static class Page {
        private final String title;

        public Page(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }
    }

    @GraphQLType
    public static class PageStats {
        private final int batches;
        private final int keys;

        public PageStats(int batches, int keys) {
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

    @Singleton
    @Requires(property = "tck.scenario", value = "data-loader-options")
    public static class PageLoader implements MappedBatchLoader<String, List<Page>> {

        final AtomicInteger batches = new AtomicInteger();
        final AtomicInteger keys = new AtomicInteger();

        @Override
        public CompletionStage<Map<String, List<Page>>> load(Set<String> readers) {
            batches.incrementAndGet();
            keys.addAndGet(readers.size());
            return CompletableFuture.completedFuture(readers.stream().collect(
                    Collectors.toMap(reader -> reader, reader -> List.of(new Page(reader + "-1"), new Page(reader + "-2")))));
        }
    }

    @Requires(property = "tck.scenario", value = "data-loader-options")
    // tag::options[]
    @Singleton
    public static class PageLoaderOptions implements DataLoaderOptionsCustomizer {

        @Override
        public String getLoaderName() {
            return "pageLoader";
        }

        @Override
        public DataLoaderOptions customize(DataLoaderOptions options) {
            // at most two keys in a batch; maxBatchSize() exists in java-dataloader 3.x and 6.x
            return new DataLoaderOptions() {
                @Override
                public int maxBatchSize() {
                    return 2;
                }
            };
        }
    }
    // end::options[]

    @Singleton
    @Requires(property = "tck.scenario", value = "data-loader-options")
    public static class ScopeContributor implements GraphQLContextContributor {

        @Override
        public void contribute(GraphQLContext context, GraphQLExecutionRequest request) {
            context.put("scope", "alpha");
        }
    }

    @Singleton
    @Requires(property = "tck.scenario", value = "data-loader-options")
    public static class ScopeLoader implements BatchLoaderWithContext<String, String> {

        @Override
        public CompletionStage<List<String>> load(List<String> keys, BatchLoaderEnvironment environment) {
            GraphQLContext context = environment.getContext();
            return CompletableFuture.completedFuture(keys.stream()
                    .map(key -> "scope:" + context.get("scope") + ":" + keys.size()).toList());
        }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = "tck.scenario", value = "data-loader-options")
    public static class Queries {

        private final PageLoader pageLoader;

        public Queries(PageLoader pageLoader) {
            this.pageLoader = pageLoader;
        }

        public List<Reader> readers() {
            return List.of(new Reader("ada"), new Reader("grace"), new Reader("lin"));
        }

        public CompletionStage<String> batchContext(DataFetchingEnvironment environment) {
            return environment.<String, String>getDataLoader("scopeLoader").load("a");
        }

        public PageStats pageStats() {
            return new PageStats(pageLoader.batches.get(), pageLoader.keys.get());
        }
    }

    @Singleton
    @GraphQLTypeResolver(Reader.class)
    @Requires(property = "tck.scenario", value = "data-loader-options")
    public static class ReaderResolver {

        public CompletionStage<List<Page>> pages(Reader reader, DataLoader<String, List<Page>> loader) {
            return loader.load(reader.getName());
        }
    }
}
