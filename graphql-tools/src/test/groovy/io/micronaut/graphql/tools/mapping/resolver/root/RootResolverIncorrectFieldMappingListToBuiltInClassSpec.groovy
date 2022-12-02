package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.annotation.Requires
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.IncorrectClassMappingException
import org.intellij.lang.annotations.Language

class RootResolverIncorrectFieldMappingListToBuiltInClassSpec extends AbstractTest {

    static final String SPEC_NAME = "RootResolverIncorrectFieldMappingListToBuiltInClassSpec"

    void "the field which returns List points to a method which returns a String"() {
        given:
        @Language("GraphQL")
        String schema = """
schema {
  query: Query
}

type Query {
  testString: [String]
}
"""

        startContext(schema, SPEC_NAME)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof IncorrectClassMappingException
        e.cause.message == """The field is mapped to a built-in class, when required an instance of Iterable interface.
  GraphQL object type: Query
  GraphQL field: testString
  Mapped class: ${Query.name}
  Mapped method: testString()
  Provided class: ${String.name}
  Supported classes: java.lang.Iterable, java.util.Collection, java.util.List, java.util.Set"""
        e.cause.mappingContext.graphQlObjectType == 'Query'
        e.cause.mappingContext.graphQlField == 'testString'
        e.cause.mappingContext.mappedClass == Query
        e.cause.mappingContext.mappedMethod == "testString()"
        e.cause.providedClass == String
        e.cause.supportedClasses == [Iterable, Collection, List, Set] as HashSet
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        String testString() {
            return null
        }
    }

}
