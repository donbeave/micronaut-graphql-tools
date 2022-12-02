package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class RootResolverIncorrectFieldMappingBuiltInScalarToNotSupportedClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectFieldMappingBuiltInScalarToNotSupportedClassSpec"

    @Language("GraphQL")
    static final String SCHEMA1 = """
schema {
  query: Query
}

type Query {
  hello: String
}
"""

    @Language("GraphQL")
    static final String SCHEMA2 = """
schema {
  query: Query
}

type Query {
  hello: String!
}
"""

    @Unroll
    void "the String field points to a method which returns an Integer"() {
        given:
        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to the incorrect class.
  GraphQL object type: Query
  GraphQL field: hello
  Mapped class: ${Query.name}
  Mapped method: hello()
  Provided class: ${Integer.name}
  Supported classes: ${String.name}"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'hello'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == 'hello()'
        e.cause.providedClass == Integer
        e.cause.supportedClasses == [String] as HashSet

        where:
        schema << [SCHEMA1, SCHEMA2]
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Integer hello() {
            return 0
        }
    }

}
