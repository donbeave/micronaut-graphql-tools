/*
 * Copyright 2021-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.graphql.tools.ws

import io.micronaut.configuration.graphql.ws.apollo.GraphQLApolloWsKeepAlive
import io.micronaut.graphql.tools.AbstractTest
import io.micronaut.websocket.WebSocketBroadcaster

class ApolloKeepAliveSpec extends AbstractTest {

    static final String SCHEMA = """
type Query {
  hello: String
}
"""

    void "the keep-alive sender of the Apollo protocol needs a broadcaster that only a server provides, so it must not exist while the Apollo support is off"() {
        when:
        startContext(SCHEMA, null, ['graphql.graphql-ws.enabled': true] + config)

        then:
        !applicationContext.containsBean(WebSocketBroadcaster)
        !applicationContext.getBeanDefinitions(GraphQLApolloWsKeepAlive).empty == present

        where:
        config                                                                           | present
        [:]                                                                              | false
        ['graphql.graphql-apollo-ws.enabled': false]                                     | false
        ['graphql.graphql-apollo-ws.enabled': true]                                      | true
        ['graphql.graphql-apollo-ws.enabled': true, 'graphql.graphql-apollo-ws.keep-alive-enabled': false] | false
    }

}
