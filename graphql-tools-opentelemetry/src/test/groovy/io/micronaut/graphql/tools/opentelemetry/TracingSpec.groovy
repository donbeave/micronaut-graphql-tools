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
package io.micronaut.graphql.tools.opentelemetry

import graphql.schema.FieldCoordinates
import graphql.schema.GraphQLSchema
import io.micronaut.context.exceptions.ConfigurationException
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.sdk.trace.data.SpanData

class TracingSpec extends AbstractOpenTelemetrySpec {

    static final String FIELDS = 'micronaut.graphql.tools.tracing.fields'
    static final String ADD_NAME = 'micronaut.graphql.tools.tracing.add-operation-name-to-span-name'

    static String attribute(SpanData span, String key) {
        span.attributes.get(AttributeKey.stringKey(key))
    }

    void "a request is one span named after the operation type, with the semantic-convention attributes"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query Hello { hello }')

        then:
        hasBean('RequestSpanObserver')
        finished().size() == 1
        def span = finished()[0]
        span.name == 'query'
        span.kind == SpanKind.INTERNAL
        span.instrumentationScopeInfo.name == 'io.micronaut.graphql.tools'
        attribute(span, 'graphql.operation.name') == 'Hello'
        attribute(span, 'graphql.operation.type') == 'query'
    }

    void "an anonymous operation has the type attribute and no name attribute"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('{ hello }')

        then:
        def span = finished()[0]
        span.name == 'query'
        attribute(span, 'graphql.operation.type') == 'query'
        attribute(span, 'graphql.operation.name') == null
    }

    void "the operation name is part of the span name only when the key asks for it"() {
        given:
        start('OpenTelemetryTracing', [(ADD_NAME): true])

        when:
        run('query Hello { hello }')
        run('{ hello }')

        then:
        finished()*.name.sort() == ['query', 'query Hello']
    }

    void "a request that never reaches an operation is a span named GraphQL Operation with an error status"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query Invalid { nothing }')

        then:
        def span = finished()[0]
        span.name == 'GraphQL Operation'
        span.status.statusCode == StatusCode.ERROR
        span.events*.name == ['exception']
        attribute(span, 'graphql.operation.type') == null
    }

    void "a result with an error marks the span and records one exception event for each error"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query Broken { broken }')

        then:
        def span = finished()[0]
        span.status.statusCode == StatusCode.ERROR
        span.events*.name == ['exception']
        span.events[0].attributes.get(AttributeKey.stringKey('exception.message')) != null
        span.events[0].attributes.get(AttributeKey.stringKey('exception.type')) != null
    }

    void "selected fields get spans that are children of the request span, and property reads get none"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.*', 'Book.*']])

        when:
        def result = run('query Both { hello book { title } }')

        then:
        result.errors.isEmpty()
        def request = finished().find { it.name == 'query' }
        def names = finished()*.name
        names.sort() == ['Query.book', 'Query.hello', 'query']
        finished().findAll { it.name != 'query' }.every {
            it.parentSpanId == request.spanId && it.traceId == request.traceId
        }
        !names.contains('Book.title')
    }

    void "a field span has the OpenTelemetry field attributes"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.hello']])

        when:
        run('{ hello }')

        then:
        def field = finished().find { it.name == 'Query.hello' }
        attribute(field, 'graphql.field.name') == 'hello'
        attribute(field, 'graphql.field.path') == '/hello'
        attribute(field, 'graphql.field.parent') == null
        field.kind == SpanKind.INTERNAL
        field.instrumentationScopeInfo.name == 'io.micronaut.graphql.tools'
    }

    void "a field that is not selected keeps the fetcher the library bound, gets no span and no instrumenter call"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.hello']])
        GraphQLSchema schema = context.getBean(GraphQLSchema)
        def fetcher = { String type, String field ->
            schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates(type, field), schema.getObjectType(type).getFieldDefinition(field))
        }
        def before = startedSpans.get()

        when:
        run('query Q { book { title } }')

        then:
        fetcher('Query', 'hello').getClass().simpleName == 'FieldSpanFetcher'
        fetcher('Query', 'book').getClass().simpleName != 'FieldSpanFetcher'
        fetcher('Book', 'title').getClass().simpleName != 'FieldSpanFetcher'
        startedSpans.get() - before == 1
        finished()*.name == ['query']
    }

    void "a selected field makes one instrumenter call, and the request one"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.hello']])
        def before = startedSpans.get()

        when:
        run('query Q { hello book { title } }')

        then:
        startedSpans.get() - before == 2
    }

    void "a property read named explicitly is not wrapped"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Book.title']])
        GraphQLSchema schema = context.getBean(GraphQLSchema)

        when:
        def fetcher = schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates('Book', 'title'),
                schema.getObjectType('Book').getFieldDefinition('title'))
        run('query Q { book { title } }')

        then:
        fetcher.getClass().simpleName != 'FieldSpanFetcher'
        finished()*.name == ['query']
    }

    void "with no field selected the library's schema is untouched and only the request has a span"() {
        given:
        start('OpenTelemetryTracing')
        GraphQLSchema schema = context.getBean(GraphQLSchema)

        when:
        run('query Q { hello book { title } }')

        then:
        schema.getObjectType('Query').fieldDefinitions.every {
            schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates('Query', it.name), it)
                    .getClass().simpleName != 'FieldSpanFetcher'
        }
        finished().size() == 1
        startedSpans.get() == 1
    }

    void "a failing field span records the error"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.broken']])

        when:
        run('query Broken { broken }')

        then:
        def field = finished().find { it.name == 'Query.broken' }
        field.status.statusCode == StatusCode.ERROR
        field.events*.name.contains('exception')
    }

    void "an async selected field ends its span when the value arrives"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.later']])

        when:
        def result = run('query Later { later { title } }')

        then:
        result.data == [later: [title: 'L']]
        finished()*.name.sort() == ['Query.later', 'query']
    }

    void "a field of the subscription type is never traced, even when the selection names it"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Subscription.*', 'Query.hello']])
        GraphQLSchema schema = context.getBean(GraphQLSchema)

        when:
        def result = run('subscription S { ticks }')

        then:
        result.errors.isEmpty()
        result.data instanceof org.reactivestreams.Publisher
        schema.codeRegistry.getDataFetcher(FieldCoordinates.coordinates('Subscription', 'ticks'),
                schema.getObjectType('Subscription').getFieldDefinition('ticks')).getClass().simpleName != 'FieldSpanFetcher'
        finished()*.name == ['subscription']
        startedSpans.get() == 1
    }

    void "the request span is current in the propagated context: a @NewSpan resolver is its child"() {
        given:
        start('OpenTelemetryTracing')

        when:
        def result = run('query T { traced }')

        then:
        result.data == [traced: 'traced']
        def request = finished().find { it.name == 'query' }
        def child = finished().find { it.name.endsWith('#resolver.work') }
        child.parentSpanId == request.spanId
        child.traceId == request.traceId
    }

    void "@SpanTag puts the argument on the span of a resolver"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query T { tagged(value: "x") }')

        then:
        attribute(finished().find { it.name.endsWith('#resolver.tagged') }, 'value') == 'x'
    }

    void "a @NewSpan resolver under a selected field is a child of the field span"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.traced']])

        when:
        run('query T { traced }')

        then:
        def request = finished().find { it.name == 'query' }
        def field = finished().find { it.name == 'Query.traced' }
        def child = finished().find { it.name.endsWith('#resolver.work') }
        field.parentSpanId == request.spanId
        child.parentSpanId == field.spanId
    }

    void "a @NewSpan resolver on an @ExecuteOn field under a selected field is a child of the field span"() {
        given:
        start('OpenTelemetryTracing', [(FIELDS): ['Query.tracedOnExecutor']])

        when:
        def result = run('query T { tracedOnExecutor }')

        then:
        result.data == [tracedOnExecutor: 'hopped']
        def request = finished().find { it.name == 'query' }
        def field = finished().find { it.name == 'Query.tracedOnExecutor' }
        def child = finished().find { it.name.endsWith('#resolver.hopped') }
        field.parentSpanId == request.spanId
        child.parentSpanId == field.spanId
    }

    void "a @NewSpan resolver on an @ExecuteOn field that is not selected is a child of the request span"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query T { tracedOnExecutor }')

        then:
        def request = finished().find { it.name == 'query' }
        finished().find { it.name.endsWith('#resolver.hopped') }.parentSpanId == request.spanId
    }

    void "a batch loader that runs after the resolver returned still sees the request span"() {
        given:
        start('OpenTelemetryTracing')

        when:
        def result = run('query L { loaded }')

        then:
        result.data == [loaded: 'title-a']
        def request = finished().find { it.name == 'query' }
        finished().find { it.name.endsWith('#loader.batch') }.parentSpanId == request.spanId
    }

    void "the thread has no span of the request left when the operation returned"() {
        given:
        start('OpenTelemetryTracing')

        when:
        run('query T { traced }')

        then:
        !io.opentelemetry.api.trace.Span.current().spanContext.valid
        !io.micronaut.core.propagation.PropagatedContext.exists()
    }

    void "a selection that names nothing fails the startup"() {
        when:
        start('OpenTelemetryTracing', [(FIELDS): selection])
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
        start('OpenTelemetryTracing', ['micronaut.graphql.tools.tracing.enabled': false, (FIELDS): ['Query.hello']])

        when:
        run('query Hello { hello }')

        then:
        !hasBean('RequestSpanObserver')
        !hasBean('FieldSpanDecorator')
        finished().isEmpty()
    }

    void "micronaut.otel.enabled=false removes the tracing beans"() {
        given:
        start('OpenTelemetryTracing', ['micronaut.otel.enabled': false, (FIELDS): ['Query.hello']])

        when:
        run('query Hello { hello }')

        then:
        !hasBean('RequestSpanObserver')
        !hasBean('FieldSpanDecorator')
        finished().isEmpty()
    }

}
