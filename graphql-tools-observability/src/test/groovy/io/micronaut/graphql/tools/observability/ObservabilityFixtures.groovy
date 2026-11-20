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
package io.micronaut.graphql.tools.observability

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.dataloader.DataLoader
import org.dataloader.MappedBatchLoader

import java.util.concurrent.CompletionStage

/** The resolvers and the batch loader of the specs: one root resolver, a model with a property, and one loader. */
class ObservabilityFixtures {

    static final String SPECS = 'Observability.*'

    @Requires(property = 'spec.name', pattern = SPECS)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        String broken() {
            throw new IllegalStateException('broken on purpose')
        }

        Book book() {
            return new Book(title: 'T')
        }

        CompletionStage<String> loaded(DataLoader<String, String> loader) {
            return loader.load('a')
        }
    }

    @Requires(property = 'spec.name', pattern = SPECS)
    @Introspected
    static class Book {
        String title
    }

    @Requires(property = 'spec.name', pattern = SPECS)
    @Singleton
    static class Titles implements MappedBatchLoader<String, String> {
        @Override
        CompletionStage<Map<String, String>> load(Set<String> keys) {
            return java.util.concurrent.CompletableFuture.completedFuture(keys.collectEntries { [(it): 'title-' + it] })
        }
    }

}
