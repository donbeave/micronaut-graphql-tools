package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.graphql.tools.AbstractTest

class InheritedConcreteResolverSpec extends AbstractTest {

    void "a bean that overrides a concrete resolver method of its superclass binds the field once"() {
        given:
        startContext('schema { query: Query }\ntype Query { hello: String }', 'InheritedConcreteResolverSpec')

        when:
        def result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'from the bean']
    }

}
