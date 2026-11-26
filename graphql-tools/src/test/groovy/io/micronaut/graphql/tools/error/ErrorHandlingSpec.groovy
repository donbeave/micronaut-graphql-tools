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
package io.micronaut.graphql.tools.error

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import graphql.ExecutionResult
import graphql.GraphQLError
import graphql.GraphqlErrorException
import graphql.language.SourceLocation
import io.micronaut.configuration.graphql.GraphQLInvocation
import io.micronaut.configuration.graphql.GraphQLInvocationData
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Order
import io.micronaut.graphql.tools.AbstractSpec
import io.micronaut.graphql.tools.annotation.GraphQLRootResolver
import io.micronaut.graphql.tools.customizer.GraphQLErrorsCustomizer
import io.micronaut.graphql.tools.internal.error.LibraryDataFetcherExceptionHandler
import jakarta.inject.Singleton
import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import jakarta.validation.ElementKind
import jakarta.validation.Path
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import reactor.core.publisher.Mono

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicInteger

/** The default error handling (#32): hidden messages, ordered mappers, details, the final error list. */
class ErrorHandlingSpec extends AbstractSpec {

    static final String SPEC = 'ErrorHandlingSpec'
    static final AtomicInteger CUSTOMIZER_CALLS = new AtomicInteger()

    @Language('GraphQL')
    static final String SCHEMA = """
schema { query: Query }

type Query {
  ok: String
  boom: String
  boomNonNull: String!
  notFound(id: Int!): String
  teapot: String
  later: String
  laterRuntime: String
  rejected: String
  emptyPath: String
  withPath: String
  invalid: String
  invalidElements: String
}
"""

    void setup() {
        CUSTOMIZER_CALLS.set(0)
    }

