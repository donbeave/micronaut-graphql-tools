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

import graphql.GraphQLError
import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

import java.util.concurrent.CopyOnWriteArrayList

class CustomizerOrderSpec extends AbstractTest {

    static final String SCHEMA = '''
schema { query: Query }

type Query {
  hello: String
}
'''

    static final List<String> CALLS = new CopyOnWriteArrayList<>()

    void setup() {
        CALLS.clear()
    }

    void "three schema customizers run lowest order first, whatever the order they are declared in"() {
        given:
        startContext(SCHEMA)

        when:
        def result = executeQuery('{ __schema { description } }')

        then:
        result.errors.isEmpty()
        result.data == [__schema: [description: 'low,middle,high']]
        CALLS == ['schema-low', 'schema-middle', 'schema-high']
    }

    void "three errors customizers run lowest order first, whatever the order they are declared in"() {
        given:
        startContext(SCHEMA)

        when:
        def result = Mono.from(applicationContext.getBean(GraphQLInvocation)
                .invoke(new GraphQLInvocationData('{ hello ', null, [:]), null, null)).block()

        then:
        result.errors.size() == 1
        result.errors[0].extensions.trace == 'low,middle,high'
        CALLS.findAll { it.startsWith('errors-') } == ['errors-low', 'errors-middle', 'errors-high']
    }

    private static GraphQLSchema describe(GraphQLSchema schema, String name) {
        CALLS << ('schema-' + name)
        schema.transform { it.description(schema.description == null ? name : schema.description + ',' + name) }
    }

    private static List<GraphQLError> trace(List<GraphQLError> errors, String name) {
        CALLS << ('errors-' + name)
        errors.collect {
            Object previous = it.extensions?.trace
            graphql.GraphqlErrorBuilder.newError().message(it.message)
                    .extensions([trace: previous == null ? name : previous + ',' + name] as Map<String, Object>).build()
        }
    }

    @Singleton
    @Order(30)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class HighSchema implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) { describe(schema, 'high') }
    }

    @Singleton
    @Order(10)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class LowSchema implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) { describe(schema, 'low') }
    }

    @Singleton
    @Order(20)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class MiddleSchema implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) { describe(schema, 'middle') }
    }

    @Singleton
    @Order(3)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class HighErrors implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) { trace(errors, 'high') }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class LowErrors implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) { trace(errors, 'low') }
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class MiddleErrors implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) { trace(errors, 'middle') }
    }

    @Singleton
    @GraphQLRootResolver
    @Requires(property = 'spec.name', value = 'CustomizerOrderSpec')
    static class Query {
        String hello() { 'world' }
    }

}
