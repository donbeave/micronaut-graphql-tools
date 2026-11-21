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

import jakarta.inject.Singleton
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
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.scalars.internal.json.JsonCoercing
import io.micronaut.json.JsonMapper
import org.intellij.lang.annotations.Language

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.DoubleAdder
import java.util.concurrent.atomic.LongAccumulator
import java.util.concurrent.atomic.LongAdder

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
    @Singleton
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

    void "a float literal outside the range of a double is refused instead of read as infinity"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.parseLiteral(FloatValue.newFloatValue(new BigDecimal('1e10000')).build(), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)
    }
    void "a number that is not finite nested in a map or a list is a serialize error"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.serialize(value, CONTEXT, Locale.ROOT)

        then:
        def e = thrown(CoercingSerializeException)
        e.message.contains('not finite')

        where:
        value << [
                [a: Double.NaN],
                [a: [1, [b: Double.POSITIVE_INFINITY]]],
                [1, Float.NEGATIVE_INFINITY],
                [[2, [Float.NaN]]],
                [a: [b: [c: [Double.NaN]]]],
        ]
    }

    void "a nested finite number is serialized as it is"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def value = [a: [1, 2.5d, [b: 3f, c: new BigDecimal('1e-10000')]]]

        expect:
        coercing.serialize(value, CONTEXT, Locale.ROOT).is(value)
    }

    void "a number that is not finite nested in a variable value is a parse error"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.parseValue(value, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)

        where:
        value << [
                Double.NaN,
                [a: Double.POSITIVE_INFINITY],
                [1, [b: Float.NaN]],
                [a: [[Double.NEGATIVE_INFINITY]]],
        ]
    }

    void "a nested number that is not finite cannot become a literal"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.valueToLiteral([a: [1, Double.NaN]], CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)
    }

    void "a float literal that underflows to zero is refused, in a literal and in a nested literal"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def number = FloatValue.newFloatValue(new BigDecimal(text)).build()

        when:
        coercing.parseLiteral(number, CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT)
        coercing.parseLiteral(ArrayValue.newArrayValue().value(number).build(), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)

        where:
        text << ['1e-10000', '-1e-10000', '2.4e-324', '-2.4e-324', '1e-400']
    }

    void "a float literal that is zero or reaches the smallest subnormal double is accepted"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        expect:
        coercing.parseLiteral(FloatValue.newFloatValue(new BigDecimal(text)).build(), CoercedVariables.emptyVariables(), CONTEXT, Locale.ROOT) == expected

        where:
        text        | expected
        '0.0'       | 0.0d
        '0e5'       | 0.0d
        '0e-10000'  | 0.0d
        '2.5e-324'  | Double.MIN_VALUE
        '4.9e-324'  | Double.MIN_VALUE
        '1e-323'    | 1e-323d
        '2.5'       | 2.5d
    }

    void "a variable that underflows to zero or overflows is refused, also when nested"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.parseValue(value, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)

        where:
        value << [
                new BigDecimal('1e-10000'),
                [a: new BigDecimal('2.4e-324')],
                [[new BigDecimal('-1e-10000')]],
                new BigDecimal('1e10000'),
        ]
    }

    void "a variable that is zero or the smallest subnormal double is accepted"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        expect:
        coercing.parseValue(value, CONTEXT, Locale.ROOT).is(value)

        where:
        value << [new BigDecimal('0.0'), new BigDecimal('0e5'), [a: new BigDecimal('2.5e-324')], 4.9e-324d, 1.5d, 'x']
    }

    void "valueToLiteral keeps each integral Number type exact, also beyond 2^53"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        def literal = coercing.valueToLiteral(value, CONTEXT, Locale.ROOT)

        then:
        literal instanceof IntValue
        literal.value == expected

        where:
        value                                      | expected
        new AtomicLong(Long.MAX_VALUE)             | BigInteger.valueOf(Long.MAX_VALUE)
        new AtomicLong(9007199254740993L)          | BigInteger.valueOf(9007199254740993L)
        new AtomicLong(Long.MIN_VALUE)             | BigInteger.valueOf(Long.MIN_VALUE)
        new AtomicInteger(Integer.MAX_VALUE)       | BigInteger.valueOf(Integer.MAX_VALUE)
        new LongAdder() { { add(9007199254740993L) } } | BigInteger.valueOf(9007199254740993L)
        Long.MAX_VALUE                             | BigInteger.valueOf(Long.MAX_VALUE)
        Integer.MIN_VALUE                          | BigInteger.valueOf(Integer.MIN_VALUE)
        (short) 7                                  | BigInteger.valueOf(7)
        (byte) -3                                  | BigInteger.valueOf(-3)
        new BigInteger('123456789012345678901234567890') | new BigInteger('123456789012345678901234567890')
    }

    void "valueToLiteral keeps a LongAccumulator exact and the floating types on the double path"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def accumulator = new LongAccumulator({ a, b -> a + b }, 0L)
        accumulator.accumulate(Long.MAX_VALUE)
        def doubles = new DoubleAdder()
        doubles.add(1.5d)

        expect:
        coercing.valueToLiteral(accumulator, CONTEXT, Locale.ROOT).value == BigInteger.valueOf(Long.MAX_VALUE)
        coercing.valueToLiteral(doubles, CONTEXT, Locale.ROOT).value == new BigDecimal('1.5')
        coercing.valueToLiteral(2.5d, CONTEXT, Locale.ROOT).value == new BigDecimal('2.5')
        coercing.valueToLiteral(new BigDecimal('1.25'), CONTEXT, Locale.ROOT).value == new BigDecimal('1.25')
    }

    void "valueToLiteral writes an unknown Number exactly through its decimal text, or refuses it"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        def literal = coercing.valueToLiteral(new Fraction('12345678901234567890.5'), CONTEXT, Locale.ROOT)

        then:
        literal instanceof FloatValue
        literal.value == new BigDecimal('12345678901234567890.5')

        when:
        coercing.valueToLiteral(new Fraction('one half'), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)
    }

    void "a concurrent floating number that is not finite is refused on every path"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def adder = new DoubleAdder()
        adder.add(Double.POSITIVE_INFINITY)

        when:
        coercing.serialize([a: [adder]], CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)

        when:
        coercing.parseValue([a: [adder]], CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)
    }

    void "a decimal out of range is refused when a value becomes a literal"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        when:
        coercing.valueToLiteral(value, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)

        where:
        value << [new BigDecimal('1e-10000'), [a: [new BigDecimal('1e10000')]]]
    }

    void "a decimal out of range behind a variable reference in a literal is refused"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def literal = ObjectValue.newObjectValue()
                .objectField(ObjectField.newObjectField().name('amount').value(new VariableReference('v')).build()).build()

        when:
        coercing.parseLiteral(literal, CoercedVariables.of([v: new BigDecimal('1e-10000')]), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)
    }

    void "a container that contains itself is an error, not a stack overflow"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        List cycle = []
        cycle << cycle

        when:
        coercing.serialize(cycle, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)

        when:
        coercing.parseValue(cycle, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)
    }

    void "a cyclic #kind is a coercion error through all four methods, a variable reference included"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def cycle = factory()

        when:
        coercing.serialize(cycle, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingSerializeException)

        when:
        coercing.parseValue(cycle, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)

        when:
        coercing.valueToLiteral(cycle, CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseValueException)

        when:
        coercing.parseLiteral(new VariableReference('v'), CoercedVariables.of([v: cycle]), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)

        when:
        def nested = ObjectValue.newObjectValue()
                .objectField(ObjectField.newObjectField().name('a').value(new VariableReference('v')).build()).build()
        coercing.parseLiteral(nested, CoercedVariables.of([v: cycle]), CONTEXT, Locale.ROOT)

        then:
        thrown(CoercingParseLiteralException)

        where:
        kind  | factory
        'map' | { Map m = [:]; m.self = m; m }
        'list' | { List l = []; l << l; l }
    }

    void "an ordinary nested value passes through a variable reference"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })
        def value = [a: [1, [b: 'c']], d: null]

        expect:
        coercing.parseLiteral(new VariableReference('v'), CoercedVariables.of([v: value]), CONTEXT, Locale.ROOT) == value
    }

    void "an unknown number outside the range of a double keeps its exact decimal text"() {
        given:
        def coercing = new JsonCoercing('JSON', { null })

        expect:
        coercing.valueToLiteral(new Fraction('1e10000'), CONTEXT, Locale.ROOT).value == new BigDecimal('1e10000')
    }

    static class Fraction extends Number {

        private final String text

        Fraction(String text) { this.text = text }

        @Override
        int intValue() { 0 }

        @Override
        long longValue() { 0L }

        @Override
        float floatValue() { 0f }

        @Override
        double doubleValue() { 0.5d }

        @Override
        String toString() { text }

    }

}
