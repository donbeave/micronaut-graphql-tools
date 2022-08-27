package io.micronaut.graphql.tools.mapping

import io.micronaut.context.annotation.Requires
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.annotation.GraphQLType
import org.intellij.lang.annotations.Language
import spock.lang.Unroll

class CircularReferencesSpec extends AbstractTest {

    static final String SPEC_NAME = "CircularReferencesSpec"

    @Unroll
    void "reference type from itself"() {
        given:
            @Language("GraphQL")
            String schema = """
schema {
  query: Query
}

type Query {
  category: Category
}

type Category {
  name: String
  children: [Category!]!
  parent: Category
}
"""

            startContext(schema, SPEC_NAME)

        when:
            getGraphQLBean()

        then:
            noExceptionThrown()
    }

    @Requires(property = 'spec.name', value = SPEC_NAME)
    @GraphQLRootResolver
    static class Query {
        Category category() {
            return null
        }
    }

    @GraphQLType
    static class Category {
        String name
        List<Category> children
        Category parent
    }

}
