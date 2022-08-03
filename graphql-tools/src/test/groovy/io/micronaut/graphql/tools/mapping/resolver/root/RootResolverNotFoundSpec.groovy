package io.micronaut.graphql.tools.mapping.resolver.root

import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.exceptions.RootResolverNotFoundException
import org.intellij.lang.annotations.Language

class RootResolverNotFoundSpec extends AbstractTest {

    void "no any root resolvers found"() {
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

            startContext(schema, null)

        when:
            getGraphQLBean()

        then:
            def e = thrown(BeanInstantiationException)
            e.cause instanceof RootResolverNotFoundException
            e.cause.message == "No any root resolvers found. Create one or ensure the class is annotated with ${GraphQLRootResolver.name}."
    }

}
