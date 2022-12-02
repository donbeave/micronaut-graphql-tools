package io.micronaut.graphql.tools

import graphql.GraphQL
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NoSuchBeanException

class InitializationSpec extends AbstractTest {

    void "initialization"() {
        given:
        startContext(null, null)

        when:
        applicationContext.getBean(GraphQL.class)

        then:
        def e = thrown(DependencyInjectionException)
        e.message.startsWith("Failed to inject value for parameter [typeDefinitionRegistry] of method [graphQL] of class: io.micronaut.graphql.tools.GraphQLFactory")
        e.cause instanceof NoSuchBeanException
        e.cause.message.startsWith("No bean of type [graphql.schema.idl.TypeDefinitionRegistry] exists")
    }

}
