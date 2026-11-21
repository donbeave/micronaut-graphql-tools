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
package example.websocket

import io.micronaut.configuration.graphql.ws.apollo.GraphQLApolloWsKeepAlive
import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@MicronautTest(startApplication = false)
class ApolloKeepAliveSpec extends Specification {

    @Inject
    ApplicationContext applicationContext

    def 'the keep-alive sender of the Apollo protocol is not a bean while the Apollo support is off'() {
        expect:
        applicationContext.getBeanDefinitions(GraphQLApolloWsKeepAlive).empty
    }

    def 'the Apollo support keeps its keep-alive sender'() {
        given:
        ApplicationContext context = ApplicationContext.builder(
                ['graphql.graphql-apollo-ws.enabled': true, 'micronaut.graphql.tools.eager-init': false]).start()

        expect:
        !context.getBeanDefinitions(GraphQLApolloWsKeepAlive).empty

        cleanup:
        context.close()
    }

}
