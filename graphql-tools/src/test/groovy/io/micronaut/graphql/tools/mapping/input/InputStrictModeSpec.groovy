package io.micronaut.graphql.tools.mapping.input

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

class InputStrictModeSpec extends AbstractTest {

    static final String SPEC_PRIMITIVE_ARGUMENT = "InputStrictModeSpecPrimitiveArgument"
    static final String SPEC_PRIMITIVE_PROPERTY = "InputStrictModeSpecPrimitiveProperty"
    static final String SPEC_NON_NULL_PRIMITIVE = "InputStrictModeSpecNonNullPrimitive"
    static final String SPEC_TYPE_CONVERTER = "InputStrictModeSpecTypeConverter"
    static final String SPEC_IMMUTABLE = "InputStrictModeSpecImmutable"

    static final String STRICT = GraphQLToolsConfiguration.PREFIX + '.strict'
    static final String UNMAPPED = GraphQLToolsConfiguration.PREFIX + '.input.unmapped-properties'

    @Language("GraphQL")
    static final String SCHEMA_PRIMITIVE_ARGUMENT = """
schema {
  query: Query
}

type Query {
  repeat(text: String!, times: Int): String
}
"""

    @Language("GraphQL")
    static final String SCHEMA_NON_NULL_PRIMITIVE = """
schema {
  query: Query
}

type Query {
  repeat(text: String!, times: Int!): String
}
"""

    @Language("GraphQL")
    static final String SCHEMA_PRIMITIVE_PROPERTY = """
schema {
  query: Query
}

type Query {
  order(line: LineInput!): String
}

input LineInput {
  sku: String!
  quantity: Int
}
"""

    @Language("GraphQL")
    static final String SCHEMA_TYPE_CONVERTER = """
schema {
  query: Query
}

type Query {
  distance(point: PointInput!): Float
}

input PointInput {
  x: Float!
  y: Float!
}
"""

    void "strict false: unmapped input class properties are accepted and listed in the manifest"() {
        given:
        startContext(InputPropertiesSpec.SCHEMA, InputPropertiesSpec.SPEC_EXTRA, [(STRICT): false])

        when:
        def result = executeQuery('{ basket(basket: {owner: "jane", items: [{sku: "a", quantity: 2}]}) }')

        then:
        result.errors.isEmpty()
        result.data.basket == 'jane: a x 2 (legacyCode=none, giftWrap=false)'
        applicationContext.getBean(BindingManifest).diagnostics == [
                "!unmapped-input-property ${InputPropertiesSpec.ExtraBasket.name}#giftWrap".toString(),
                "!unmapped-input-property ${InputPropertiesSpec.ExtraItem.name}#discount".toString(),
                "!unmapped-input-property ${InputPropertiesSpec.ExtraItem.name}#legacyCode".toString()
        ]
    }

    void "strict false with unmapped-properties=fail still fails"() {
        given:
        startContext(InputPropertiesSpec.SCHEMA, InputPropertiesSpec.SPEC_EXTRA, [(STRICT): false, (UNMAPPED): 'fail'])

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof UnmappedInputPropertiesException
        e.cause.unmappedProperties.keySet() == ['BasketInput', 'ItemInput'] as Set
    }

    void "strict mode with unmapped-properties=ignore accepts them and still lists them in the manifest"() {
        given:
        startContext(InputPropertiesSpec.SCHEMA, InputPropertiesSpec.SPEC_EXTRA, [(UNMAPPED): 'ignore'])

        when:
        def result = executeQuery('{ basket(basket: {owner: "jane", items: [{sku: "a", quantity: 2}]}) }')

        then:
        result.errors.isEmpty()
        applicationContext.getBean(BindingManifest).diagnostics.contains(
                "!unmapped-input-property ${InputPropertiesSpec.ExtraItem.name}#discount".toString())
    }

