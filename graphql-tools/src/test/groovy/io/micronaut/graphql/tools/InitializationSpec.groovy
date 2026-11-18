package io.micronaut.graphql.tools

import graphql.GraphQL
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.exceptions.SchemaDiscoveryException

class InitializationSpec extends AbstractTest {

    void "initialization"() {
        given:
        startContext(null, null)

        when:
        applicationContext.getBean(GraphQL.class)

        then:
def e = thrown(BeanInstantiationException)
        e.cause instanceof SchemaDiscoveryException
        e.cause.message.startsWith("No GraphQL schema files found for micronaut.graphql.tools.schema.locations=[classpath:graphql/**/*.graphqls]")
    }

}
