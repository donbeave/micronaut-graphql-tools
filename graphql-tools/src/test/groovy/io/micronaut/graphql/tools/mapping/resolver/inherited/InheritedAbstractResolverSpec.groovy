package io.micronaut.graphql.tools.mapping.resolver.inherited

import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.BindingManifest

class InheritedAbstractResolverSpec extends AbstractTest {

    void "the implementation of an abstract resolver method binds the field, once"() {
        given:
        startContext('schema { query: Query }\ntype Query { hello: String }', 'InheritedAbstractResolverSpec')

        when:
        def result = executeQuery('{ hello }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'hello']
        applicationContext.getBean(BindingManifest).lines.contains('Query.hello: String -> root ; '
                + 'io.micronaut.graphql.tools.mapping.resolver.inherited.JavaRootResolverImplementation#hello() ; '
                + 'java.lang.String ; java.lang.String')
    }

}