    void "strict mode: a primitive parameter for a nullable built-in scalar argument fails"() {
        given:
        startContext(SCHEMA_PRIMITIVE_ARGUMENT, SPEC_PRIMITIVE_ARGUMENT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """[nullable-primitive] The argument is mapped to the primitive int, but the GraphQL type Int is nullable, so null cannot be passed. Use java.lang.Integer or make the GraphQL type non-null. Strict mode rejects this for built-in scalars too; with micronaut.graphql.tools.strict=false a null reaches the primitive as its default value or fails the request.
  GraphQL coordinate: Query.repeat(times:) (line 7, column 25)
  GraphQL object type: Query
  GraphQL field: repeat
  GraphQL argument: times
  Mapped class: ${PrimitiveArgumentQuery.name}
  Mapped method: repeat(java.lang.String text, int times)
  Provided class: int
  Supported classes: java.lang.Integer"""
    }

    void "strict mode: a primitive property for a nullable built-in scalar input field fails"() {
        given:
        startContext(SCHEMA_PRIMITIVE_PROPERTY, SPEC_PRIMITIVE_PROPERTY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.mappingContext.graphQlInputObjectType == 'LineInput'
        e.cause.message.startsWith("[nullable-primitive] The argument is mapped to the primitive int, but the GraphQL type Int is nullable")
    }

    void "strict false: a primitive for a nullable built-in scalar is accepted, as before"() {
        given:
        startContext(SCHEMA_PRIMITIVE_PROPERTY, SPEC_PRIMITIVE_PROPERTY, [(STRICT): false])

        when:
        def result = executeQuery('{ order(line: {sku: "a", quantity: 3}) }')

        then:
        result.errors.isEmpty()
        result.data.order == 'a x 3'
    }

    void "a primitive for a non-null built-in scalar argument is accepted in strict mode"() {
        given:
        startContext(SCHEMA_NON_NULL_PRIMITIVE, SPEC_NON_NULL_PRIMITIVE)

        when:
        def result = executeQuery('{ repeat(text: "ab", times: 2) }')

        then:
        result.errors.isEmpty()
        result.data.repeat == 'abab'
    }

    void "an input class with a TypeConverter bean converts the input object through it"() {
        given:
        startContext(SCHEMA_TYPE_CONVERTER, SPEC_TYPE_CONVERTER)

        when:
        def result = executeQuery('{ distance(point: {x: 3, y: 4}) }')

        then: 'the bean scales the coordinates by ten'
        result.errors.isEmpty()
        result.data.distance == 50.0d
    }

    void "an immutable input class is built through its constructor"() {
        given:
        startContext(SCHEMA_TYPE_CONVERTER, SPEC_IMMUTABLE)

        when:
        def result = executeQuery('{ distance(point: {x: 3, y: 4}) }')

        then:
        result.errors.isEmpty()
        result.data.distance == 5.0d
    }

    @Requires(property = 'spec.name', value = SPEC_PRIMITIVE_ARGUMENT)
    @GraphQLRootResolver
    static class PrimitiveArgumentQuery {
        String repeat(String text, int times) {
            return text * times
        }
    }

    @Requires(property = 'spec.name', value = SPEC_NON_NULL_PRIMITIVE)
    @GraphQLRootResolver
    static class NonNullPrimitiveQuery {
        String repeat(String text, int times) {
            return text * times
        }
    }

    @Requires(property = 'spec.name', value = SPEC_PRIMITIVE_PROPERTY)
    @GraphQLRootResolver
    static class PrimitivePropertyQuery {
        String order(LineInput line) {
            return "${line.sku} x ${line.quantity}"
        }
    }

    @GraphQLInput
    static class LineInput {
        String sku
        int quantity
    }

    @Requires(property = 'spec.name', pattern = 'InputStrictModeSpec(TypeConverter|Immutable)')
    @GraphQLRootResolver
    static class DistanceQuery {
        Double distance(Point point) {
            return Math.sqrt(point.x * point.x + point.y * point.y)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_TYPE_CONVERTER)
    @Singleton
    static class PointConverter implements TypeConverter<Map, Point> {
        @Override
        Optional<Point> convert(Map values, Class<Point> targetType, ConversionContext context) {
            return Optional.of(new Point(((Number) values.x).doubleValue() * 10, ((Number) values.y).doubleValue() * 10))
        }
    }

    @GraphQLInput
    static class Point {
        final double x
        final double y

        Point(double x, double y) {
            this.x = x
            this.y = y
        }
    }
}
