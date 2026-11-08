package io.micronaut.graphql.tools.mapping.scalar

import com.tailrocks.graphql.datetime.LocalDateScalar
import graphql.scalars.ExtendedScalars
import graphql.schema.GraphQLScalarType
import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.GraphQLScalarMapping
import io.micronaut.graphql.tools.annotation.GraphQLInput
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import io.micronaut.graphql.tools.exceptions.InvalidScalarMappingException
import io.micronaut.graphql.tools.exceptions.ScalarMappingNotProvidedException
import jakarta.inject.Singleton
import org.intellij.lang.annotations.Language

import java.time.LocalDate
import java.time.temporal.Temporal

/**
 * A custom scalar that a field, an argument or an input field uses needs a {@link GraphQLScalarMapping}, and the Java
 * type must be one of its declared classes; otherwise the startup fails and names the coordinate.
 */
class CustomScalarMappingErrorsSpec extends AbstractTest {

    static final String SPEC_UNMAPPED = "CustomScalarMappingErrorsSpecUnmapped"
    static final String SPEC_MAPPED = "CustomScalarMappingErrorsSpecMapped"
    static final String SPEC_DUPLICATE = "CustomScalarMappingErrorsSpecDuplicate"
    static final String SPEC_BUILT_IN = "CustomScalarMappingErrorsSpecBuiltIn"
    static final String SPEC_OUTPUT_ONLY = "CustomScalarMappingErrorsSpecOutputOnly"
    static final String SPEC_PRIMITIVE = "CustomScalarMappingErrorsSpecPrimitive"
    static final String SPEC_PRIMITIVE_INPUT = "CustomScalarMappingErrorsSpecPrimitiveInput"
    static final String SPEC_PRIMITIVE_NON_NULL = "CustomScalarMappingErrorsSpecPrimitiveNonNull"

    void "a field of an unmapped scalar fails the startup with its coordinate"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  today: LocalDate
}
"""
        startContext(schema, SPEC_UNMAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ScalarMappingNotProvidedException
        e.cause.message == """The GraphQL scalar LocalDate has no mapping. Register a ${GraphQLScalarMapping.name} bean with its graphql.schema.GraphQLScalarType and Java classes.
  GraphQL object type: Query
  GraphQL field: today
  Mapped class: ${UnmappedQuery.name}
  Mapped method: today()"""
        e.cause.scalarName == 'LocalDate'
    }

    void "an argument of an unmapped scalar fails the startup with its coordinate"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  daysUntil(date: LocalDate): Int
}
"""
        startContext(schema, SPEC_UNMAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ScalarMappingNotProvidedException
        e.cause.message == """The GraphQL scalar LocalDate has no mapping. Register a ${GraphQLScalarMapping.name} bean with its graphql.schema.GraphQLScalarType and Java classes.
  GraphQL object type: Query
  GraphQL field: daysUntil
  GraphQL argument: date
  Mapped class: ${UnmappedQuery.name}
  Mapped method: daysUntil(${LocalDate.name} date)"""
    }

    void "an input field of an unmapped scalar fails the startup with its coordinate"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  book(input: BookingInput): Int
}

