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
package io.micronaut.graphql.tools.internal.http

import io.micronaut.configuration.graphql.GraphQLResponseBody
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import spock.lang.Specification
import spock.lang.Unroll

/** The choice of the response media type from {@code Accept} and the status that follows from it. */
class GraphQLHttpResponsesSpec extends Specification {

    static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE
    static final MediaType GRAPHQL = GraphQLHttpResponses.GRAPHQL_RESPONSE_JSON_TYPE

    @Unroll
    void "Accept '#accept' selects #expected"() {
        expect:
        GraphQLHttpResponses.negotiate((String) accept) == expected

        where:
        accept                                                                  | expected
        null                                                                    | JSON
        ''                                                                      | JSON
        ' , '                                                                   | JSON
        '*/*'                                                                   | JSON
        'application/*'                                                         | JSON
        'application/json'                                                      | JSON
        'application/graphql-response+json'                                     | GRAPHQL
        'Application/GraphQL-Response+JSON'                                     | GRAPHQL
        ' application/graphql-response+json ; charset=utf-8 '                   | GRAPHQL
        'application/graphql-response+json, application/json;q=0.9'             | GRAPHQL
        'application/json, application/graphql-response+json;q=0.5'             | JSON
        'application/graphql-response+json;q=0.5, application/json'             | JSON
        'application/graphql-response+json;q=0.8, application/json;q=0.8'       | GRAPHQL
        'application/json, application/graphql-response+json'                   | JSON
        'application/graphql-response+json, application/json'                   | GRAPHQL
        'application/graphql-response+json, */*'                                | GRAPHQL
        '*/*, application/graphql-response+json'                                | GRAPHQL
        'application/json, */*;q=0.1'                                           | JSON
        'application/graphql-response+json;q=0.001, application/json;q=0'      | GRAPHQL
        'application/graphql-response+json;q=0, application/json'              | JSON
        'application/graphql-response+json;q=0, */*'                            | JSON
        'application/json;q=0, */*;q=0.5'                                       | GRAPHQL
        'text/html, application/graphql-response+json;level=1;q=0.2'            | GRAPHQL
        'application/graphql-response+json;q=bad'                               | GRAPHQL
        'application/xml'                                                       | null
        'text/*'                                                                | null
        'application/json;q=0, application/graphql-response+json;q=0'           | null
        '*/*;q=0'                                                               | null
    }

    void "a status 200 becomes 400 only under graphql-response+json and only without data"() {
        when:
        def response = GraphQLHttpResponses.prepare(HttpResponse.<String> status(HttpStatus.OK), type, dataPresent)

        then:
        response.status() == status
        response.contentType.get() == type

        where:
        type    | dataPresent | status
        GRAPHQL | false       | HttpStatus.BAD_REQUEST
        GRAPHQL | true        | HttpStatus.OK
        JSON    | false       | HttpStatus.OK
        JSON    | true        | HttpStatus.OK
    }

    void "data that is present and null is data"() {
        expect:
        GraphQLHttpResponses.prepare(HttpResponse.<String> status(HttpStatus.OK), GRAPHQL,
                new GraphQLResponseBody([data: null])).status() == HttpStatus.OK
        GraphQLHttpResponses.prepare(HttpResponse.<String> status(HttpStatus.OK), GRAPHQL,
                new GraphQLResponseBody([errors: [[message: 'x']]])).status() == HttpStatus.BAD_REQUEST
    }

    void "a status that a customizer set is kept"() {
        expect:
        GraphQLHttpResponses.prepare(HttpResponse.<String> status(HttpStatus.UNAUTHORIZED), GRAPHQL, false).status() == HttpStatus.UNAUTHORIZED
    }

}
