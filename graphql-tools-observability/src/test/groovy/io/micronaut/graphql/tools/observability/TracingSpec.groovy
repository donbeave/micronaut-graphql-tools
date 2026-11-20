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
package io.micronaut.graphql.tools.observability

import graphql.schema.GraphQLSchema
import io.micronaut.context.exceptions.ConfigurationException
import io.opentelemetry.api.trace.StatusCode

class TracingSpec extends AbstractObservabilitySpec {

    static final String FIELDS = 'micronaut.graphql.tools.observability.tracing.fields'

    void "a request is one span named after the operation, with its attributes"() {
        given:
        start('ObservabilityTracing')

        when:
        run('query Hello { hello }')

        then:
        finished().size() == 1
        def span = finished()[0]
        span.name == 'query Hello'
        span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey(GraphQLObservability.OPERATION_NAME_ATTRIBUTE)) == 'Hello'
        span.attributes.get(io.opentelemetry.api.common.AttributeKey.stringKey(GraphQLObservability.OPERATION_TYPE_ATTRIBUTE)) == 'query'
    }

    void "a result with an error marks the span"() {
        given:
        start('ObservabilityTracing')

        when:
        run('query Broken { broken }')

        then:
        finished()[0].status.statusCode == StatusCode.ERROR
    }

    void "selected fields get spans that are children of the request span, and property reads get none"() {
        given:
        start('ObservabilityTracing', [(FIELDS): ['Query.*', 'Book.*']])

        when:
        def result = run('query Both { hello book { title } }')

        then:
        result.errors.isEmpty()
        def request = finished().find { it.name == 'query Both' }
        def names = finished()*.name
        names.sort() == ['Query.book', 'Query.hello', 'query Both']
        finished().findAll { it.name != 'query Both' }.every {
            it.parentSpanId == request.spanId && it.traceId == request.traceId
        }
        !names.contains('Book.title')
    }

    void "a field that is not selected keeps the fetcher the library bound"() {
        given:
        start('ObservabilityTracing', [(FIELDS): ['Query.hello']])
        GraphQLSchema schema = context.getBean(GraphQLSchema)
        def fetcher = { String type, String field ->
            schema.codeRegistry.getDataFetcher(graphql.schema.FieldCoordinates.coordinates(type, field),
                    schema.getObjectType(type).getFieldDefinition(field))
        }

        expect:
        fetcher('Query', 'hello').getClass().simpleName == 'TracedDataFetcher'
        fetcher('Query', 'book').getClass().simpleName != 'TracedDataFetcher'
        fetcher('Book', 'title').getClass().simpleName != 'TracedDataFetcher'
    }

    void "a property read named explicitly is not wrapped"() {
        given:
        start('ObservabilityTracing', [(FIELDS): ['Book.title']])
        GraphQLSchema schema = context.getBean(GraphQLSchema)

        when:
        def fetcher = schema.codeRegistry.getDataFetcher(graphql.schema.FieldCoordinates.coordinates('Book', 'title'),
                schema.getObjectType('Book').getFieldDefinition('title'))
        run('query Q { book { title } }')

        then:
        fetcher.getClass().simpleName != 'TracedDataFetcher'
        finished()*.name == ['query Q']
    }

    void "with no field selected the library's schema is untouched and only the request has a span"() {
        given:
        start('ObservabilityTracing')
        GraphQLSchema schema = context.getBean(GraphQLSchema)

        when:
        run('query Q { hello book { title } }')

        then:
        schema.getObjectType('Query').fieldDefinitions.every {
            schema.codeRegistry.getDataFetcher(graphql.schema.FieldCoordinates.coordinates('Query', it.name), it)
                    .getClass().simpleName != 'TracedDataFetcher'
        }
        finished().size() == 1
    }

    void "a failing field span records the error"() {
        given:
        start('ObservabilityTracing', [(FIELDS): ['Query.broken']])

        when:
        run('query Broken { broken }')

        then:
        def field = finished().find { it.name == 'Query.broken' }
        field.status.statusCode == StatusCode.ERROR
        field.events*.name.contains('exception')
    }

    void "a selection that names nothing fails the startup"() {
        when:
        start('ObservabilityTracing', [(FIELDS): selection])
        context.getBean(GraphQLSchema)

        then:
        def e = thrown(Exception)
        def cause = e
        while (cause != null && !(cause instanceof ConfigurationException)) {
            cause = cause.cause
        }
        cause != null
        cause.message.contains(FIELDS)
        cause.message.contains(selection[0])

        where:
        selection << [['Nothing.field'], ['Query.nothing'], ['Query']]
    }

    void "tracing can be turned off"() {
        given:
        start('ObservabilityTracing', ['micronaut.graphql.tools.observability.tracing.enabled': false, (FIELDS): ['Query.hello']])

        when:
        run('query Hello { hello }')

        then:
        finished().isEmpty()
        meters.find(GraphQLObservability.REQUEST_TIMER).timer().count() == 1
    }

}