input BookingInput {
  day: LocalDate
}
"""
        startContext(schema, SPEC_UNMAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof ScalarMappingNotProvidedException
        e.cause.message == """The GraphQL scalar LocalDate has no mapping. Register a ${GraphQLScalarMapping.name} bean with its graphql.schema.GraphQLScalarType and Java classes.
  GraphQL input object type: BookingInput
  GraphQL input value: day
  Mapped class: ${BookingInput.name}
  Mapped property: day"""
    }

    void "a field backed by a class that is not an output class of the scalar fails"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  todayAsString: LocalDate
}
"""
        startContext(schema, SPEC_MAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to the incorrect class.
  GraphQL object type: Query
  GraphQL field: todayAsString
  Mapped class: ${MappedQuery.name}
  Mapped method: todayAsString()
  Provided class: ${String.name}
  Supported classes: ${LocalDate.name}"""
    }

    void "an argument declared as a supertype of the scalar's input class fails"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  anything(date: LocalDate): Int
}
"""
        startContext(schema, SPEC_MAPPED)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to the incorrect class.
  GraphQL object type: Query
  GraphQL field: anything
  GraphQL argument: date
  Mapped class: ${MappedQuery.name}
  Mapped method: anything(${Temporal.name} date)
  Provided class: ${Temporal.name}
  Supported classes: ${LocalDate.name}"""
    }

    void "a field backed by a subclass of an output class is accepted"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar JSON

schema {
  query: Query
}

type Query {
  attributes: JSON
}
"""
        startContext(schema, SPEC_MAPPED)

        when:
        def result = executeQuery("{ attributes }")

        then:
        result.errors.isEmpty()
        result.data.attributes == [size: 'XL']
    }

    void "two mappings of the same scalar fail the startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""
        startContext(schema, SPEC_DUPLICATE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidScalarMappingException
        e.cause.message == "Invalid ${GraphQLScalarMapping.name} beans: the scalar LocalDate is mapped 2 times."
    }

    void "a mapping of a scalar the library provides fails the startup"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""
        startContext(schema, SPEC_BUILT_IN)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidScalarMappingException
        e.cause.message == "Invalid ${GraphQLScalarMapping.name} beans: the scalars Long, String are built in."
    }

    void "a scalar without input classes cannot be used for an argument"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar LocalDate

schema {
  query: Query
}

type Query {
  echo(date: LocalDate): LocalDate
}
"""
        startContext(schema, SPEC_OUTPUT_ONLY)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The mapping of the GraphQL scalar LocalDate declares no input classes, so the scalar cannot be used for an argument or an input field.
  GraphQL object type: Query
  GraphQL field: echo
  GraphQL argument: date
  Mapped class: ${OutputOnlyQuery.name}
  Mapped method: echo(${LocalDate.name} date)
  Provided class: ${LocalDate.name}
  Supported classes: """
        e.cause.supportedClasses == [] as Set
    }

    void "a primitive parameter for a nullable custom scalar argument fails"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar Count

schema {
  query: Query
}

type Query {
  repeat(times: Count): String
}
"""
        startContext(schema, SPEC_PRIMITIVE)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The argument is mapped to the primitive long, but the GraphQL type Count is nullable, so null cannot be passed. Use ${Long.name} or make the GraphQL type non-null.
  GraphQL object type: Query
  GraphQL field: repeat
  GraphQL argument: times
  Mapped class: ${PrimitiveQuery.name}
  Mapped method: repeat(long times)
  Provided class: long
  Supported classes: ${Long.name}"""
    }

    void "a primitive property for a nullable custom scalar input field fails"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar Count

schema {
  query: Query
}

type Query {
  repeat(input: RepeatInput): String
}

input RepeatInput {
  times: Count
}
"""
        startContext(schema, SPEC_PRIMITIVE_INPUT)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message.startsWith("The argument is mapped to the primitive long, but the GraphQL type Count is nullable")
        e.cause.message.contains("GraphQL input object type: RepeatInput\n  GraphQL input value: times")
    }

    void "a primitive parameter for a non-null custom scalar argument is accepted"() {
        given:
        @Language("GraphQL")
        String schema = """
scalar Count

schema {
  query: Query
}

