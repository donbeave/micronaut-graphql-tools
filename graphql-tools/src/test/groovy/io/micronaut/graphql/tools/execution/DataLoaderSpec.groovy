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

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters
import graphql.schema.DataFetcher
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer
import graphql.schema.DataFetchingEnvironment
import io.micronaut.configuration.graphql.DefaultGraphQLExecutionInputCustomizer
import io.micronaut.configuration.graphql.DefaultGraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLExecutionInputCustomizer
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.core.propagation.PropagatedContext
import io.micronaut.core.propagation.PropagatedContextElement
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.annotation.GraphQLTypeResolver
import io.micronaut.graphql.tools.binding.BindingCode
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.internal.execution.LazyDataLoaderRegistry
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import jakarta.inject.Named
import jakarta.inject.Singleton
import org.dataloader.BatchLoader
import org.dataloader.BatchLoaderEnvironment
import org.dataloader.BatchLoaderWithContext
import org.dataloader.DataLoader
import org.dataloader.DataLoaderRegistry
import org.dataloader.MappedBatchLoader
import org.dataloader.MappedBatchLoaderWithContext
import org.intellij.lang.annotations.Language
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** The batch loader beans, the lazy registry of each execution and the typed DataLoader parameters. */
class DataLoaderSpec extends AbstractTest {

    static final String SPEC = 'DataLoaderSpec'
    static final String LOADED = SPEC + '(\\.(concurrent|replaced))?'

    static final AtomicInteger BOOK_BATCHES = new AtomicInteger()
    static final List<Set<String>> BOOK_KEYS = Collections.synchronizedList([])
    static final List<Object> MARKERS = Collections.synchronizedList([])
    static final List<DataLoaderRegistry> REGISTRIES = Collections.synchronizedList([])

    @Language('GraphQL')
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  authors(count: Int!): [Author!]!
  loaders: [String!]!
  contextual: String
}

