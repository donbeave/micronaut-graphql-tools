package io.micronaut.graphql.tools

import graphql.GraphQL
import io.micronaut.context.exceptions.DependencyInjectionException

class InitializationSpec extends AbstractTest {

    void "initialization"() {
        given:
            startContext(null, null)

        when:
            applicationContext.getBean(GraphQL.class)

        then:
            def e = thrown(DependencyInjectionException)
            e.message == "Failed to inject value for parameter [typeDefinitionRegistry] of method [graphQL] of class: graphql.GraphQL\n" +
                    "\n" +
                    "Message: No bean of type [graphql.schema.idl.TypeDefinitionRegistry] exists. Make sure the bean is not disabled by bean requirements (enable trace logging for 'io.micronaut.context.condition' to check) and if the bean is enabled then ensure the class is declared a bean and annotation processing is enabled (for Java and Kotlin the 'micronaut-inject-java' dependency should be configured as an annotation processor).\n" +
                    "Path Taken: GraphQL.graphQL(ApplicationContext applicationContext,GraphQLResolversRegistry graphQLResolversRegistry,TypeDefinitionRegistry typeDefinitionRegistry,SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer) --> GraphQL.graphQL(ApplicationContext applicationContext,GraphQLResolversRegistry graphQLResolversRegistry,[TypeDefinitionRegistry typeDefinitionRegistry],SchemaMappingDictionaryCustomizer schemaMappingDictionaryCustomizer)"
    }

}