type Query {
  repeat(times: Count!): String
}
"""
        startContext(schema, SPEC_PRIMITIVE_NON_NULL)

        when:
        def result = executeQuery('{ repeat(times: 3) }')

        then:
        result.errors.isEmpty()
        result.data.repeat == 'xxx'
    }

    void "a mapping must declare output or input classes"() {
        when:
        GraphQLScalarMapping.builder(ExtendedScalars.Json).build()

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "The mapping of the scalar JSON declares neither output nor input classes."
    }

    void "primitives match their wrapper classes"() {
        given:
        def mapping = GraphQLScalarMapping.of(ExtendedScalars.GraphQLLong, Long)

        expect:
        mapping.isOutputClass(long)
        mapping.isInputClass(long)
        mapping.isOutputClass(Long)
        !mapping.isInputClass(Number)
        !mapping.isOutputClass(Number)
    }

    @Requires(property = 'spec.name', value = SPEC_UNMAPPED)
    @GraphQLRootResolver
    static class UnmappedQuery {
        LocalDate today() {
            return null
        }

        Integer daysUntil(LocalDate date) {
            return null
        }

        Integer book(BookingInput input) {
            return null
        }
    }

    @GraphQLInput
    static class BookingInput {
        LocalDate day
    }

    @Requires(property = 'spec.name', value = SPEC_MAPPED)
    @Factory
    static class MappedScalars {
        @Bean
        @Singleton
        GraphQLScalarMapping localDate() {
            return GraphQLScalarMapping.of(LocalDateScalar.create(null, false, null), LocalDate)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping json() {
            return GraphQLScalarMapping.of(ExtendedScalars.Json, Map)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_MAPPED)
    @GraphQLRootResolver
    static class MappedQuery {
        String todayAsString() {
            return null
        }

        Integer anything(Temporal date) {
            return null
        }

        LinkedHashMap<String, Object> attributes() {
            return [size: 'XL']
        }
    }

    @Requires(property = 'spec.name', value = SPEC_DUPLICATE)
    @Factory
    static class DuplicateScalars {
        @Bean
        @Singleton
        GraphQLScalarMapping first() {
            return GraphQLScalarMapping.of(LocalDateScalar.create(null, false, null), LocalDate)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping second() {
            return GraphQLScalarMapping.of(LocalDateScalar.create(null, false, null), LocalDate)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_BUILT_IN)
    @Factory
    static class BuiltInScalars {
        @Bean
        @Singleton
        GraphQLScalarMapping string() {
            return GraphQLScalarMapping.of(GraphQLScalarType.newScalar(ExtendedScalars.Json).name("String").build(),
                    String)
        }

        @Bean
        @Singleton
        GraphQLScalarMapping longScalar() {
            return GraphQLScalarMapping.of(ExtendedScalars.GraphQLLong, Long)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_OUTPUT_ONLY)
    @Factory
    static class OutputOnlyScalars {
        @Bean
        @Singleton
        GraphQLScalarMapping localDate() {
            return GraphQLScalarMapping.builder(LocalDateScalar.create(null, false, null))
                    .outputClasses(LocalDate)
                    .build()
        }
    }

    @Requires(property = 'spec.name', value = SPEC_OUTPUT_ONLY)
    @GraphQLRootResolver
    static class OutputOnlyQuery {
        LocalDate echo(LocalDate date) {
            return date
        }
    }

    @Requires(property = 'spec.name', pattern = SPEC_PRIMITIVE + '.*')
    @Factory
    static class CountScalars {
        @Bean
        @Singleton
        GraphQLScalarMapping count() {
            return GraphQLScalarMapping.of(
                    GraphQLScalarType.newScalar(ExtendedScalars.GraphQLLong).name("Count").build(), Long)
        }
    }

    @Requires(property = 'spec.name', value = SPEC_PRIMITIVE)
    @GraphQLRootResolver
    static class PrimitiveQuery {
        String repeat(long times) {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_PRIMITIVE_INPUT)
    @GraphQLRootResolver
    static class PrimitiveInputQuery {
        String repeat(RepeatInput input) {
            return null
        }
    }

    @GraphQLInput
    static class RepeatInput {
        long times
    }

    @Requires(property = 'spec.name', value = SPEC_PRIMITIVE_NON_NULL)
    @GraphQLRootResolver
    static class PrimitiveNonNullQuery {
        String repeat(long times) {
            return 'x' * times
        }
    }

    @Requires(property = 'spec.name', value = SPEC_DUPLICATE)
    @GraphQLRootResolver
    static class DuplicateQuery {
        String hello() {
            return null
        }
    }

    @Requires(property = 'spec.name', value = SPEC_BUILT_IN)
    @GraphQLRootResolver
    static class BuiltInQuery {
        String hello() {
            return null
        }
    }

}