type Author {
  id: ID!
  book: String
  bookByName: String
  titled(prefix: String!): String
}
"""

    void setup() {
        BOOK_BATCHES.set(0)
        BOOK_KEYS.clear()
        MARKERS.clear()
        REGISTRIES.clear()
    }

    private ExecutionResult run(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
    }

    void "a list of 50 parents with a typed DataLoader parameter issues one batch call"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 50) { id book } }')

        then:
        result.errors.isEmpty()
        result.data.authors.size() == 50
        result.data.authors.every { it.book == 'book-' + it.id }
        BOOK_BATCHES.get() == 1
        BOOK_KEYS[0].size() == 50
    }

    void "a DataLoader parameter binds at any position, and the other parameters still bind by name"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 3) { id titled(prefix: "Dr. ") } }')

        then:
        result.errors.isEmpty()
        result.data.authors*.titled == ['Dr. book-0', 'Dr. book-1', 'Dr. book-2']
        BOOK_BATCHES.get() == 1
    }

    void "the loader of a name lookup and of a typed parameter is one loader, so one batch serves both"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 4) { id book bookByName } }')

        then:
        result.errors.isEmpty()
        result.data.authors.every { it.book == it.bookByName }
        BOOK_BATCHES.get() == 1
        BOOK_KEYS[0].size() == 4
    }

    void "a loader that is never used is never created, and the registry lists only created loaders"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def none = run('{ loaders }')

        then:
        none.errors.isEmpty()
        none.data.loaders == []

        when:
        def one = run('{ authors(count: 1) { book } loaders }')

        then:
        one.errors.isEmpty()
        one.data.loaders == ['bookLoader']
    }

    void "a loader bean is named by its @Named qualifier, else by its simple class name with a lower-case first letter"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        def result = run('{ authors(count: 1) { book } contextual loaders }')

        then:
        result.errors.isEmpty()
        result.data.loaders.sort() == ['bookLoader', 'contextual']
    }

    void "two executions never share a loader or its cache, also when they run at the same time"() {
        given:
        startContext(SCHEMA, SPEC + '.concurrent')
        def threads = (1..2).collect { n ->
            Thread.start { run('{ authors(count: 2) { book } }') }
        }

        when:
        threads*.join(10_000)

        then:
        BOOK_BATCHES.get() == 2
        BOOK_KEYS.every { it.size() == 2 }
        registries().size() == 2
        registries().every { it instanceof LazyDataLoaderRegistry }
    }

    void "the second request does not see the cache of the first"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        run('{ authors(count: 2) { book } }')
        run('{ authors(count: 2) { book } }')

        then:
        BOOK_BATCHES.get() == 2
    }

    void "a batch function sees the propagated context of the execution when it is dispatched on another thread"() {
        given:
        startContext(SCHEMA, SPEC + '.concurrent')
        def marker = new Marker('request-1')

        when:
        def scope = PropagatedContext.getOrEmpty().plus(marker).propagate()
        try {
            run('{ contextual }')
        } finally {
            scope.close()
        }

        then:
        MARKERS == [marker]

        when: 'the loader of that execution is used again later, from a thread that holds no context'
        MARKERS.clear()
        def registry = REGISTRIES.last()
        assert PropagatedContext.getOrEmpty().findAll(Marker).count() == 0
        DataLoader<Long, String> loader = registry.getDataLoader('contextual')
        def future = loader.load(7L)
        loader.dispatch()
        future.get(5, TimeUnit.SECONDS)

        then: 'the wrapper installed the context that the execution captured'
        MARKERS == [marker]
    }

    void "the four loader kinds all batch: batch, with context, mapped and mapped with context"() {
        given:
        startContext(SCHEMA, SPEC)
        run('{ contextual }')
        DataLoaderRegistry registry = REGISTRIES.last()

        when:
        DataLoader<String, String> mapped = registry.getDataLoader('bookLoader')
        DataLoader<Long, String> withContext = registry.getDataLoader('contextual')
        DataLoader<Integer, Double> batch = registry.getDataLoader('nameLoader')
        DataLoader<Float, Float> mappedWithContext = registry.getDataLoader('unusedLoader')
        def futures = [mapped.load('a'), withContext.load(2L), batch.load(3), mappedWithContext.load(4f)]
        registry.dispatchAll()

        then:
        futures[0].get(5, TimeUnit.SECONDS) == 'book-a'
        futures[1].get(5, TimeUnit.SECONDS) == 'context-2'
        futures[2].get(5, TimeUnit.SECONDS) == 3d
        futures[3].get(5, TimeUnit.SECONDS) == 4f
        registry.keys.sort() == ['bookLoader', 'contextual', 'nameLoader', 'unusedLoader']
        registry.getDataLoader('unknown') == null
    }

    void "the registry of a customizer replaces the library's: a DataLoader parameter is a field error that names the customizer"() {
        given:
        startContext(SCHEMA, SPEC + '.replaced')

        when:
        def result = run('{ authors(count: 1) { id book } }')

        then:
        result.errors.size() == 1
        result.errors[0].message.contains('ReplacingCustomizer')
        result.errors[0].extensions.code == 'DATA_LOADER_UNAVAILABLE'
        result.errors[0].path == ['authors', 0, 'book']
    }

    void "without batch loader beans no registry is set, so graphql-java keeps its empty one and its no-op dispatch"() {
        given:
        startContext(SCHEMA2, SPEC + '.plain')
        def expected = ExecutionInput.newExecutionInput().query('{ hello(prefix: "x") }').build().dataLoaderRegistry

        when:
        def result = run('{ hello(prefix: "x") }')

        then:
        result.data.hello == 'x'
        REGISTRIES.size() == 1
        REGISTRIES[0].is(expected)
        !(REGISTRIES[0] instanceof LazyDataLoaderRegistry)

        and: 'on graphql-java 22 and later the execution selected NO_OP (21 has no such strategy)'
        !STRATEGIES.isEmpty()
        STRATEGIES.every { it == 'none' || it.is(noOp()) }
    }

    void "a DataLoaderRegistry bean fails the startup with data-loader-registry-bean and the fix"() {
        when:
        startContext(SCHEMA2, SPEC + '.registry', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.DATA_LOADER_REGISTRY_BEAN
        cause.message.startsWith('[data-loader-registry-bean] ')
        cause.message.contains('RegistryFactory')
        cause.message.contains('shared across operations on one WebSocket connection')
        cause.message.contains('BatchLoader')
        cause.message.contains('GraphQLContextContributor')
        cause.message.contains('@Replaces(DefaultGraphQLInvocation.class)')
    }

    void "two loaders with one name fail the startup with duplicate-data-loader"() {
        when:
        startContext(SCHEMA2, SPEC + '.duplicate', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.DUPLICATE_DATA_LOADER
        cause.message.contains("'shared'")
        cause.message.contains('@Named')
    }

    void "a DataLoader parameter that matches no loader fails the startup with data-loader-not-found, naming the method"() {
        when:
        startContext(SCHEMA2, SPEC + '.unmatched', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.DATA_LOADER_NOT_FOUND
        cause.message.contains("parameter 'loader' of")
        cause.message.contains('Unmatched#hello')
        cause.message.contains('DataLoader<java.lang.Integer, java.lang.Integer>')
    }

    void "a DataLoader parameter that matches two loaders fails the startup unless it is named"() {
        when:
        startContext(SCHEMA2, SPEC + '.ambiguous', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.DATA_LOADER_NOT_FOUND
        cause.message.contains('2 batch loader beans')
        cause.message.contains('@Named')

        when: 'the parameter names the loader'
        applicationContext?.close()
        startContext(SCHEMA2, SPEC + '.named', ['micronaut.graphql.tools.eager-init': true])

        then:
        run('{ hello(prefix: "a") }').data.hello == 'second:a'
    }

    void "a raw DataLoader parameter fails the startup with argument-mismatch"() {
        when:
        startContext(SCHEMA2, SPEC + '.untyped', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.ARGUMENT_MISMATCH
        cause.message.contains('without its key and value types')
    }

    void "a DataLoader parameter fails the startup with execution-context-unavailable when the application replaces the invocation"() {
        when:
        startContext(SCHEMA2, SPEC + '.stepaside', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.EXECUTION_CONTEXT_UNAVAILABLE
        cause.message.contains('a DataLoader')
        cause.message.contains('Query.hello')
    }

    void "a DataLoader parameter fails the startup with execution-context-unavailable when the application invocation is primary"() {
        when:
        startContext(SCHEMA2, SPEC + '.primary', ['micronaut.graphql.tools.eager-init': true])

        then:
        def e = thrown(Exception)
        def cause = causeOfType(e, SchemaBindingException)
        cause.code() == BindingCode.EXECUTION_CONTEXT_UNAVAILABLE
        cause.message.contains('a DataLoader')
        cause.message.contains('PrimaryInvocation')
    }

    @Language('GraphQL')
    static final String SCHEMA2 = """
