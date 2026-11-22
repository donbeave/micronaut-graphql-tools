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
package io.micronaut.graphql.tools.execution

import graphql.ExecutionResult
import graphql.GraphQLContext
import graphql.schema.DataFetchingEnvironment
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.execution.ExecutionState
import jakarta.inject.Singleton
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.BatchLoader
import org.dataloader.DataLoader
import org.dataloader.DataLoaderOptions
import org.dataloader.MappedBatchLoaderWithContext
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/** The per-loader options customizer, caching off in a subscription and the context of the batch loader environment. */
class DataLoaderOptionsSpec extends AbstractTest {

    static final String SPEC = 'DataLoaderOptionsSpec'

    static final List<List<Long>> SHARED_BATCHES = Collections.synchronizedList([])
    static final List<Integer> CHUNKED_BATCHES = Collections.synchronizedList([])
    static final List<Integer> OTHER_BATCHES = Collections.synchronizedList([])
    static final List<Integer> SEEN_BY_SECOND = Collections.synchronizedList([])
    static final List<Integer> RETURNED_BATCHES = Collections.synchronizedList([])
    static final List<Integer> MUTATED_BATCHES = Collections.synchronizedList([])
    static final List<Object> CONTEXTS = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
  subscription: Subscription
}

type Query {
  authors(count: Int!): [Author!]!
}

type Subscription {
  authorStream(count: Int!): [Author!]!
}

