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
package io.micronaut.graphql.tools.scalars

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.ArrayValue
import graphql.language.BooleanValue
import graphql.language.FloatValue
import graphql.language.IntValue
import graphql.language.NullValue
import graphql.language.ObjectField
import graphql.language.ObjectValue
import graphql.language.StringValue
import graphql.language.VariableReference
import graphql.schema.CoercingSerializeException
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.scalars.internal.json.JsonCoercing
import io.micronaut.json.JsonMapper
import org.intellij.lang.annotations.Language

import static io.micronaut.graphql.tools.scalars.JsonFixtures.Reading

/** The JSON scalar: values that are JSON pass through, anything else is converted by Micronaut Serialization. */
class JsonScalarSpec extends AbstractScalarsSpec {

    static final String SPEC_NAME = 'JsonScalarSpec'
    static final GraphQLContext CONTEXT = GraphQLContext.getDefault()

    @Language('GraphQL')
    static final String SCHEMA = '''
scalar JSON
schema { query: Query }
type Query {
  reading: JSON
  readings: [JSON!]!
  echo(data: JSON): JSON
  settings(data: JSON!): String!
}
'''

    void "a bean is written as the maps, lists and numbers of its Serde representation"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        when:
        def result = execute('{ reading readings }')

        then:
        result.errors.isEmpty()
        result.data.reading == [sensor: 'ph', value: 5.5d, tags: ['a', 'b'], limits: [max: 9], previous: [sensor: 'ph', value: 4d]]
        result.data.readings == [[sensor: 'ph', value: 5.5d, tags: ['a', 'b'], limits: [max: 9], previous: [sensor: 'ph', value: 4d]], 'text', 7, [1, 2]]
    }

    void "the mapper that the scalar uses is the Micronaut Serialization one"() {
        given:
        startContext(SCHEMA, SPEC_NAME)

        expect:
        applicationContext.getBean(JsonMapper).class.name.startsWith('io.micronaut.serde')
    }

    void "values that are JSON already come back as the same objects"() {
        given:
        def coercing = new JsonCoercing('JSON', { throw new IllegalStateException('no mapper needed') })
        def map = [a: 1]
        def list = [1, 2]

        expect:
        coercing.serialize(map, CONTEXT, Locale.ROOT).is(map)
        coercing.serialize(list, CONTEXT, Locale.ROOT).is(list)
        coercing.serialize('x', CONTEXT, Locale.ROOT) == 'x'
        coercing.serialize(5, CONTEXT, Locale.ROOT) == 5
        coercing.serialize(true, CONTEXT, Locale.ROOT) == true
        coercing.parseValue(map, CONTEXT, Locale.ROOT).is(map)
    }

    void "a value that needs the mapper fails as a serialize error when there is none"() {
        given:
        def coercing = new JsonCoercing('JSON', { throw new IllegalStateException('no JsonMapper bean') })

        when:
        coercing.serialize(new Object(), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)
    }

    void "literals become Java values: integers keep their width, floats are doubles, variables are resolved"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def literal = ObjectValue.newObjectValue()
                .objectField(new ObjectField('n', NullValue.of()))
                .objectField(new ObjectField('s', StringValue.of('x')))
                .objectField(new ObjectField('b', BooleanValue.of(true)))
                .objectField(new ObjectField('i', IntValue.of(7)))
                .objectField(new ObjectField('l', new IntValue(BigInteger.valueOf(Integer.MAX_VALUE + 1L))))
                .objectField(new ObjectField('big', new IntValue(new BigInteger('123456789012345678901234567890'))))
                .objectField(new ObjectField('f', new FloatValue(new BigDecimal('2.5'))))
                .objectField(new ObjectField('a', ArrayValue.newArrayValue().value(IntValue.of(1)).value(StringValue.of('y')).build()))
                .objectField(new ObjectField('v', new VariableReference('who')))
                .build()

        when:
        def value = coercing.parseLiteral(literal, CoercedVariables.of([who: 'me']), CONTEXT, Locale.ROOT)

        then:
        value == [n: null, s: 'x', b: true, i: 7, l: 2147483648L, big: new BigInteger('123456789012345678901234567890'), f: 2.5d, a: [1, 'y'], v: 'me']
        value.i instanceof Integer
        value.l instanceof Long
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {

        Object reading() { sample() }

        List<Object> readings() { [sample(), 'text', 7, [1, 2]] }

        Object echo(Object data) { data }

        String settings(Map data) { data.toString() }

        private static Reading sample() {
            new Reading('ph', 5.5d, ['a', 'b'], [max: 9], new Reading('ph', 4d, [], [:], null))
        }

    }


    void "valueToLiteral keeps the JSON types: objects, lists, booleans, numbers, strings and null"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        def literal = coercing.valueToLiteral([a: 1, b: [true, 'x', 1.5d, null], c: [d: 3L]], CONTEXT, Locale.ROOT)

        then:
        literal instanceof ObjectValue
        def fields = literal.objectFields.collectEntries { [it.name, it.value] }
        fields.a instanceof IntValue && fields.a.value == 1
        fields.b instanceof ArrayValue
        fields.b.values*.class == [BooleanValue, StringValue, FloatValue, NullValue]
        fields.c instanceof ObjectValue
    }

    void "a number that is not finite is a serialize error"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.serialize(value, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)

        where:
        value << [Double.NaN, Double.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY]
    }
}
