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
package io.micronaut.graphql.tools

import io.micronaut.graphql.tools.customizer.GraphQLSchemaCustomizer
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.util.concurrent.atomic.AtomicInteger
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class EagerInitSpec extends AbstractSpec {

    static final String SPEC_NAME = "EagerInitSpec"
    static final String SPEC_NAME_OWN_GRAPHQL = "EagerInitSpec.ownGraphQL"

    static final AtomicInteger BINDINGS = new AtomicInteger()

    @Language("GraphQL")
    static final String VALID = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    @Language("GraphQL")
    static final String BROKEN = """
schema {
  query: Query
}

type Query {
  hello: String
  missing: String
}
"""

    @Override
    protected boolean lazyBinding() {
        false
    }

    void setup() {
        BINDINGS.set(0)
    }

    void "a field without a binding fails the context start with the binding error"() {
        when:
        startContext(BROKEN, SPEC_NAME)

        then:
        def e = thrown(BeanInstantiationException)
        rootCause(e) instanceof SchemaBindingException
        rootCause(e).getCode() == BindingCode.FIELD_NOT_BOUND
        rootCause(e).message.contains("Query.missing")
        applicationContext == null
    }

    void "a field without a binding fails the context start when the application defines its own GraphQL bean"() {
        when:
        startContext(BROKEN, SPEC_NAME_OWN_GRAPHQL)

        then: 'the library has no GraphQL bean here, but the schema is still bound'
        def e = thrown(BeanInstantiationException)
        rootCause(e) instanceof SchemaBindingException
        rootCause(e).getCode() == BindingCode.FIELD_NOT_BOUND
        rootCause(e).message.contains("Query.missing")
    }

    void "a valid schema starts, and the first request does no binding work"() {
        when:
        startContext(VALID, SPEC_NAME)

        then: 'bound at startup'
        BINDINGS.get() == 1

        when:
        def result = executeQuery('{ hello }')

        then:
        result.data == [hello: 'world']
        BINDINGS.get() == 1
        applicationContext.findBean(GraphQLSchema).isPresent()
    }

    void "a valid schema starts with an application-defined GraphQL bean, and the schema is already bound"() {
        when:
        startContext(VALID, SPEC_NAME_OWN_GRAPHQL)

        then:
        BINDINGS.get() == 1
        executeQuery('{ hello }').data == [hello: 'own']
        BINDINGS.get() == 1
    }

    void "with eager-init false nothing is bound at startup, and a broken binding fails on the first lookup"() {
        when:
        startContext(BROKEN, SPEC_NAME, ['micronaut.graphql.tools.eager-init': false])

        then:
        BINDINGS.get() == 0

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        rootCause(e) instanceof SchemaBindingException
        rootCause(e).getCode() == BindingCode.FIELD_NOT_BOUND
    }

    void "with eager-init false a valid schema is bound on first use"() {
        when:
        startContext(VALID, SPEC_NAME, ['micronaut.graphql.tools.eager-init': false])

        then:
        BINDINGS.get() == 0

        when:
        def result = executeQuery('{ hello }')

        then:
        result.data == [hello: 'world']
        BINDINGS.get() == 1
    }

    void "eager-init true is the same as the default"() {
        when:
        startContext(BROKEN, SPEC_NAME, ['micronaut.graphql.tools.eager-init': true])

        then:
        thrown(BeanInstantiationException)
    }

    private static Throwable rootCause(Throwable e) {
        while (e.cause != null && !e.cause.is(e)) {
            e = e.cause
        }
        return e
    }

    @Requires(property = 'spec.name', pattern = 'EagerInitSpec(|\\.ownGraphQL)')
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String hello() {
            return 'world'
        }
    }

    @Requires(property = 'spec.name', pattern = 'EagerInitSpec(|\\.ownGraphQL)')
    @Singleton
    static class CountingCustomizer implements GraphQLSchemaCustomizer {
        @Override
        GraphQLSchema customize(GraphQLSchema schema) {
            BINDINGS.incrementAndGet()
            return schema
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_OWN_GRAPHQL)
    @Factory
    static class OwnGraphQLFactory {
        @Bean
        @Singleton
        GraphQL graphQL() {
            return GraphQL.newGraphQL(GraphQLSchema.newSchema()
                    .query(graphql.schema.GraphQLObjectType.newObject().name('Query')
                            .field { it.name('hello').type(graphql.Scalars.GraphQLString) }.build())
                    .codeRegistry(graphql.schema.GraphQLCodeRegistry.newCodeRegistry()
                            .dataFetcher(graphql.schema.FieldCoordinates.coordinates('Query', 'hello'),
                                    new graphql.schema.StaticDataFetcher('own')).build())
                    .build()).build()
        }
    }

}
