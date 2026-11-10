package io.micronaut.graphql.tools.mapping.input

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest
import io.micronaut.graphql.tools.GraphQLRuntimeWiringGenerator
import io.micronaut.graphql.tools.GraphQLToolsConfiguration
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import io.micronaut.graphql.tools.exceptions.UnmappedInputPropertiesException
import io.micronaut.jackson.modules.BeanIntrospectionModule
import jakarta.inject.Named
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory

/**
 * Strict mode governs the input checks that are not about a binding that cannot work:
 * <ul>
 *     <li>a settable input class property that the GraphQL input type does not define fails in strict mode, unless
 *     {@code input.unmapped-properties} says otherwise; it is a {@code !unbound} line of the manifest in every mode;</li>
 *     <li>a primitive for a nullable built-in scalar argument or input field fails in strict mode, because a
 *     {@code null} cannot reach it;</li>
 *     <li>an input class that the input mapper converts with a deserializer of its own is not checked for settable
 *     properties, and the startup logs it.</li>
 * </ul>
 */
class InputStrictModeSpec extends AbstractTest {

    static final String SPEC_PRIMITIVE_ARGUMENT = "InputStrictModeSpecPrimitiveArgument"
    static final String SPEC_PRIMITIVE_PROPERTY = "InputStrictModeSpecPrimitiveProperty"
    static final String SPEC_NON_NULL_PRIMITIVE = "InputStrictModeSpecNonNullPrimitive"
    static final String SPEC_DESERIALIZER = "InputStrictModeSpecDeserializer"

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
    static final String SCHEMA_DESERIALIZER = """
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

    ListAppender<ILoggingEvent> appender

    void setup() {
        appender = new ListAppender<>()
        appender.start()
        generatorLogger().addAppender(appender)
    }

    void cleanup() {
        generatorLogger().detachAppender(appender)
    }

    void "strict false: unmapped input class properties are accepted and listed in the manifest"() {
        given:
        startContext(InputPropertiesSpec.SCHEMA, InputPropertiesSpec.SPEC_EXTRA, [(STRICT): false])

        when:
        def result = executeQuery('{ basket(basket: {owner: "jane", items: [{sku: "a", quantity: 2}]}) }')

        then:
        result.errors.isEmpty()
        result.data.basket == 'jane: a x 2 (legacyCode=none, giftWrap=false)'
        applicationContext.getBean(BindingManifest).diagnostics == [
                "!unbound ${InputPropertiesSpec.ExtraBasket.name}#giftWrap".toString(),
                "!unbound ${InputPropertiesSpec.ExtraItem.name}#discount".toString(),
                "!unbound ${InputPropertiesSpec.ExtraItem.name}#legacyCode".toString()
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
                "!unbound ${InputPropertiesSpec.ExtraItem.name}#discount".toString())
    }

    void "strict mode: a primitive parameter for a nullable built-in scalar argument fails"() {
        given:
        startContext(SCHEMA_PRIMITIVE_ARGUMENT, SPEC_PRIMITIVE_ARGUMENT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to the primitive int, but the GraphQL type Int is nullable, so null cannot be passed. Use java.lang.Integer or make the GraphQL type non-null. Strict mode rejects this for built-in scalars too; with micronaut.graphql.tools.strict=false a null reaches the primitive as its default value or fails the request.
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
        e.cause.message.startsWith("The argument is mapped to the primitive int, but the GraphQL type Int is nullable")
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

    void "an input class with a deserializer of its own is not checked for settable properties, and the startup logs it"() {
        given:
        startContext(SCHEMA_DESERIALIZER, SPEC_DESERIALIZER)

        when:
        def result = executeQuery('{ distance(point: {x: 3, y: 4}) }')

        then:
        result.errors.isEmpty()
        result.data.distance == 5.0d
        appender.list.any {
            it.level == Level.INFO && it.formattedMessage == "Input classes converted by a deserializer of their own " +
                    "are not checked for settable input fields: PointInput (${Point.name})"
        }
    }

    private static Logger generatorLogger() {
        return (Logger) LoggerFactory.getLogger(GraphQLRuntimeWiringGenerator)
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

    @Requires(property = 'spec.name', value = SPEC_DESERIALIZER)
    @GraphQLRootResolver
    static class DeserializerQuery {
        Double distance(Point point) {
            return Math.sqrt(point.x * point.x + point.y * point.y)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_DESERIALIZER)
    @Factory
    static class PointMappers {
        @Bean
        @Named(GraphQLToolsConfiguration.INPUT_OBJECT_MAPPER)
        ObjectMapper graphQLToolsObjectMapper() {
            return new ObjectMapper()
                    .registerModule(new BeanIntrospectionModule())
                    .registerModule(new SimpleModule('points').addDeserializer(Point, new PointDeserializer()))
        }
    }

    static class PointDeserializer extends JsonDeserializer<Point> {
        @Override
        Point deserialize(JsonParser parser, DeserializationContext context) {
            Map<String, Object> values = parser.readValueAs(Map)
            return new Point(((Number) values.x).doubleValue(), ((Number) values.y).doubleValue())
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