schema {
  query: Query
}

type Query {
  hello(prefix: String!): String
}
"""

    private static final List<Object> STRATEGIES = Collections.synchronizedList([])

    private static Set<DataLoaderRegistry> registries() {
        Set<DataLoaderRegistry> distinct = Collections.newSetFromMap(new IdentityHashMap<DataLoaderRegistry, Boolean>())
        distinct.addAll(REGISTRIES)
        distinct
    }

    private static Object noOp() {
        try {
            Class.forName('graphql.execution.DataLoaderDispatchStrategy').getField('NO_OP').get(null)
        } catch (ClassNotFoundException ignored) {
            null
        }
    }

    private static Throwable causeOfType(Throwable e, Class<? extends Throwable> type) {
        for (Throwable cause = e; cause != null; cause = cause.cause) {
            if (type.isInstance(cause)) {
                return cause
            }
        }
        throw new AssertionError("No cause of type ${type.simpleName} in ${e}")
    }

    record Marker(String id) implements PropagatedContextElement {
    }

    // ---- the loaders

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    static class BookLoader implements MappedBatchLoader<String, String> {
        @Override
        CompletionStage<Map<String, String>> load(Set<String> keys) {
            BOOK_BATCHES.incrementAndGet()
            BOOK_KEYS << keys
            CompletableFuture.completedFuture(keys.collectEntries { [(it): 'book-' + it] } as Map<String, String>)
        }
    }

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    @Named('contextual')
    static class ContextualLoader implements BatchLoaderWithContext<Long, String> {
        @Override
        CompletionStage<List<String>> load(List<Long> keys, BatchLoaderEnvironment environment) {
            MARKERS.addAll(PropagatedContext.getOrEmpty().findAll(Marker).toList())
            CompletableFuture.completedFuture(keys.collect { 'context-' + it })
        }
    }

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    static class NameLoader implements BatchLoader<Integer, Double> {
        @Override
        CompletionStage<List<Double>> load(List<Integer> keys) {
            CompletableFuture.completedFuture(keys.collect { it.doubleValue() })
        }
    }

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    static class UnusedLoader implements MappedBatchLoaderWithContext<Float, Float> {
        @Override
        CompletionStage<Map<Float, Float>> load(Set<Float> keys, BatchLoaderEnvironment environment) {
            CompletableFuture.completedFuture(keys.collectEntries { [(it): it] } as Map<Float, Float>)
        }
    }

    // ---- the resolvers of the schema with loaders

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        List<Author> authors(int count) {
            (0..<count).collect { new Author(id: it.toString()) }
        }

        List<String> loaders(DataFetchingEnvironment environment) {
            REGISTRIES << environment.dataLoaderRegistry
            environment.dataLoaderRegistry.keys.sort()
        }

        CompletionStage<String> contextual(DataLoader<Long, String> loader, DataFetchingEnvironment environment) {
            REGISTRIES << environment.dataLoaderRegistry
            loader.load(1L)
        }
    }

    @GraphQLType
    static class Author {
        String id
    }

    @Requires(property = 'spec.name', pattern = LOADED)
    @Singleton
    @GraphQLTypeResolver(Author)
    static class AuthorResolver {
        CompletionStage<String> book(Author author, DataLoader<String, String> loader, DataFetchingEnvironment environment) {
            REGISTRIES << environment.dataLoaderRegistry
            loader.load(author.id)
        }

        CompletionStage<String> bookByName(Author author, DataFetchingEnvironment environment) {
            DataLoader<String, String> loader = environment.getDataLoader('bookLoader')
            loader.load(author.id)
        }

        CompletionStage<String> titled(Author author, DataLoader<String, String> loader, String prefix) {
            loader.load(author.id).thenApply { prefix + it }
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.replaced')
    @Singleton
    static class ReplacingCustomizer implements GraphQLExecutionInputCustomizer {
        @Override
        Publisher<ExecutionInput> customize(ExecutionInput executionInput, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.just(executionInput.transform { it.dataLoaderRegistry(new DataLoaderRegistry()) })
        }
    }

    // ---- no loaders

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(plain|registry|duplicate)')
    @Singleton
    @GraphQLRootResolver
    static class PlainQuery {
        String hello(String prefix, DataFetchingEnvironment environment) {
            REGISTRIES << environment.dataLoaderRegistry
            prefix
        }
    }

    /** Records the dispatch strategy that graphql-java selected, where the version has the notion. */
    @Requires(property = 'spec.name', value = SPEC + '.plain')
    @Singleton
    static class StrategyRecorder implements GraphQLBuilderCustomizer {
        @Override
        void customize(GraphQL.Builder builder) {
            builder.instrumentation(new SimplePerformantInstrumentation() {
                @Override
                DataFetcher<?> instrumentDataFetcher(DataFetcher<?> dataFetcher, InstrumentationFieldFetchParameters parameters, InstrumentationState state) {
                    def context = parameters.executionContext
                    STRATEGIES << (context.metaClass.respondsTo(context, 'getDataLoaderDispatcherStrategy') ?
                            context.getDataLoaderDispatcherStrategy() : 'none')
                    dataFetcher
                }
            })
        }
    }

    // ---- startup failures

    @Requires(property = 'spec.name', value = SPEC + '.registry')
    @Factory
    static class RegistryFactory {
        @Bean
        @Singleton
        DataLoaderRegistry registry() {
            new DataLoaderRegistry()
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.duplicate')
    @Singleton
    @Named('shared')
    static class FirstShared implements MappedBatchLoader<String, String> {
        @Override
        CompletionStage<Map<String, String>> load(Set<String> keys) {
            CompletableFuture.completedFuture([:] as Map<String, String>)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.duplicate')
    @Singleton
    @Named('shared')
    static class SecondShared implements BatchLoader<Long, String> {
        @Override
        CompletionStage<List<String>> load(List<Long> keys) {
            CompletableFuture.completedFuture([])
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(unmatched|ambiguous|named|untyped|stepaside|primary)')
    @Singleton
    @Named('first')
    static class First implements MappedBatchLoader<String, String> {
        @Override
        CompletionStage<Map<String, String>> load(Set<String> keys) {
            CompletableFuture.completedFuture(keys.collectEntries { [(it): 'first:' + it] } as Map<String, String>)
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(ambiguous|named)')
    @Singleton
    @Named('second')
    static class Second implements MappedBatchLoader<String, String> {
        @Override
        CompletionStage<Map<String, String>> load(Set<String> keys) {
            CompletableFuture.completedFuture(keys.collectEntries { [(it): 'second:' + it] } as Map<String, String>)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.unmatched')
    @Singleton
    @GraphQLRootResolver
    static class Unmatched {
        CompletionStage<String> hello(String prefix, DataLoader<Integer, Integer> loader) {
            CompletableFuture.completedFuture(prefix)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.ambiguous')
    @Singleton
    @GraphQLRootResolver
    static class Ambiguous {
        CompletionStage<String> hello(String prefix, DataLoader<String, String> loader) {
            loader.load(prefix)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.named')
    @Singleton
    @GraphQLRootResolver
    static class NamedQuery {
        CompletionStage<String> hello(String prefix, @Named('second') DataLoader<String, String> loader) {
            loader.load(prefix)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.untyped')
    @Singleton
    @GraphQLRootResolver
    static class Untyped {
        CompletionStage<String> hello(String prefix, DataLoader loader) {
            CompletableFuture.completedFuture(prefix)
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC + '\\.(stepaside|primary)')
    @Singleton
    @GraphQLRootResolver
    static class SteppedAside {
        CompletionStage<String> hello(String prefix, DataLoader<String, String> loader) {
            loader.load(prefix)
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.stepaside')
    @Singleton
    @Replaces(DefaultGraphQLInvocation)
    static class OwnInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

    @Requires(property = 'spec.name', value = SPEC + '.primary')
    @Singleton
    @Primary
    static class PrimaryInvocation implements GraphQLInvocation {
        @Override
        Publisher<ExecutionResult> invoke(GraphQLInvocationData invocationData, HttpRequest httpRequest, MutableHttpResponse<String> httpResponse) {
            Mono.empty()
        }
    }

}