    void "an exception without a mapper sends a generic error and no secret text, and the data of the other fields stays"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ ok boom }')

        then:
        result.data == [ok: 'fine', boom: null]
        result.errors.size() == 1
        result.errors[0].message == 'Internal server error'
        result.errors[0].path == ['boom']
        result.errors[0].locations.size() == 1
        result.errors[0].extensions == [code: 'INTERNAL_ERROR']
        !result.toSpecification().toString().contains('secret')
    }

    void "a failure in a non-null field reaches the nearest nullable parent with the generic error"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ ok boomNonNull }')

        then:
        result.data == null
        result.errors*.message == ['Internal server error']
        result.errors*.path == [['boomNonNull']]
    }

    void "a mapper chooses the message and the extensions, and the path and location are the field's"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ notFound(id: 7) }')

        then:
        result.data == [notFound: null]
        result.errors.size() == 1
        result.errors[0].message == 'no item 7'
        result.errors[0].path == ['notFound']
        result.errors[0].extensions == [code: 'NOT_FOUND', id: 7]
    }

    void "mappers are tried lowest order first, a declining mapper passes on, and the first error wins"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ teapot }')

        then:
        result.errors.size() == 1
        result.errors[0].extensions.code == 'TEAPOT'
    }

    void "a mapper matches the real exception of a failed stage, not the CompletionException around it"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ later laterRuntime }')

        then:
        result.data == [later: null, laterRuntime: null]
        result.errors.collect { it.path[0] + ':' + it.extensions.code } as Set == ['later:NOT_FOUND', 'laterRuntime:INTERNAL_ERROR'] as Set
        result.errors.find { it.path[0] == 'laterRuntime' }.message == 'Internal server error'
    }

    void "a GraphQLError that a resolver throws is sent as it is, with the path and the location added"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ rejected }')

        then:
        result.errors.size() == 1
        result.errors[0].message == 'Not allowed'
        result.errors[0].extensions.reason == 'policy'
        result.errors[0].path == ['rejected']
        result.errors[0].locations.size() == 1
    }

    void "a GraphQLError with an empty path and an empty location list gets the path and the location of the field"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        Map body = executeQuery('{ ok emptyPath }').toSpecification()

        then:
        body.errors.size() == 1
        body.errors[0].message == 'Empty'
        body.errors[0].path == ['emptyPath']
        body.errors[0].locations == [[line: 1, column: 6]]
    }

    void "a GraphQLError that has a path and a location keeps them"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        Map body = executeQuery('{ ok withPath }').toSpecification()

        then:
        body.errors.size() == 1
        body.errors[0].path == ['custom', 0]
        body.errors[0].locations == [[line: 7, column: 8]]
    }

    void "a ConstraintViolationException becomes one error for each violation, with the argument and its path"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ invalid }')

        then:
        result.errors.size() == 2
        def blank = result.errors.find { it.message == 'must not be blank' }
        blank.path == ['invalid']
        blank.extensions == [code: 'CONSTRAINT_VIOLATION', argument: 'book', inputPath: ['title']]
        def positive = result.errors.find { it.message == 'must be positive' }
        positive.extensions.argument == 'count'
        positive.extensions.inputPath == []
    }

    void "a container element keeps the label of a custom extractor, without angle brackets, and drops the names of the built-in ones"() {
        given:
        startContext(SCHEMA, SPEC)

        when:
        ExecutionResult result = executeQuery('{ invalidElements }')

        then:
        result.errors.size() == 3
        result.errors.find { it.message == 'plain' }.extensions.inputPath == ['item', 1]
        result.errors.find { it.message == 'bracketed' }.extensions.inputPath == ['crate item', 2]
        result.errors.find { it.message == 'built in' }.extensions.inputPath == [0]
    }

    void "details on sends the message and the class of an exception without a mapper"() {
        given:
        startContext(SCHEMA, SPEC, ['micronaut.graphql.tools.errors.details': true])

        when:
        ExecutionResult result = executeQuery('{ boom }')

        then:
        result.errors.size() == 1
        result.errors[0].message == 'secret detail'
        result.errors[0].extensions.code == 'INTERNAL_ERROR'
        result.errors[0].extensions.exceptionClass == 'java.lang.IllegalStateException'
    }

    void "an exception without a mapper is logged once at ERROR without a stack trace, and with one when asked"() {
        given:
        ListAppender<ILoggingEvent> appender = capture()
        startContext(SCHEMA, SPEC, config)

        when:
        executeQuery('{ boom }')

        then:
        appender.list.size() == 1
        appender.list[0].level == Level.ERROR
        appender.list[0].formattedMessage.contains('/boom')
        (appender.list[0].throwableProxy != null) == stackTrace

        cleanup:
        logger().detachAppender(appender)

        where:
        config                                               | stackTrace
        [:]                                                  | false
        ['micronaut.graphql.tools.errors.stack-traces': true] | true
    }

    void "a mapped exception is not logged at ERROR"() {
        given:
        ListAppender<ILoggingEvent> appender = capture()
        startContext(SCHEMA, SPEC)

        when:
        executeQuery('{ notFound(id: 1) }')

        then:
        appender.list.findAll { it.level.isGreaterOrEqual(Level.INFO) }.isEmpty()

        cleanup:
        logger().detachAppender(appender)
    }

    void "the customizers of the final error list run in order on a response with errors only"() {
        given:
        startContext(SCHEMA, SPEC + '.customizers')

        when:
        ExecutionResult success = invoke('{ ok }')

        then:
        success.errors.isEmpty()
        CUSTOMIZER_CALLS.get() == 0

        when:
        ExecutionResult failure = invoke('{ boom notFound(id: 3) }')

        then:
        failure.errors.size() == 2
        failure.errors*.extensions*.trace == ['first,second', 'first,second']
        CUSTOMIZER_CALLS.get() == 2

        when: 'a syntax error never reached a resolver and is customized too'
        ExecutionResult syntax = invoke('{ ok ')

        then:
        syntax.errors.size() == 1
        syntax.errors[0].extensions.trace == 'first,second'
    }

    void "a customizer that fails is skipped and the errors stay"() {
        given:
        startContext(SCHEMA, SPEC + '.failing')

        when:
        ExecutionResult result = invoke('{ boom }')

        then:
        result.errors*.message == ['Internal server error']
        result.errors[0].extensions == [code: 'INTERNAL_ERROR']
    }

    void "a customizer that returns an empty list ends the chain and the response has no errors"() {
        given:
        startContext(SCHEMA, SPEC + '.empty')

        when:
        Map body = invoke('{ boom }').toSpecification()

        then:
        !body.containsKey('errors')
        body.data == [boom: null]
        CUSTOMIZER_CALLS.get() == 2
    }

    // the customizers of the final error list belong to the invocation, which the transport calls
    private ExecutionResult invoke(String query) {
        Mono.from(applicationContext.getBean(GraphQLInvocation).invoke(new GraphQLInvocationData(query, null, [:]), null, null)).block()
    }

    private static Logger logger() {
        (Logger) LoggerFactory.getLogger(LibraryDataFetcherExceptionHandler)
    }

    private static ListAppender<ILoggingEvent> capture() {
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger().addAppender(appender)
        logger().setLevel(Level.DEBUG)
        appender
    }

    static class NotFoundException extends RuntimeException {
        final int id

        NotFoundException(int id) {
            super('no item ' + id)
            this.id = id
        }
    }

    static class TeapotException extends NotFoundException {
        TeapotException() {
            super(418)
        }
    }

    @Singleton
    @Requires(property = 'spec.name', pattern = 'ErrorHandlingSpec.*')
    @GraphQLRootResolver
    static class Query {

        String ok() { 'fine' }

        String boom() { throw new IllegalStateException('secret detail') }

        String boomNonNull() { throw new IllegalStateException('secret detail') }

        String notFound(int id) { throw new NotFoundException(id) }

        String teapot() { throw new TeapotException() }

        CompletionStage<String> later() { CompletableFuture.<String> failedFuture(new NotFoundException(9)) }

        CompletionStage<String> laterRuntime() { CompletableFuture.<String> failedFuture(new IllegalStateException('secret later')) }

        String emptyPath() {
            throw GraphqlErrorException.newErrorException().message('Empty').path([]).build()
        }

        String withPath() {
            throw GraphqlErrorException.newErrorException().message('Custom').path(['custom', 0])
                    .sourceLocation(new SourceLocation(7, 8)).build()
        }

        String rejected() {
            throw GraphqlErrorException.newErrorException().message('Not allowed').extensions([reason: 'policy']).build()
        }

        String invalid() {
            throw new ConstraintViolationException(
                    [violation('must not be blank', [node('invalid', ElementKind.METHOD), node('book', ElementKind.PARAMETER), node('title', ElementKind.PROPERTY)]),
                     violation('must be positive', [node('invalid', ElementKind.METHOD), node('count', ElementKind.PARAMETER)])] as LinkedHashSet)
        }

        String invalidElements() {
            throw new ConstraintViolationException(
                    [violation('plain', [node('m', ElementKind.METHOD), node('bag', ElementKind.PARAMETER), element('item', 1)]),
                     violation('bracketed', [node('m', ElementKind.METHOD), node('crate', ElementKind.PARAMETER), element('<crate item>', 2)]),
                     violation('built in', [node('m', ElementKind.METHOD), node('list', ElementKind.PARAMETER), element('<list element>', 0)])] as LinkedHashSet)
        }

        private static Path.Node element(String name, int index) {
            [getName: { name }, getKind: { ElementKind.CONTAINER_ELEMENT }, isInIterable: { true }, getIndex: { index }, getKey: { null }] as Path.Node
        }

        private static ConstraintViolation<?> violation(String message, List<Path.Node> nodes) {
            Path path = [iterator: { nodes.iterator() }] as Path
            [getMessage: { message }, getPropertyPath: { path }] as ConstraintViolation
        }

        private static Path.Node node(String name, ElementKind kind) {
            [getName: { name }, getKind: { kind }, isInIterable: { false }, getIndex: { null }, getKey: { null }] as Path.Node
        }
    }

    @Singleton
    @Order(10)
    @Requires(property = 'spec.name', pattern = 'ErrorHandlingSpec.*')
    static class NotFoundMapper implements GraphQLExceptionMapper<NotFoundException> {
        @Override
        GraphQLError map(NotFoundException exception, GraphQLExceptionContext context) {
            context.newError().message(exception.message).extensions([code: 'NOT_FOUND', id: exception.id] as Map<String, Object>).build()
        }
    }

    @Singleton
    @Order(0)
    @Requires(property = 'spec.name', pattern = 'ErrorHandlingSpec.*')
    static class TeapotMapper implements GraphQLExceptionMapper<TeapotException> {
        @Override
        GraphQLError map(TeapotException exception, GraphQLExceptionContext context) {
            context.newError().message('teapot').extensions([code: 'TEAPOT'] as Map<String, Object>).build()
        }
    }

    @Singleton
    @Order(-5)
    @Requires(property = 'spec.name', pattern = 'ErrorHandlingSpec.*')
    static class DecliningMapper implements GraphQLExceptionMapper<RuntimeException> {
        @Override
        GraphQLError map(RuntimeException exception, GraphQLExceptionContext context) {
            null
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.customizers')
    static class First implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            CUSTOMIZER_CALLS.incrementAndGet()
            errors.collect { tag(it, 'first') }
        }
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.customizers')
    static class Second implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            CUSTOMIZER_CALLS.incrementAndGet()
            errors.collect { tag(it, 'second') }
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.empty')
    static class EmptyFirst implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            CUSTOMIZER_CALLS.incrementAndGet()
            errors
        }
    }

    @Singleton
    @Order(2)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.empty')
    static class EmptySecond implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            CUSTOMIZER_CALLS.incrementAndGet()
            []
        }
    }

    @Singleton
    @Order(3)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.empty')
    static class EmptyThird implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            CUSTOMIZER_CALLS.incrementAndGet()
            throw new IllegalStateException('a customizer after an empty list must not be called')
        }
    }

    @Singleton
    @Order(1)
    @Requires(property = 'spec.name', value = 'ErrorHandlingSpec.failing')
    static class Failing implements GraphQLErrorsCustomizer {
        @Override
        List<GraphQLError> customize(List<GraphQLError> errors) {
            throw new IllegalStateException('customizer bug')
        }
    }

    static GraphQLError tag(GraphQLError error, String name) {
        Map<String, Object> extensions = new LinkedHashMap<>(error.extensions ?: [:])
        extensions.put('trace', extensions.containsKey('trace') ? extensions.get('trace') + ',' + name : name)
        graphql.GraphqlErrorBuilder.newError().message(error.message).path(error.path).locations(error.locations)
                .extensions(extensions).build()
    }
}
