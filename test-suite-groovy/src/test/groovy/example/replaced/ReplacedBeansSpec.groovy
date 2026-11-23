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
package example.replaced

import graphql.schema.GraphQLSchema
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class ReplacedBeansSpec extends Specification {

    static final Map<String, Object> PORT = ['micronaut.server.port': -1]

    def 'an application schema serves the request'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, PORT, ReplacedBeansFactory.SCHEMA)
        ApplicationContext context = server.applicationContext

        expect:
        context.getBeansOfType(GraphQLSchema).size() == 1
        context.getBean(GraphQLSchema).queryType.getFieldDefinition('owner') != null
        query(context, server).contains('application schema')

        cleanup:
        server?.close()
    }

    // the example resolvers need a DataLoader, so an application invocation comes with an application schema
    def 'an application invocation serves the request'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, PORT, ReplacedBeansFactory.SCHEMA, ReplacedBeansFactory.INVOCATION)
        ApplicationContext context = server.applicationContext

        expect:
        context.getBeanDefinition(GraphQLInvocation).declaringType.get() == ReplacedBeansFactory
        query(context, server).contains('application invocation')

        cleanup:
        server?.close()
    }

    private static String query(ApplicationContext context, EmbeddedServer server) {
        HttpClient client = context.createBean(HttpClient, server.URL)
        try {
            client.toBlocking().retrieve(
                    HttpRequest.POST('/graphql', '{"query":"{ owner }"}').contentType(MediaType.APPLICATION_JSON), String)
        } finally {
            client.close()
        }
    }

}
