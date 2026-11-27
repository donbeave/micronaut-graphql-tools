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
package io.micronaut.graphql.tools.client.declarative

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.graphql.tools.client.GraphQLResponse
import io.micronaut.graphql.tools.client.call.StubController
import io.micronaut.graphql.tools.client.declarative.Types.Book
import io.micronaut.graphql.tools.client.declarative.Types.Filter
import io.micronaut.graphql.tools.client.declarative.Types.Pair
import io.micronaut.graphql.tools.client.declarative.Types.Sort
import io.micronaut.graphql.tools.client.exceptions.GraphQLClientException
import io.micronaut.graphql.tools.client.exceptions.GraphQLErrorsException
import io.micronaut.graphql.tools.client.exceptions.GraphQLTransportException
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import reactor.core.publisher.Flux
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

import java.util.concurrent.ExecutionException

class DeclarativeClientSpec extends Specification {

    @Shared int port = SocketUtils.findAvailableTcpPort()
    @Shared @AutoCleanup EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
            'micronaut.server.port'            : port,
            'micronaut.http.services.decl.url' : "http://localhost:$port",
            'micronaut.http.services.stub.url' : "http://localhost:$port",
    ])
    @Shared ApplicationContext context = server.applicationContext
    @Shared BookClient books = context.getBean(BookClient)
    @Shared BrokenClient broken = context.getBean(BrokenClient)
    @Shared StubClient stub = context.getBean(StubClient)
    @Shared FlakyClient flaky = context.getBean(FlakyClient)
    @Shared EchoController echo = context.getBean(EchoController)
    @Shared StubController stubServer = context.getBean(StubController)

    private static final Book BOOK = new Book('T7', 3, 7L)

    def "a payload method returns the value of the root field, in every return shape"() {
        expect:
        books.book('7') == BOOK
        books.bookFuture('7').get() == BOOK
        books.bookStage('7').toCompletableFuture().get() == BOOK
        Flux.from(books.bookPublisher('7')).collectList().block() == [BOOK]
        books.bookMono('7').block() == BOOK
        books.bookFlux('7').collectList().block() == [BOOK]
    }

    def "an envelope method returns the response with the unwrapped data, in every return shape"() {
        expect:
        books.bookResponse('7').data() == BOOK
        books.bookResponse('7').httpResponse().code() == 200
        books.bookResponseFuture('7').get().data() == BOOK
        books.bookResponseMono('7').block().data() == BOOK
        Flux.from(books.bookResponsePublisher('7')).blockFirst().data() == BOOK
    }

    def "the payload can be dynamic"() {
        expect:
        books.bookDynamic('7') == [title: 'T7', pages: 3, id: 7]
        books.bookDynamicResponse('7').data() == [title: 'T7', pages: 3, id: 7]
        books.bookRawResponse('7').data() == [title: 'T7', pages: 3, id: 7]
    }

    def "the request carries the document, the operation name and the variables"() {
        when:
        books.book('7')

        then:
        echo.last().method() == 'POST'
        echo.last().body().keySet() as List == ['query', 'operationName', 'variables']
        echo.last().body().query == BookClient.BOOK
        echo.last().body().operationName == 'Book'
        echo.last().body().variables == [id: '7']
    }

    def "a method with no variables sends no variables key"() {
        when:
        books.pair()

        then:
        echo.last().body().keySet() as List == ['query', 'operationName']
    }

    def "an operation of several root fields returns the whole data object"() {
        expect:
        books.pair() == new Pair(1, 2)
        books.pairResponse().data() == new Pair(1, 2)
    }

    def "variables are encoded with the type of their parameter, in declaration order"() {
        when:
        def result = books.echo(7L, new Filter('n', ['a', 'b']), 3, [Sort.TITLE, Sort.PAGES], null)

        then:
        echo.last().body().variables.keySet() as List == ['id', 'filter', 'count', 'sorts', 'missing']
        echo.last().body().variables == [id: 7, filter: [name: 'n', tags: ['a', 'b']], count: 3, sorts: ['TITLE', 'PAGES'], missing: null]
        result.variables == echo.last().body().variables
    }

    def "variables bind by name whatever the order of the parameters"() {
        when:
        books.echoShuffled(3, null, new Filter('n', ['a']), [Sort.PAGES], 7L)

        then:
        echo.last().body().variables.keySet() as List == ['id', 'filter', 'count', 'sorts', 'missing']
        echo.last().body().variables == [id: 7, filter: [name: 'n', tags: ['a']], count: 3, sorts: ['PAGES'], missing: null]
    }

    def "a query can be sent with GET and the variables go to the URL"() {
        when:
        def result = books.echoGet(7L, 3)

        then:
        echo.last().method() == 'GET'
        echo.last().uri().contains('operationName=Echo')
        echo.last().body().variables == [id: 7, count: 3]
        result.variables == [id: 7, count: 3]
    }

    def "GET variables are encoded with the declared types, a null one included"() {
        when:
        def result = books.echoGetObjects(new Filter('n', ['a', 'b']), [Sort.PAGES], null)

        then:
        echo.last().method() == 'GET'
        echo.last().body().variables == [filter: [name: 'n', tags: ['a', 'b']], sorts: ['PAGES'], missing: null]
        result.variables == echo.last().body().variables
    }

    def "headers and cookies bind from parameters, and static headers come from the annotations"() {
        when:
        def result = books.echoHeaders(3, 'tok', 'req-1', 'sess', null, 5)

        then:
        result.headers['x-token'] == ['tok']
        result.headers['request-id'] == ['req-1']
        result.headers['x-number'] == ['5']
        result.headers['x-static'] == ['type']
        result.headers['x-method'] == ['method']
        result.headers['x-same'] == ['X-Same']
        !result.headers.containsKey('x-absent')
        result.cookies == [session: 'sess']
        result.variables == [count: 3]
    }

    def "the filter of the service id runs and sees the operation attributes"() {
        when:
        int before = DeclFilter.CALLS.get()
        def result = books.echoHeaders(1, 't', 'r', 's', null, 1)

        then:
        DeclFilter.CALLS.get() == before + 1
        result.headers['x-operation-name'] == ['Echo']
        result.headers['x-operation-type'] == ['QUERY']
    }

    def "a mutation is sent with POST and a void method returns nothing"() {
        expect:
        books.touch() == null
        echo.last().body().operationName == 'Touch'
        books.touchFuture().get() == null
        books.touchMono().block() == null
        books.touched()
    }

    def "a null payload is null, a future of null, and an empty publisher"() {
        expect:
        books.missing() == null
        books.missingFuture().get() == null
        books.missingMono().block() == null
        books.missingResponse().hasData()
        books.missingResponse().data() == null
    }

    def "a reactive method sends nothing until it is subscribed to, and sends again for each subscription"() {
        given:
        def before = echo.last()
        def mono = books.bookMono('1')

        expect:
        echo.last().is(before)

        when:
        mono.block()
        def first = echo.last()
        mono.block()

        then:
        !first.is(before)
        !echo.last().is(first)
    }

    def "errors fail a payload method, whatever its return shape, and keep the partial data in the exception"() {
        when:
        books.fail()

        then:
        def e = thrown(GraphQLErrorsException)
        e.errors*.message() == ['boom']
        e.response.data() == new Book('partial', 1, 1L)
    }

    def "errors fail a payload future with the exception as the cause"() {
        when:
        books.failFuture().get()

        then:
        def e = thrown(ExecutionException)
        e.cause instanceof GraphQLErrorsException
    }

    @Unroll
    def "errors fail #name"() {
        when:
        call()

        then:
        thrown(GraphQLErrorsException)

        where:
        name                 | call
        'a payload Mono'     | { books.failMono().block() }
        'a void method'      | { books.failVoid() }
        'a void Mono'        | { books.failVoidMono().block() }
    }

    def "errors never fail an envelope method"() {
        expect:
        books.failResponse().errors()*.message() == ['boom']
        books.failResponse().data() == new Book('partial', 1, 1L)
        books.failResponseFuture().get().errors().size() == 1
        stub.partialResponse().errors().size() == 1
        stub.partialResponse().hasData()
        stub.partialResponse().data() == null
        stub.nullData().hasData()
        stub.nullData().data() == null
        stub.nullData().errors().isEmpty()
    }

    def "a response with errors and no data is an envelope, and fails a payload method"() {
        when:
        def response = stub.requestError()

        then:
        !response.hasData()
        response.errors().size() == 1
        response.httpResponse().code() == 400
    }

    def "a payload method fails with the partial errors of the stub"() {
        when:
        stub.partial()

        then:
        thrown(GraphQLErrorsException)
    }

    def "a call without a problem on the stub returns the root field"() {
        expect:
        stub.data() == 1
        stubServer.last().method() == 'POST'
        stubServer.last().uri() == '/graphql'
    }

    def "a transport failure is thrown to the caller of a blocking method"() {
        when:
        stub.gatewayError()

        then:
        def e = thrown(GraphQLTransportException)
        e.status == 502
    }

    def "a body that does not fit the declared type is a decode error"() {
        when:
        stub.wrongShape()

        then:
        def e = thrown(GraphQLClientException)
        e.code == GraphQLClientException.DECODE_ERROR
    }

    def "a method without @GraphQLOperation goes on to the HTTP client"() {
        expect:
        books.plain() == 'plain'
    }

    def "a blocking method called on an event loop is refused"() {
        given:
        def http = context.createBean(HttpClient, server.URL)

        expect:
        http.toBlocking().retrieve('/echo/on-event-loop') == GraphQLClientException.BLOCKING_ON_EVENT_LOOP

        cleanup:
        http.close()
    }

    def "Micronaut Retry repeats a method that is annotated with Retryable"() {
        given:
        echo.resetFlaky()

        expect:
        flaky.retried().title() == 'ok'
        echo.flakyCalls() == 3

        when:
        echo.resetFlaky()
        flaky.once()

        then:
        def e = thrown(GraphQLTransportException)
        e.status == 503
        echo.flakyCalls() == 1
    }

    def "a method that the processor did not compile fails and tells to apply it"() {
        when:
        broken.notCompiled()

        then:
        def e = thrown(GraphQLClientException)
        e.code == GraphQLClientException.MISSING_OPERATION
        e.message.contains('BrokenClient#notCompiled')
        e.message.contains('micronaut-graphql-tools-processor')
    }

    @Unroll
    def "#name is refused as an unsupported signature"() {
        when:
        call()

        then:
        def e = thrown(GraphQLClientException)
        e.code == GraphQLClientException.UNSUPPORTED_SIGNATURE
        e.message.contains(text)

        where:
        name                                    | call                                | text
        'a parameter that HTTP annotations bind' | { broken.bodyParameter('x') }       | 'bound by an HTTP annotation'
        'a query parameter'                     | { broken.queryParameter('x') }      | 'bound by an HTTP annotation'
        'a parameter of an undeclared variable' | { broken.undeclaredVariable('a', 'b') } | 'does not declare'
        'two parameters of one variable'        | { broken.duplicateVariable('a', 'b') }  | 'both bind the variable $id'
    }

    def "a mutation over GET and a subscription fail"() {
        when:
        broken.mutationOverGet()

        then:
        thrown(IllegalStateException)

        when:
        broken.subscription()

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains('subscription transport')
    }
}
