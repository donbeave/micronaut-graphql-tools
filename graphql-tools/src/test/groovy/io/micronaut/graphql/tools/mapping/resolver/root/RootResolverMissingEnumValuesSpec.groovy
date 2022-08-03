package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.MissingEnumValuesException
import org.intellij.lang.annotations.Language

class RootResolverMissingEnumValuesSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverMissingEnumValuesSpec"

    void "validates enum has missed values"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  month: Month
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
            e.cause instanceof MissingEnumValuesException
            e.cause.message == """Some enum values are missing in class ${Month.name}.
  GraphQL object type: Query
  GraphQL field: month
  Mapped class: ${Query.name}
  Mapped method: month()
  Missing values: MARCH"""
            e.cause.mappingContext.graphQlObjectType == 'Query'
            e.cause.mappingContext.graphQlField == 'month'
            e.cause.mappingContext.mappedClass == Query
            e.cause.mappingContext.mappedMethod == 'month()'
            e.cause.missingValues == ['MARCH']
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Month month() {
            return null
        }
    }

    static enum Month {
        JANUARY,
        FEBRUARY
    }

}
