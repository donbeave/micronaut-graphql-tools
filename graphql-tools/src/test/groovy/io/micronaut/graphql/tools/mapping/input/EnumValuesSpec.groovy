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
package io.micronaut.graphql.tools.mapping.input

import graphql.schema.GraphQLEnumType
import graphql.schema.idl.MapEnumValuesProvider
import graphql.schema.idl.RuntimeWiring
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.annotation.Introspected
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.customizer.GraphQLRuntimeWiringCustomizer
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language
import io.micronaut.graphql.tools.exceptions.SchemaBindingException
import io.micronaut.graphql.tools.binding.BindingCode

class EnumValuesSpec extends AbstractSpec {

    static final String SPEC_NAME = "EnumValuesSpec"
    static final String SPEC_NOT_INTROSPECTED = "EnumValuesSpecNotIntrospected"
    static final String SPEC_REPLACED = "EnumValuesSpecReplaced"

    static final List<Object> RECEIVED = Collections.synchronizedList([])

    @Language("GraphQL")
    static final String SCHEMA = """
schema {
  query: Query
}

type Query {
  pick(color: Color!): String
  next(color: Color): Color
  filtered(filter: Filter!): String
  many(colors: [Color!]!): String
  fallback(color: Color = GREEN): String
}

enum Color {
  RED
  GREEN
}

input Filter {
  color: Color!
}
"""

    @Language("GraphQL")
    static final String SCHEMA_NOT_INTROSPECTED = """
schema {
  query: Query
}

type Query {
  shade: Shade
}

enum Shade {
  LIGHT
  DARK
}
"""

    void setup() {
        RECEIVED.clear()
    }

    void "the schema enum values are the Java constants"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        GraphQLEnumType type = (GraphQLEnumType) getGraphQLBean().graphQLSchema.getType('Color')
        type.getValue('RED').value.is(Color.RED)
        type.getValue('GREEN').value.is(Color.GREEN)
    }

    void "an enum argument arrives as the Java constant"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ pick(color: GREEN) }')

        then:
        result.errors.isEmpty()
        result.data == [pick: 'GREEN']
        RECEIVED.size() == 1
        RECEIVED[0].is(Color.GREEN)
    }

    void "an enum argument given by a variable and an enum input field arrive as the Java constants"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def viaVariable = getGraphQLBean().execute(graphql.ExecutionInput.newExecutionInput()
                .query('query($c: Color!) { pick(color: $c) }').variables([c: 'RED']).build())
        def viaInput = executeQuery('{ filtered(filter: {color: GREEN}) }')

        then:
        viaVariable.errors.isEmpty()
        viaInput.errors.isEmpty()
        RECEIVED.size() == 2
        RECEIVED[0].is(Color.RED)
        RECEIVED[1].is(Color.GREEN)
    }

    void "a list of enums and a schema default value arrive as the Java constants"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def many = executeQuery('{ many(colors: [GREEN, RED]) }')
        def fallback = executeQuery('{ fallback }')

        then:
        many.errors.isEmpty()
        fallback.errors.isEmpty()
        many.data == [many: 'GREEN,RED']
        fallback.data == [fallback: 'GREEN']
        RECEIVED.size() == 2
        RECEIVED[0] == [Color.GREEN, Color.RED]
        RECEIVED[0].every { it instanceof Color }
        RECEIVED[1].is(Color.GREEN)
    }

    void "an enum field returns the constant by its schema name"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        executeQuery('{ next(color: RED) }').data == [next: 'GREEN']
        executeQuery('{ next(color: GREEN) }').data == [next: 'RED']
        executeQuery('{ next }').data == [next: null]
    }

    void "an unknown enum value is a request error and never reaches the resolver"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def literal = executeQuery('{ pick(color: PURPLE) }')
        def variable = getGraphQLBean().execute(graphql.ExecutionInput.newExecutionInput()
                .query('query($c: Color!) { pick(color: $c) }').variables([c: 'PURPLE']).build())

        then:
        literal.data == null
        literal.errors.size() == 1
        literal.errors[0].message.contains('PURPLE')
        variable.data == null
        variable.errors.size() == 1
        variable.errors[0].message.contains('PURPLE')
        RECEIVED.isEmpty()
    }

    void "an enum without a bean introspection fails the startup and names the fix"() {
        given:
        startContext(SCHEMA_NOT_INTROSPECTED, SPEC_NOT_INTROSPECTED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.CLASS_NOT_INTROSPECTED
        e.cause.message.startsWith("[class-not-introspected] The class ${Shade.name} is not introspected. " +
                "Ensure the class is annotated with ${Introspected.name}.")
    }

    void "a customizer cannot replace the values provider of a bound enum"() {
        given:
        startContext(SCHEMA, SPEC_REPLACED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaBindingException
        e.cause.getCode() == BindingCode.RUNTIME_WIRING_OVERRIDE
        e.cause.replacements == ["enum values provider of Color (${ReplaceValues.name})".toString()]
    }

    @Introspected
    static enum Color {
        RED, GREEN
    }

    static enum Shade {
        LIGHT, DARK
    }

    @GraphQLInput
    static class Filter {
        Color color
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Singleton
    @GraphQLRootResolver
    static class Query {
        String pick(Color color) {
            RECEIVED << color
            return color.name()
        }

        Color next(Color color) {
            return color == null ? null : (color == Color.RED ? Color.GREEN : Color.RED)
        }

        String filtered(Filter filter) {
            RECEIVED << filter.color
            return filter.color.name()
        }

        String many(List<Color> colors) {
            RECEIVED << colors
            return colors*.name().join(',')
        }

        String fallback(Color color) {
            RECEIVED << color
            return color.name()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_REPLACED)
    @Singleton
    @GraphQLRootResolver
    static class ReplacedQuery {
        String pick(Color color) {
            return color.name()
        }

        Color next(Color color) {
            return color
        }

        String filtered(Filter filter) {
            return filter.color.name()
        }

        String many(List<Color> colors) {
            return colors*.name().join(',')
        }

        String fallback(Color color) {
            return color?.name()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NOT_INTROSPECTED)
    @Singleton
    @GraphQLRootResolver
    static class ShadeQuery {
        Shade shade() {
            return Shade.LIGHT
        }
    }

    @Requires(property = 'spec.name', value = SPEC_REPLACED)
    @Singleton
    static class ReplaceValues implements GraphQLRuntimeWiringCustomizer {
        @Override
        void customize(RuntimeWiring.Builder builder) {
            builder.type('Color') { it.enumValues(new MapEnumValuesProvider([RED: 'r', GREEN: 'g'])) }
        }
    }

}
