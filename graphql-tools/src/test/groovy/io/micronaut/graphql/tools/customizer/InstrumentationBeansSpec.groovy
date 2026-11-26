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

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.execution.instrumentation.ChainedInstrumentation
import graphql.execution.instrumentation.Instrumentation
import graphql.execution.instrumentation.InstrumentationState
import graphql.execution.instrumentation.SimplePerformantInstrumentation
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters
import graphql.schema.GraphQLSchema
import graphql.schema.PropertyDataFetcher
import graphql.TrivialDataFetcher
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture

/**
 * Every Instrumentation bean takes part in an execution, in bean order, and the library installs none when there is no bean.
 */
class InstrumentationBeansSpec extends AbstractSpec {

    static final String CHAINED = "InstrumentationBeansSpecChained"
    static final String SINGLE = "InstrumentationBeansSpecSingle"
    static final String NONE = "InstrumentationBeansSpecNone"

    static final List<String> CALLS = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  hello: String
  book: Book
}

type Book {
  title: String
}
"""

    void setup() {
        CALLS.clear()
    }

    void "two Instrumentation beans both observe one execution, lowest order first"() {
        given:
        startContext(SCHEMA, CHAINED)

        when:
        ExecutionResult result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'world']
        graphQLBean.instrumentation instanceof ChainedInstrumentation
        CALLS == ['first:begin', 'second:begin']

        when: 'a second execution'
        CALLS.clear()
        executeQuery('{ hello }')

        then: 'the order is the same'
        CALLS == ['first:begin', 'second:begin']
    }

    void "one Instrumentation bean is installed as it is"() {
        given:
        startContext(SCHEMA, SINGLE)

        expect:
        installed().any { it instanceof Recording }
        installed().findAll { it instanceof Recording }.size() == 1
        executeQuery('{ hello }').data == [hello: 'world']
        CALLS == ['only:begin']
    }

    void "without an Instrumentation bean the library sets no instrumentation"() {
        given:
        startContext(SCHEMA, NONE)
        GraphQLSchema schema = applicationContext.getBean(GraphQLSchema)

        expect: 'the default of graphql-java is untouched'
        graphQLBean.instrumentation.getClass() == GraphQL.newGraphQL(schema).build().instrumentation.getClass()
        !(graphQLBean.instrumentation instanceof Recording)
    }

    void "the library's own fetchers are not rewritten by an Instrumentation bean"() {
        given:
        startContext(SCHEMA, CHAINED)
        def registry = applicationContext.getBean(GraphQLSchema).codeRegistry
        def title = registry.getDataFetcher(
                graphql.schema.FieldCoordinates.coordinates('Book', 'title'),
                applicationContext.getBean(GraphQLSchema).getObjectType('Book').getFieldDefinition('title'))

        expect: 'a property read stays the trivial fetcher'
        title instanceof TrivialDataFetcher || title instanceof PropertyDataFetcher
    }

    // graphql-java 21 chains whatever instrumentation it is given with its own, so the bean is looked for in the chain
    private List<Instrumentation> installed() {
        def root = graphQLBean.instrumentation
        return root instanceof ChainedInstrumentation ? ((ChainedInstrumentation) root).instrumentations : [root]
    }

    static class Recording extends SimplePerformantInstrumentation {
        final String name

        Recording(String name) {
            this.name = name
        }

        @Override
        InstrumentationState createState(graphql.execution.instrumentation.parameters.InstrumentationCreateStateParameters parameters) {
            CALLS << (name + ':begin')
            return null
        }
    }

    @Requires(property = 'spec.name', value = CHAINED)
    @Singleton
    @Order(20)
    static class Second extends Recording {
        Second() { super('second') }
    }

    @Requires(property = 'spec.name', value = CHAINED)
    @Singleton
    @Order(10)
    static class First extends Recording {
        First() { super('first') }
    }

    @Requires(property = 'spec.name', value = SINGLE)
    @Singleton
    static class Only extends Recording {
        Only() { super('only') }
    }

    @Requires(property = 'spec.name', pattern = 'InstrumentationBeansSpec.*')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }

        Book book() {
            return new Book(title: 'T')
        }
    }

    @Requires(property = 'spec.name', pattern = 'InstrumentationBeansSpec.*')
    @Introspected
    static class Book {
        String title
    }

}
