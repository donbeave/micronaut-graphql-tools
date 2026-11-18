package io.micronaut.graphql.tools

import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.exceptions.SchemaDefinitionNotProvidedException
import org.intellij.lang.annotations.Language

class SchemaDefinitionNotProvidedSpec extends AbstractTest {

    void "schema definition"() {
        given:
        @Language("GraphQL")
        String schema = """
type Hello {
  hello: String
}
"""

        startContext(schema, null)

        when:
        getGraphQLBean()

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaDefinitionNotProvidedException
        e.cause.message == """[schema-definition-not-provided] Schema definition is not set, and the schema has no object type named Query, Mutation or Subscription. Define one of them, or make sure your GraphQL schema contains such definition:
  schema {
    query: Query
    mutation: Mutation
  }"""
    }

}
