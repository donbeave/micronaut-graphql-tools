package io.micronaut.graphql.tools.mapping.resolver.introduction

import io.micronaut.graphql.tools.AbstractTest

class IntroductionResolverSpec extends AbstractTest {

    void "an introduction advice resolver binds all its abstract methods"() {
        given:
        startContext('schema { query: Query }\ntype Query { hello: String goodbye: String }', 'IntroductionResolverSpec')

        when:
        def result = executeQuery('{ hello goodbye }')

        then:
        result.errors.isEmpty()
        result.data == [hello: 'introduced hello', goodbye: 'introduced goodbye']
    }

}
