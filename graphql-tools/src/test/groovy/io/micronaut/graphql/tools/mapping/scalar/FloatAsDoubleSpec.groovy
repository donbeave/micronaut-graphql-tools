package io.micronaut.graphql.tools.mapping.scalar

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

/**
 * GraphQL {@code Float} is a double-precision value (graphql-java coerces it to {@link Double}), so {@code double} and
 * {@link Double} must be accepted wherever {@code float}/{@link Float} are.
 */
class FloatAsDoubleSpec extends AbstractTest {

    static final String SPEC_NAME = "FloatAsDoubleSpec"
    static final String SPEC_NAME_MISMATCH = "FloatAsDoubleSpecMismatch"

    void "a Float! field backed by a double getter"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  measurement: Measurement
}

type Measurement {
  value: Float!
  average: Float
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    measurement {
        value
        average
    }
}
""")

        then:
        result.errors.isEmpty()
        result.data.measurement.value == 4.5d
        result.data.measurement.average == 0.25d
    }

    void "Float fields of a root resolver returned as double, Double, CompletionStage<Double> and List<Double>"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  primitiveDouble: Float!
  boxedDouble: Float
  asyncDouble: Float!
  doubles: [Float!]!
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    primitiveDouble
    boxedDouble
    asyncDouble
    doubles
}
""")

        then:
        result.errors.isEmpty()
        result.data.primitiveDouble == Double.MAX_VALUE
        result.data.boxedDouble == -1.5d
        result.data.asyncDouble == 2.5d
        result.data.doubles == [1.0d, 2.0d]
    }

    void "Float arguments and input fields bound to double and Double"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  scale(factor: Float!, offset: Float): Float!
  width(range: RangeInput!): Float!
}

input RangeInput {
  min: Float!
  max: Float
}
"""
        startContext(schema, SPEC_NAME)

        when:
        def result = executeQuery("""
{
    scale(factor: 1.5, offset: 0.25)
    width(range: {min: 1.25, max: 4.0})
}
""")

        then:
        result.errors.isEmpty()
        result.data.scale == 1.75d
        result.data.width == 2.75d
    }

    void "a Float field backed by a class that is neither float nor double still fails"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  ratio: Float
}
"""
        startContext(schema, SPEC_NAME_MISMATCH)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.providedClass == Long
        e.cause.supportedClasses == [float, Float, double, Double] as HashSet
        e.cause.message.contains("GraphQL field: ratio")
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Measurement measurement() {
            return new Measurement()
        }

        double primitiveDouble() {
            return Double.MAX_VALUE
        }

        Double boxedDouble() {
            return Double.valueOf(-1.5d)
        }

        CompletionStage<Double> asyncDouble() {
            return CompletableFuture.completedFuture(Double.valueOf(2.5d))
        }

        List<Double> doubles() {
            return [1.0d, 2.0d]
        }

        double scale(double factor, Double offset) {
            return factor + offset
        }

        double width(RangeInput range) {
            return range.max - range.min
        }
    }

    @GraphQLType
    static class Measurement {
        double value = 4.5d
        Double average = Double.valueOf(0.25d)
    }

    @GraphQLInput
    static class RangeInput {
        double min
        Double max
    }

    @Requires(property = 'spec.name', value = SPEC_NAME_MISMATCH)
    @GraphQLRootResolver
    static class MismatchQuery {
        Long ratio() {
            return 1L
        }
    }

}
