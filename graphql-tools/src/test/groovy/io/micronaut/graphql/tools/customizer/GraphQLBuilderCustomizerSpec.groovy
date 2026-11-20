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
package io.micronaut.graphql.tools.customizer

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.GraphQL
import graphql.GraphqlErrorBuilder
import graphql.execution.AsyncExecutionStrategy
import graphql.execution.DataFetcherExceptionHandler
import graphql.execution.DataFetcherExceptionHandlerParameters
import graphql.execution.DataFetcherExceptionHandlerResult
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.execution.preparsed.PreparsedDocumentEntry
import graphql.execution.preparsed.PreparsedDocumentProvider
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.customizer.GraphQLBuilderCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

class GraphQLBuilderCustomizerSpec extends AbstractTest {

    static final String SPEC_NAME = "GraphQLBuilderCustomizerSpec"

    static final List<String> CALLS = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  broken: String
}
"""

    void setup() {
        CALLS.clear()
        CountingDocumentProvider.CALLS.set(0)
    }

    void "customizers configure the GraphQL bean in order"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ hello }')

        then: 'the instrumentation of the first customizer and the document provider of the second'
        result.errors.isEmpty()
        result.data == [hello: 'world']
        result.extensions == [instrumented: true]
        CountingDocumentProvider.CALLS.get() == 1
        CALLS == ['instrumentation', 'documentProvider', 'strategies']

        when: 'the exception handler set through the execution strategy and as the default'
        result = executeQuery('{ broken }')

        then:
        result.data == [broken: null]
        result.errors*.message == ['handled: broken on purpose']
    }

    static class MarkingInstrumentation extends SimplePerformantInstrumentation {
        @Override
        CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult,
                                                                     InstrumentationExecutionParameters parameters,
                                                                     InstrumentationState state) {
            return CompletableFuture.completedFuture(executionResult.transform { it.addExtension('instrumented', true) })
        }
    }

    // graphql-java 21.3 has the abstract getDocument, 25.1 the abstract getDocumentAsync; implementing both fits both
    static class CountingDocumentProvider implements PreparsedDocumentProvider {
        static final AtomicInteger CALLS = new AtomicInteger()

        PreparsedDocumentEntry getDocument(ExecutionInput executionInput,
                                           Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            CALLS.incrementAndGet()
            return parseAndValidateFunction.apply(executionInput)
        }

        CompletableFuture<PreparsedDocumentEntry> getDocumentAsync(ExecutionInput executionInput,
                                                                   Function<ExecutionInput, PreparsedDocumentEntry> parseAndValidateFunction) {
            return CompletableFuture.completedFuture(getDocument(executionInput, parseAndValidateFunction))
        }
    }

    static class PrefixingExceptionHandler implements DataFetcherExceptionHandler {
        @Override
        CompletableFuture<DataFetcherExceptionHandlerResult> handleException(DataFetcherExceptionHandlerParameters parameters) {
            def error = GraphqlErrorBuilder.newError()
                    .message('handled: ' + parameters.exception.message)
                    .path(parameters.path)
                    .build()
            return CompletableFuture.completedFuture(DataFetcherExceptionHandlerResult.newResult(error).build())
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class CustomizerFactory {
        @Bean
        @Singleton
        @Order(30)
        GraphQLBuilderCustomizer strategies() {
            return { GraphQL.Builder builder ->
                CALLS << 'strategies'
                def handler = new PrefixingExceptionHandler()
                builder.queryExecutionStrategy(new AsyncExecutionStrategy(handler))
                        .defaultDataFetcherExceptionHandler(handler)
            } as GraphQLBuilderCustomizer
        }

        @Bean
        @Singleton
        @Order(20)
        GraphQLBuilderCustomizer documentProvider() {
            return { GraphQL.Builder builder ->
                CALLS << 'documentProvider'
                builder.preparsedDocumentProvider(new CountingDocumentProvider())
            } as GraphQLBuilderCustomizer
        }

        @Bean
        @Singleton
        @Order(10)
        GraphQLBuilderCustomizer instrumentation() {
            return { GraphQL.Builder builder ->
                CALLS << 'instrumentation'
                builder.instrumentation(new MarkingInstrumentation())
            } as GraphQLBuilderCustomizer
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        String broken() {
            throw new IllegalStateException('broken on purpose')
        }
    }

}