type Author {
  id: ID!
  shared: String
  chunked: String
  other: String
  contextual: String
  mappedContextual: String
  returned: String
  mutated: String
}
"""

    void setup() {
        SHARED_BATCHES.clear()
        CHUNKED_BATCHES.clear()
        OTHER_BATCHES.clear()
        SEEN_BY_SECOND.clear()
        RETURNED_BATCHES.clear()
        MUTATED_BATCHES.clear()
        CONTEXTS.clear()
    }

    private ExecutionResult run(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
    }

    void "a customizer changes the options of its loader and of no other, and a later one sees what the earlier returned"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 5) { chunked other } }')

        then:
        result.errors.isEmpty()
        result.data.authors*.chunked == ['c0', 'c1', 'c2', 'c3', 'c4']
        CHUNKED_BATCHES == [3, 2]
        OTHER_BATCHES == [5]
        SEEN_BY_SECOND == [2]
    }

    void "a query loader keeps caching on, so the same key is loaded once"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 3) { shared } }')

        then:
        result.errors.isEmpty()
        SHARED_BATCHES == [[1L]]
    }

    void "a loader of a subscription operation has caching off, so a key that is loaded three times reaches the batch function three times"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { authorStream(count: 3) { shared } }')
        def events = Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        events.size() == 1
        events[0].errors.isEmpty()
        events[0].data.authorStream*.shared == ['s1', 's1', 's1']
        SHARED_BATCHES*.size().sum() == 3
        SHARED_BATCHES.flatten().toSet() == [1L] as Set
    }

    void "a customizer also runs for a loader of a subscription operation, and sees the options of the earlier one"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { authorStream(count: 5) { chunked } }')
        Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        CHUNKED_BATCHES.sum() == 5
        SEEN_BY_SECOND == [2]
    }

    void "a BatchLoaderWithContext and a MappedBatchLoaderWithContext receive the GraphQLContext of the execution"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 2) { contextual mappedContextual } }')

        then:
        result.errors.isEmpty()
        result.data.authors*.contextual == ['x0', 'x1']
        result.data.authors*.mappedContextual == ['m0', 'm1']
        CONTEXTS.size() == 2
        CONTEXTS.every { it instanceof GraphQLContext && ExecutionState.of(it as GraphQLContext) != null }
        CONTEXTS[0].is(CONTEXTS[1])
    }

    void "a customizer that returns new options with caching on re-enables the cache in a subscription"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { authorStream(count: 3) { returned } }')
        def events = Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        events[0].errors.isEmpty()
        events[0].data.authorStream*.returned == ['r1', 'r1', 'r1']
        RETURNED_BATCHES == [1]
    }

    @spock.lang.Requires({ DataLoaderOptions.methods.any { it.name == 'setCachingEnabled' } })
    void "on java-dataloader 3.x a customizer that mutates the options it was given does not re-enable the cache"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('subscription { authorStream(count: 3) { mutated } }')
        def events = Flux.from(result.<Publisher<ExecutionResult>> getData()).collectList().block()

        then:
        events[0].errors.isEmpty()
        events[0].data.authorStream*.mutated == ['u1', 'u1', 'u1']
        MUTATED_BATCHES.sum() == 3
    }

    void "a customizer for a loader that no bean has fails the startup, naming the loaders that exist"() {
        when:
        startContext(SCHEMA, SPEC + '.unknown', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOf(e)
        cause.getCode() == BindingCode.DATA_LOADER_NOT_FOUND
        cause.message.contains("'missingLoader'")
        cause.message.contains('chunkedLoader')
        cause.message.contains('loaderName()')
    }

    private static SchemaBindingException causeOf(Throwable e) {
        while (e != null) {
            if (e instanceof SchemaBindingException) {
                return e
            }
            e = e.cause
        }
        throw new AssertionError('No SchemaBindingException')
    }

    private static DataLoaderOptions smaller(int size) {
        new DataLoaderOptions() {
            @Override
            int maxBatchSize() {
                size
            }
        }
    }

    // ---- the loaders

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class SharedLoader implements BatchLoader<Long, String> {
        @Override
        CompletionStage<List<String>> load(List<Long> keys) {
            SHARED_BATCHES << new ArrayList<>(keys)
            CompletableFuture.completedFuture(keys.collect { 's' + it })
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class ChunkedLoader implements BatchLoader<String, String> {
        @Override
        CompletionStage<List<String>> load(List<String> keys) {
            CHUNKED_BATCHES << keys.size()
            CompletableFuture.completedFuture(keys.collect { 'c' + it })
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class OtherLoader implements BatchLoader<Integer, String> {
        @Override
        CompletionStage<List<String>> load(List<Integer> keys) {
            OTHER_BATCHES << keys.size()
            CompletableFuture.completedFuture(keys.collect { 'o' + it })
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class ContextLoader implements BatchLoaderWithContext<Float, String> {
        @Override
        CompletionStage<List<String>> load(List<Float> keys, BatchLoaderEnvironment environment) {
            CONTEXTS << environment.getContext()
            CompletableFuture.completedFuture(keys.collect { 'x' + it.intValue() })
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class MappedContextLoader implements MappedBatchLoaderWithContext<Double, String> {
        @Override
        CompletionStage<Map<Double, String>> load(Set<Double> keys, BatchLoaderEnvironment environment) {
            CONTEXTS << environment.getContext()
            CompletableFuture.completedFuture(keys.collectEntries { [(it): 'm' + it.intValue()] } as Map<Double, String>)
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class ReturnedLoader implements BatchLoader<Long, String> {
        @Override
        CompletionStage<List<String>> load(List<Long> keys) {
            RETURNED_BATCHES << keys.size()
            CompletableFuture.completedFuture(keys.collect { 'r' + it })
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    static class MutatedLoader implements BatchLoader<Long, String> {
        @Override
        CompletionStage<List<String>> load(List<Long> keys) {
            MUTATED_BATCHES << keys.size()
            CompletableFuture.completedFuture(keys.collect { 'u' + it })
        }
    }

    // ---- the customizers

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class ReturnedCustomizer implements DataLoaderOptionsCustomizer {
        @Override
        String loaderName() {
            'returnedLoader'
        }

        @Override
        DataLoaderOptions customize(DataLoaderOptions options) {
            new DataLoaderOptions() {
                @Override
                boolean cachingEnabled() {
                    true
                }
            }
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class MutatingCustomizer implements DataLoaderOptionsCustomizer {
        @Override
        String loaderName() {
            'mutatedLoader'
        }

        @Override
        DataLoaderOptions customize(DataLoaderOptions options) {
            if (options.metaClass.respondsTo(options, 'setCachingEnabled', boolean)) {
                options.setCachingEnabled(true)
            }
            options
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class FirstCustomizer implements DataLoaderOptionsCustomizer {
        @Override
        String loaderName() {
            'chunkedLoader'
        }

        @Override
        int getOrder() {
            1
        }

        @Override
        DataLoaderOptions customize(DataLoaderOptions options) {
            smaller(2)
        }
    }

    @Requires(property = 'spec.name', value = SPEC)
    @Singleton
    static class SecondCustomizer implements DataLoaderOptionsCustomizer {
        @Override
        String loaderName() {
            'chunkedLoader'
        }

        @Override
        int getOrder() {
            2
        }

        @Override
        DataLoaderOptions customize(DataLoaderOptions options) {
            SEEN_BY_SECOND << options.maxBatchSize()
            smaller(3)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.unknown')
    @Singleton
    static class MissingCustomizer implements DataLoaderOptionsCustomizer {
        @Override
        String loaderName() {
            'missingLoader'
        }

        @Override
        DataLoaderOptions customize(DataLoaderOptions options) {
            options
        }
    }

    // ---- the resolvers

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        List<Author> authors(int count) {
            (0..<count).collect { new Author(id: it.toString()) }
        }

        Publisher<List<Author>> authorStream(int count) {
            Flux.just((0..<count).collect { new Author(id: it.toString()) })
        }
    }

    @GraphQLType
    static class Author {
        String id
    }

    @Requires(property = 'spec.name', pattern = SPEC + '(\\.unknown)?')
    @Singleton
    @GraphQLTypeResolver(Author)
    static class AuthorResolver {
        CompletionStage<String> shared(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Long, String>> getDataLoader('sharedLoader').load(1L)
        }

        CompletionStage<String> chunked(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<String, String>> getDataLoader('chunkedLoader').load(author.id)
        }

        CompletionStage<String> other(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Integer, String>> getDataLoader('otherLoader').load(author.id as Integer)
        }

        CompletionStage<String> contextual(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Float, String>> getDataLoader('contextLoader').load(author.id as Float)
        }

        CompletionStage<String> returned(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Long, String>> getDataLoader('returnedLoader').load(1L)
        }

        CompletionStage<String> mutated(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Long, String>> getDataLoader('mutatedLoader').load(1L)
        }

        CompletionStage<String> mappedContextual(Author author, DataFetchingEnvironment environment) {
            environment.<DataLoader<Double, String>> getDataLoader('mappedContextLoader').load(author.id as Double)
        }
    }
}
