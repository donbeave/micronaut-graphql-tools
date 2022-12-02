package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MappingConflictException
import org.intellij.lang.annotations.Language

class RootResolverMappingConflictEnumSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMappingConflictEnumSpec"

    void "attempt map a single enum to a different implementations"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  currentMonth: Month
  nextMonth: Month
}

enum Month {
  JANUARY
  FEBRUARY
  MARCH
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof MappingConflictException
        e.cause.message == """Unable to map GraphQL enum `Month` to ${AnotherMonth.name}, as it is already mapped to ${Month.name}.
  GraphQL object type: Query
  GraphQL field: nextMonth
  Mapped class: ${Query.name}
  Mapped method: nextMonth()"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'nextMonth'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'nextMonth()'
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Month currentMonth() {
            return null
        }

        AnotherMonth nextMonth() {
            return null
        }
    }

    static enum Month {
        JANUARY,
        FEBRUARY,
        MARCH
    }

    static enum AnotherMonth {
        JANUARY,
        FEBRUARY,
        MARCH
    }

}
