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

import graphql.schema.DataFetcher
import graphql.schema.FieldCoordinates
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.internal.execution.AsyncBoundaryDataFetcher
import io.micronaut.graphql.tools.internal.execution.ExecutorHopDataFetcher
import io.micronaut.graphql.tools.internal.resolver.MicronautExecutableMethodDataFetcher
import io.micronaut.graphql.tools.internal.resolver.MicronautPropertyDataFetcher
import io.micronaut.scheduling.annotation.ExecuteOn
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** A field fetcher decorator is asked once for each field at startup, and wraps outermost. */
class GraphQLFieldFetcherDecoratorSpec extends AbstractTest {

    static final String SPEC_NAME = 'GraphQLFieldFetcherDecoratorSpec'

    /** A dedicated executor: 'io' is the operation's executor on some JDK lines, and a field on that executor does not hop. */
    static final Map EXECUTORS = ['micronaut.executors.decorated.type': 'fixed', 'micronaut.executors.decorated.nThreads': 1]

    static final List<FieldDescriptor> ARGUMENT_OFFERS = new CopyOnWriteArrayList<>()
    static final List<FieldDescriptor> EVERYTHING_OFFERS = new CopyOnWriteArrayList<>()
    static final List<DataFetcher<Object>> INNER_OF_ARGUMENT_DECORATOR = new CopyOnWriteArrayList<>()
    static final List<String> CALLS = new CopyOnWriteArrayList<>()
    static final AtomicInteger DECISIONS = new AtomicInteger()

    @Language('GraphQL')
    static final String SCHEMA = """
type Query {
  echo(text: String): String
  plain: String
  hopped(text: String): Box
  box: Box
}

type Box {
  label: String
}
"""

    void setup() {
        [ARGUMENT_OFFERS, EVERYTHING_OFFERS, INNER_OF_ARGUMENT_DECORATOR, CALLS].each { it.clear() }
        DECISIONS.set(0)
    }

    void "a decorator that handles fields with arguments leaves the other fetchers as they are"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)
        def schema = getGraphQLBean().graphQLSchema

        expect:
        fetcher(schema, 'Query', 'plain').class == MicronautExecutableMethodDataFetcher
        fetcher(schema, 'Box', 'label').class == MicronautPropertyDataFetcher
        fetcher(schema, 'Query', 'box').class == MicronautExecutableMethodDataFetcher
        !(fetcher(schema, 'Query', 'echo') instanceof MicronautExecutableMethodDataFetcher)
        ARGUMENT_OFFERS*.coordinates().toSet() == [FieldCoordinates.coordinates('Query', 'echo'),
                                                   FieldCoordinates.coordinates('Query', 'hopped')].toSet()
    }

    void "a decorator that opts in is offered fields without arguments and properties"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)
        getGraphQLBean()

        expect:
        EVERYTHING_OFFERS.collect { it.coordinates().toString() }.toSet() == ['Query.echo', 'Query.plain', 'Query.hopped', 'Query.box', 'Box.label'].toSet()
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'label' }.kind() == FieldExecutionKind.TRIVIAL
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'plain' }.kind() == FieldExecutionKind.SYNCHRONOUS
        !EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'plain' }.hasArguments()
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'echo' }.hasArguments()
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'echo' }.annotationMetadata() != null
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'plain' }.resolver().endsWith(' plain()')
        EVERYTHING_OFFERS.find { it.coordinates().fieldName == 'label' }.resolver().endsWith(' getLabel()')
    }

    void "the descriptor carries the execution kind and the field executor"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)
        getGraphQLBean()
        def hopped = ARGUMENT_OFFERS.find { it.coordinates().fieldName == 'hopped' }
        def echo = ARGUMENT_OFFERS.find { it.coordinates().fieldName == 'echo' }

        expect:
        hopped.kind() == FieldExecutionKind.ASYNC
        hopped.fieldExecutor() != null
        echo.kind() == FieldExecutionKind.SYNCHRONOUS
        echo.fieldExecutor() == null
    }

    void "a decorator wraps the async boundary and the executor hop, not the other way round"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)
        getGraphQLBean()
        def hopped = ARGUMENT_OFFERS.find { it.coordinates().fieldName == 'hopped' }
        DataFetcher<Object> inner = INNER_OF_ARGUMENT_DECORATOR.find { it instanceof AsyncBoundaryDataFetcher }

        expect:
        hopped != null
        inner.delegate instanceof ExecutorHopDataFetcher
        inner.delegate.delegate instanceof MicronautExecutableMethodDataFetcher
    }

    void "the first decorator in order is the outermost"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)

        when:
        def result = executeQuery('{ echo(text: "a") }')

        then:
        result.errors.isEmpty()
        result.data == [echo: 'a']
        CALLS == ['argument', 'everything']
    }

    void "decisions are made at startup and none is made per call"() {
        given:
        startContext(SCHEMA, SPEC_NAME, EXECUTORS)
        getGraphQLBean()
        int atStartup = DECISIONS.get()

        when:
        5.times { executeQuery('{ echo(text: "a") plain box { label } }') }

        then:
        atStartup == 7
        DECISIONS.get() == atStartup
    }

    private static DataFetcher fetcher(schema, String type, String field) {
        def definition = schema.getObjectType(type).getFieldDefinition(field)
        schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates(type, field), definition)
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(1)
    static class ArgumentDecorator implements GraphQLFieldFetcherDecorator {

        @Override
        DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
            DECISIONS.incrementAndGet()
            ARGUMENT_OFFERS.add(field)
            INNER_OF_ARGUMENT_DECORATOR.add(fetcher)
            return { env ->
                CALLS.add('argument')
                fetcher.get(env)
            } as DataFetcher<Object>
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @Order(2)
    static class EverythingDecorator implements GraphQLFieldFetcherDecorator {

        @Override
        boolean decoratesFieldsWithoutArguments() {
            true
        }

        @Override
        boolean decoratesTrivialFields() {
            true
        }

        @Override
        DataFetcher<Object> decorate(FieldDescriptor field, DataFetcher<Object> fetcher) {
            DECISIONS.incrementAndGet()
            EVERYTHING_OFFERS.add(field)
            if (field.coordinates().fieldName != 'echo') {
                return fetcher
            }
            return { env ->
                CALLS.add('everything')
                fetcher.get(env)
            } as DataFetcher<Object>
        }
    }

    @GraphQLType
    static class Box {
        String getLabel() {
            'label'
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {

        String echo(String text) {
            text
        }

        String plain() {
            'plain'
        }

        @ExecuteOn('decorated')
        CompletableFuture<Box> hopped(String text) {
            CompletableFuture.completedFuture(new Box())
        }

        Box box() {
            new Box()
        }
    }
}
