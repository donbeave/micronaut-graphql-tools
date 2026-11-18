package io.micronaut.graphql.tools

import io.micronaut.configuration.graphql.ws.apollo.GraphQLApolloWsKeepAlive

class TestContextSpec extends AbstractTest {

    void "a test context has no websocket keep-alive that could stop it"() {
        when:
        startContext(null)

        then:
        !applicationContext.containsBean(GraphQLApolloWsKeepAlive)
    }

}
