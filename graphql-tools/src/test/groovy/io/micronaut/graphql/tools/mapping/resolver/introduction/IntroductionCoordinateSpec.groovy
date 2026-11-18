package io.micronaut.graphql.tools.mapping.resolver.introduction

import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.graphql.tools.exceptions.InvalidSchemaCoordinateException

class IntroductionCoordinateSpec extends AbstractTest {

    void "the schema coordinates of an introduction advice resolver are validated"() {
        when:
        startContext('schema { query: Query }\ntype Query { hello: String }', 'IntroductionCoordinateSpec')
        executeQuery('{ hello }')

        then:
        def e = thrown(BeanInstantiationException)
        e.cause instanceof InvalidSchemaCoordinateException
    }

}
