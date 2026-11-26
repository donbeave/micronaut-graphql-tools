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
package example.quickstart

import graphql.ExecutionResult
import graphql.GraphQL
import graphql.schema.GraphQLSchema
import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

class QuickStartTest extends Specification {

    // tag::query[]
    def 'the first query returns the books'() {
        given:
        ApplicationContext context = ApplicationContext.run()
        GraphQL graphQL = context.getBean(GraphQL)

        when:
        ExecutionResult result = graphQL.execute('{ allBooks { id title } }')

        then:
        result.errors.empty
        result.<Map<String, List<Map>>> getData().allBooks.first().title == 'The Fractured Sage'

        cleanup:
        context?.close()
    }
    // end::query[]

    // tag::schema-build[]
    def 'the schema builds'() {
        given:
        ApplicationContext context = ApplicationContext.run()

        when:
        GraphQLSchema schema = context.getBean(GraphQLSchema) // getting it builds the schema

        then:
        schema.queryType.getFieldDefinition('allBooks') != null

        cleanup:
        context?.close()
    }
    // end::schema-build[]

    def 'the same query over HTTP returns the response of the guide'() {
        given:
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, ['micronaut.server.port': -1])
        HttpRequest request = HttpRequest.newBuilder(URI.create(server.URL.toString() + '/graphql'))
                .header('Content-Type', 'application/json')
                .POST(HttpRequest.BodyPublishers.ofString('{"query":"{ allBooks { id title } }"}'))
                .build()

        when:
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString())

        then:
        response.statusCode() == 200
        response.body() == '{"data":{"allBooks":[{"id":"ab","title":"The Fractured Sage"},{"id":"ax","title":"Considered for Duty"}]}}'

        cleanup:
        server?.close()
    }

}
