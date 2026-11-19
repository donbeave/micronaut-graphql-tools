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

import graphql.ExecutionInput
import graphql.ExecutionResult
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class InputConversionErrorsSpec extends AbstractTest {

    static final String SPEC_NAME = "InputConversionErrorsSpec"

    @Language("GraphQL")
    static final String SCHEMA = """
scalar Code
scalar Json

schema {
  query: Query
}

type Query {
  ok: String!
  lookup(code: Code!): String
  lookupNonNull(code: Code!): String!
  payload(data: Json!): String
  box(box: BoxInput!): String
  strict(strict: StrictInput!): String
  codes(codes: [Code!]!): String
}

input BoxInput {
  items: [ItemInput!]!
}

input ItemInput {
  code: Code!
}

input StrictInput {
  size: Int!
}
"""

    void "a value that does not convert is an error that names the argument, and the other fields still resolve"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ ok lookup(code: "not-a-uuid") }')

        then:
        result.data == [ok: 'fine', lookup: null]
        result.errors.size() == 1
        def error = result.errors[0].toSpecification()
        error.message == 'Invalid value for argument Query.lookup(code): the value is not accepted by the scalar Code.'
        error.path == ['lookup']
        error.locations == [[line: 1, column: 6]]
        error.extensions == [code: 'INPUT_CONVERSION_FAILED', coordinate: 'Query.lookup(code)', inputPath: [],
                             classification: 'DataFetchingException']
        noInternals(result)
    }

    void "the error carries the path of the value inside input objects and lists"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('''{ box(box: {items: [{code: "11111111-1111-1111-1111-111111111111"}, {code: "nope"}]}) }''')

        then:
        result.errors.size() == 1
        def error = result.errors[0].toSpecification()
        error.message == 'Invalid value for argument Query.box(box) at items[1].code: the value is not accepted by the scalar Code.'
        error.extensions.inputPath == ['items', 1, 'code']
        error.extensions.coordinate == 'Query.box(box)'
        noInternals(result)
    }

    void "a list element at the top of the argument is named by its index"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ codes(codes: ["11111111-1111-1111-1111-111111111111", "x"]) }')

        then:
        result.errors[0].message == 'Invalid value for argument Query.codes(codes) at [1]: the value is not accepted by the scalar Code.'
        result.errors[0].extensions.inputPath == [1]
        noInternals(result)
    }

    void "a scalar that gives a list where the parameter is a Map is a GraphQL error without Java class names"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = execute('query($data: Json!) { ok payload(data: $data) }', [data: [1, 2]])

        then:
        result.data == [ok: 'fine', payload: null]
        result.errors.size() == 1
        result.errors[0].message == 'Invalid value for argument Query.payload(data): the value is not accepted by the scalar Json.'
        result.errors[0].extensions.code == 'INPUT_CONVERSION_FAILED'
        noInternals(result)
    }

    void "a failure in a non-null field goes to the nearest nullable parent"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ ok lookupNonNull(code: "nope") }')

        then:
        result.data == null
        result.errors.size() == 1
        result.errors[0].message == 'Invalid value for argument Query.lookupNonNull(code): the value is not accepted by the scalar Code.'
    }

    void "an input class that rejects a value reports the problem without its message or class"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ strict(strict: {size: 3}) }')

        then:
        result.data == [strict: null]
        result.errors[0].message == 'Invalid value for argument Query.strict(strict): the value could not be converted.'
        result.errors[0].extensions.code == 'INPUT_CONVERSION_FAILED'
        !result.errors[0].toSpecification().toString().contains('secret')
        noInternals(result)
    }

    void "a value that converts has no errors"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = executeQuery('{ lookup(code: "11111111-1111-1111-1111-111111111111") }')

        then:
        result.errors.isEmpty()
        result.data.lookup == '11111111-1111-1111-1111-111111111111'
    }

    private ExecutionResult execute(String query, Map variables) {
        getGraphQLBean().execute(ExecutionInput.newExecutionInput().query(query).variables(variables).build())
    }

    private static boolean noInternals(ExecutionResult result) {
        String body = result.toSpecification().toString()
        return !body.contains('java.') && !body.contains('com.') && !body.contains('io.micronaut') &&
                !body.contains('\tat ')
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @Factory
    static class Scalars {

        @Bean
        @Singleton
        GraphQLScalarMapping code() {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Code').coercing(new Coercing<Object, String>() {
                @Override
                String serialize(Object value) { value.toString() }

                @Override
                Object parseValue(Object value) { value }

                @Override
                Object parseLiteral(Object value) { ((StringValue) value).value }
            }).build()).outputClasses(UUID).inputClasses(UUID).build()
        }

        @Bean
        @Singleton
        GraphQLScalarMapping json() {
            return GraphQLScalarMapping.builder(GraphQLScalarType.newScalar().name('Json').coercing(new Coercing<Object, Object>() {
                @Override
                Object serialize(Object value) { value }

                @Override
                Object parseValue(Object value) { value }

                @Override
                Object parseLiteral(Object value) { throw new UnsupportedOperationException() }
            }).build()).outputClasses(Map).inputClasses(Map).build()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String ok() { 'fine' }

        String lookup(UUID code) { code.toString() }

        String lookupNonNull(UUID code) { code.toString() }

        String payload(Map data) { data.toString() }

        String box(BoxInput box) { box.items.size().toString() }

        String strict(StrictInput strict) { 'unreachable' }

        String codes(List<UUID> codes) { codes.size().toString() }
    }

    @GraphQLInput
    static class BoxInput {
        List<ItemInput> items
    }

    @GraphQLInput
    static class ItemInput {
        UUID code
    }

    @GraphQLInput
    static class StrictInput {
        int size

        void setSize(int size) {
            throw new IllegalStateException('secret internals')
        }
    }

}
