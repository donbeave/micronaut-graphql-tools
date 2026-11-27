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
package io.micronaut.graphql.tools.client

import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.http.HttpHeaders
import io.micronaut.http.simple.SimpleHttpHeaders
import io.micronaut.core.convert.ConversionService
import spock.lang.Specification

class GraphQLModelSpec extends Specification {

    void "an error replaces absent parts with empty ones and copies what it holds"() {
        given:
        def path = ['a', 1]
        def error = new GraphQLError(null, null, path, null)
        path << 'late'

        expect:
        error.message() == ''
        error.locations().empty
        error.path() == ['a', 1]
        error.extensions().isEmpty()
        GraphQLError.of('m') == new GraphQLError('m', [], [], [:])
    }

    void "a response replaces absent parts with empty ones and attaches an http response"() {
        given:
        def response = new GraphQLResponse<String>('x', true, null, null, null)

        expect:
        response.errors().empty
        response.extensions().isEmpty()
        response.withHttpResponse(null) == response
        response.dataOrThrow() == 'x'
    }

    void "the media types and attributes are the documented values"() {
        expect:
        GraphQLMediaTypes.GRAPHQL_RESPONSE_JSON_TYPE.toString() == 'application/graphql-response+json'
        GraphQLMediaTypes.ACCEPT == 'application/graphql-response+json, application/json;q=0.9'
        GraphQLClientAttributes.OPERATION_NAME == 'graphql.operation.name'
        GraphQLClientAttributes.OPERATION_TYPE == 'graphql.operation.type'
    }

    void "every exception has a lower-case kebab-case code at the start of its message"() {
        given:
        def headers = new SimpleHttpHeaders(ConversionService.SHARED)
        def cause = new IOException('down')
        def failures = [
                new GraphQLClientException('custom-code', 'm'),
                new GraphQLErrorsException([GraphQLError.of('boom')], null),
                new GraphQLTransportException('m', 503, headers, false, cause),
                new GraphQLTransportException('m', GraphQLTransportException.NO_STATUS, null, true, cause),
        ]

        expect:
        failures.every { it.code ==~ /[a-z]+(-[a-z]+)*/ && it.message.startsWith("[${it.code}] ") }
        failures*.code == ['custom-code', 'graphql-errors', 'transport-error', 'transport-timeout']
        failures.every { it instanceof GraphQLClientException && it instanceof RuntimeException }
    }

    void "the transport exception carries the status, the headers, the timeout flag and the cause"() {
        given:
        def headers = new SimpleHttpHeaders(ConversionService.SHARED)
        headers.add('X-Test', '1')
        def cause = new IOException('down')

        when:
        def failure = new GraphQLTransportException('bad gateway', 502, headers, false, cause)
        def timeout = new GraphQLTransportException('slow', GraphQLTransportException.NO_STATUS, null, true, null)

        then:
        failure.status == 502
        failure.headers.get('X-Test') == '1'
        !failure.timeout
        failure.cause.is(cause)
        timeout.status == -1
        timeout.headers.names().empty
        timeout.timeout
        timeout.cause == null
    }

    void "the errors exception carries the errors and the response"() {
        given:
        def errors = [GraphQLError.of('boom')]
        def response = new GraphQLResponse<String>(null, false, errors, null, null)

        when:
        def failure = new GraphQLErrorsException(errors, response)

        then:
        failure.errors == errors
        failure.response.is(response)
        failure.message == '[graphql-errors] boom'
    }
}
